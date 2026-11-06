package com.craxiom.networksurvey.scout

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ConfigureResult
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.link.ReceivedRawFrames
import com.craxiom.networksurvey.scout.link.ReceivedRecordBatch
import com.craxiom.networksurvey.scout.link.ReconnectBackoff
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutLinkFailure
import com.craxiom.networksurvey.scout.link.ScoutLinkInfo
import com.craxiom.networksurvey.scout.link.ScoutProtocol
import com.craxiom.networksurvey.scout.link.ScoutSession
import com.craxiom.networksurvey.scout.link.ScoutSessionListener
import com.craxiom.networksurvey.scout.link.ScoutTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Keeps one Scout connected while it is wanted: connects, reconnects with [ReconnectBackoff]
 * (1 s doubling to 30 s, and only while [connect] is in effect), applies the wanted capture
 * (Configure and Start, or Stop) and stops the board before letting go, because a board keeps
 * surveying for 5 minutes after it loses its phone.
 *
 * Everything runs in [scope], on the Scout thread. Problems only the user can fix (a lost or
 * refused pairing, a device that is not a Scout) end the attempts and are reported once.
 */
internal class ScoutLinkController(
    private val scope: CoroutineScope,
    private val transportFactory: () -> ScoutTransport,
    private val appVersion: String,
    private val clock: ScoutClock,
    private val events: Events,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
) {
    /** What the controller reports, on the Scout thread. */
    interface Events {
        fun onPhase(phase: ScoutLinkPhase)
        fun onReady(info: ScoutLinkInfo)
        fun onLinkLost(failure: ScoutLinkFailure)
        fun onNeedsUser(failure: ScoutLinkFailure)

        /**
         * A connect attempt failed and the loop will try again. [reachedScout] is true when the
         * link came up and a later step failed: the Scout is near and answering.
         */
        fun onAttemptFailed(reachedScout: Boolean) {}
        /** The board's capture changed; [configured] is its answer to the last Configure. */
        fun onCaptureChanged(capture: ScoutCapture?, configured: ConfigureResult?)

        /** The board refused the wanted capture; it is retried with backoff until it takes it. */
        fun onCaptureRejected()
        fun onRecords(batch: ReceivedRecordBatch)
        fun onRawFrames(frames: ReceivedRawFrames)
        fun onStatus(status: BoardStatus)
        fun onOtaAck(ack: OtaAck)
    }

    private val commands = Mutex()
    private var loop: Job? = null

    /** The Stop and close a [disconnect] started; a new link waits for it so the two never overlap. */
    private var closing: Job? = null
    private var address: String? = null
    private var session: ScoutSession? = null
    private var linkLost: CompletableDeferred<ScoutLinkFailure>? = null
    private var wantedCapture: ScoutCapture? = null

    /** The board's refusals in a row of the wanted capture, for the retry backoff. */
    private var captureRejections = 0
    private var captureRetry: Job? = null

    /** True when the app closed the current link itself, so Android still holds it for a while. */
    private var appClosedLink = false

    /** The current link, for the firmware update. Null while not connected. */
    val activeSession: ScoutSession? get() = session

    /** True while the board is capturing for us. */
    val capturing: Boolean get() = session?.activeCapture != null

    /** Keeps the Scout at [scoutAddress] connected until [disconnect]. A no-op when already doing so. */
    fun connect(scoutAddress: String) {
        if (loop?.isActive == true && address == scoutAddress) return
        val previous = loop
        val closePending = closing
        address = scoutAddress
        loop = scope.launch {
            previous?.let { stopAndJoin(it) }
            // Else this link's session could be closed by the earlier link's cleanup.
            closePending?.join()
            runLink(scoutAddress, firstSession = null)
        }
    }

    /**
     * Pairs with the Scout at [scoutAddress] (Android shows its prompt for a new bond) and, on
     * success, keeps it connected as [connect] does. Any other link is stopped first.
     *
     * @throws ScoutLinkException When pairing or setup fails; nothing stays connected then.
     */
    suspend fun pair(scoutAddress: String): ScoutLinkInfo {
        loop?.let { stopAndJoin(it) }
        loop = null
        closing?.join()
        address = scoutAddress
        events.onPhase(ScoutLinkPhase.CONNECTING)
        val newSession = newSession()
        val info = try {
            newSession.establish(scoutAddress, allowPairing = true)
        } catch (e: ScoutLinkException) {
            events.onPhase(ScoutLinkPhase.IDLE)
            // The user is here to act on it and nothing sticks, so one key failure is enough to say so
            throw if (e.failure == ScoutLinkFailure.KEY_REJECTED) {
                ScoutLinkException(ScoutLinkFailure.BOND_MISSING, "key failure while pairing", e)
            } else {
                e
            }
        } catch (e: CancellationException) {
            newSession.close()
            events.onPhase(ScoutLinkPhase.IDLE)
            throw e
        }
        val started = scope.launch { runLink(scoutAddress, firstSession = newSession) }
        // A job cancelled before it starts never runs its cleanup, which would leave this
        // established link open with nobody holding it.
        started.invokeOnCompletion { newSession.close() }
        loop = started
        return info
    }

    /** Stops the board if it is capturing, closes the link and stops reconnecting. */
    fun disconnect() {
        val current = loop ?: return
        loop = null
        address = null
        closing = scope.launch { stopAndJoin(current) }
    }

    /**
     * Ends the current link at once and lets the loop reconnect, for a firmware update that needs
     * a fresh link after the Scout restarts. A no-op without a link.
     */
    fun dropLink() {
        closeForReconnect(session ?: return, ScoutLinkFailure.LINK_LOST)
    }

    /** Sets what the board should capture; null stops it while keeping the link up. */
    fun setCapture(capture: ScoutCapture?) {
        wantedCapture = capture
        scope.launch { applyWantedCapture() }
    }

    private suspend fun runLink(target: String, firstSession: ScoutSession?) {
        var attempt = 0
        var shortLinks = 0
        var keyRejections = 0
        var pending = firstSession
        while (true) {
            var reached = false
            // One link at a time: a session left behind by any path is closed before another opens.
            session?.let { if (it !== pending) endSession(it) }
            val current = pending ?: newSession()
            pending = null
            try {
                val info = current.linkInfo ?: run {
                    events.onPhase(ScoutLinkPhase.CONNECTING)
                    current.establish(target, allowPairing = false)
                }
                attempt = 0
                keyRejections = 0
                captureRejections = 0
                appClosedLink = false
                val connectedAtMs = clock.elapsedMs()
                val lost = CompletableDeferred<ScoutLinkFailure>()
                linkLost = lost
                session = current
                if (current.isOpen) {
                    events.onReady(info)
                    events.onPhase(ScoutLinkPhase.READY)
                    applyWantedCapture()
                } else {
                    // It dropped after its last setup step, before it became current, so no one heard
                    lost.complete(ScoutLinkFailure.LINK_LOST)
                }
                val failure = lost.await()
                Timber.i("Scout link lost: %s", failure)
                endSession(current)
                events.onLinkLost(failure)
                // The first drop reconnects at once; a link that sets up and then drops again at once
                // (a crash looping board) backs off like a failed attempt.
                shortLinks = if (clock.elapsedMs() - connectedAtMs < STABLE_LINK_MS) shortLinks + 1 else 0
                attempt = (shortLinks - 1).coerceAtLeast(0)
                reached = appClosedLink
            } catch (e: CancellationException) {
                // Stopped while connecting or connected: never leave a GATT link open.
                endSession(current)
                throw e
            } catch (e: ScoutLinkException) {
                endSession(current)
                keyRejections = if (e.failure == ScoutLinkFailure.KEY_REJECTED) keyRejections + 1 else 0
                val failure = if (keyRejections >= STALE_KEY_ATTEMPTS) ScoutLinkFailure.BOND_MISSING else e.failure
                if (failure.needsUser) {
                    Timber.w(e, "Scout link needs the user")
                    events.onPhase(ScoutLinkPhase.IDLE)
                    events.onNeedsUser(failure)
                    return
                }
                attempt++
                reached = current.reachedScout
                Timber.i("Scout connect attempt %d failed (reached the Scout: %b): %s", attempt, reached, e.message)
                events.onAttemptFailed(reached)
            } catch (e: Exception) {
                // Anything unexpected must close the link and retry, not end the loop with the
                // GATT client still open (the next connect would then open a second one).
                endSession(current)
                keyRejections = 0
                attempt++
                reached = current.reachedScout
                Timber.e(e, "Scout link attempt %d failed unexpectedly", attempt)
                events.onAttemptFailed(reached)
            }
            events.onPhase(ScoutLinkPhase.CONNECTING)
            if (attempt > 0 || reached) delay(retryDelayMs(attempt, reached))
        }
    }

    /**
     * Makes the board match [wantedCapture]. On a new link that also means a Stop when nothing is
     * wanted, because the board may still be surveying from before (see [ScoutSession.isApplied]).
     */
    private suspend fun applyWantedCapture() = commands.withLock {
        captureRetry?.cancel()
        captureRetry = null
        val current = session ?: return@withLock
        if (current.linkInfo?.compatibility != ScoutCompatibility.COMPATIBLE) return@withLock
        val capture = wantedCapture
        if (current.isApplied(capture)) {
            if (captureRejections > 0) {
                // The refused capture is no longer wanted
                captureRejections = 0
                events.onCaptureChanged(current.activeCapture, current.configureResult)
            }
            return@withLock
        }
        try {
            applyOrRetry(current, capture)
        } catch (e: ScoutLinkException) {
            // A link that cannot carry a command is broken: drop it so the loop reconnects.
            Timber.w(e, "The Scout did not answer the capture settings")
            closeForReconnect(current, e.failure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Applying the Scout capture failed unexpectedly")
            closeForReconnect(current, ScoutLinkFailure.GATT_ERROR)
        }
    }

    private suspend fun applyOrRetry(current: ScoutSession, capture: ScoutCapture?) {
        try {
            current.applyCapture(capture)
            captureRejections = 0
            events.onCaptureChanged(current.activeCapture, current.configureResult)
        } catch (e: ScoutLinkException) {
            if (e.failure != ScoutLinkFailure.REJECTED) throw e
            Timber.w(e, "The Scout refused the capture settings")
            onCaptureRejected(current, e)
        }
    }

    /**
     * Reports the refusal and tries again with backoff. A capture is only wanted while no firmware
     * update holds the Scout, so `INVALID_STATE` then means a board left in its OTA state by an
     * update that ended: OtaAbort frees it for the retry. A refused Stop has nothing to retry for.
     */
    private suspend fun onCaptureRejected(current: ScoutSession, rejection: ScoutLinkException) {
        if (wantedCapture == null) return
        if (rejection.boardResult == ResultCode.RESULT_CODE_INVALID_STATE) current.send(ScoutProtocol.otaAbort())
        captureRejections++
        events.onCaptureRejected()
        val waitMs = backoff.delayAfterAttemptMs(captureRejections)
        captureRetry = scope.launch {
            delay(waitMs)
            captureRetry = null
            applyWantedCapture()
        }
    }

    /** Closes [current] from this side and lets the loop reconnect, after [FRESH_LINK_GAP_MS]. */
    private fun closeForReconnect(current: ScoutSession, failure: ScoutLinkFailure) {
        if (current.isOpen) appClosedLink = true
        current.close()
        linkLost?.complete(failure)
    }

    /** Sends Stop if the board is capturing, then cancels [job], which closes the link. */
    private suspend fun stopAndJoin(job: Job) {
        withContext(NonCancellable) {
            commands.withLock {
                val current = session
                if (current?.activeCapture != null) {
                    withTimeoutOrNull(STOP_TIMEOUT_MS) { runCatching { current.applyCapture(null) } }
                    events.onCaptureChanged(null, null)
                }
            }
            job.cancel()
            session?.let { endSession(it) }
            job.join()
            events.onPhase(ScoutLinkPhase.IDLE)
        }
    }

    /**
     * The wait before the next attempt. After an attempt that reached the Scout, or a link the app
     * closed, it is at least [FRESH_LINK_GAP_MS]: Android keeps the closed link up for 4 s, and an
     * attempt inside that window reuses it with the old ATT state (its MTU request never goes out).
     */
    private fun retryDelayMs(attempt: Int, linkStillHeld: Boolean): Long {
        val delay = backoff.delayAfterAttemptMs(attempt)
        return if (linkStillHeld) maxOf(delay, FRESH_LINK_GAP_MS) else delay
    }

    private fun endSession(ended: ScoutSession) {
        ended.close()
        if (session === ended) {
            session = null
            linkLost = null
        }
    }

    private fun newSession(): ScoutSession {
        lateinit var created: ScoutSession
        created = ScoutSession(transportFactory(), appVersion, object : ScoutSessionListener {
            override fun onRecords(batch: ReceivedRecordBatch) = events.onRecords(batch)
            override fun onRawFrames(frames: ReceivedRawFrames) = events.onRawFrames(frames)
            override fun onStatus(status: BoardStatus) = events.onStatus(status)
            override fun onOtaAck(ack: OtaAck) = events.onOtaAck(ack)
            override fun onLinkLost(failure: ScoutLinkFailure) {
                if (session === created) linkLost?.complete(failure)
            }
        }, clock)
        return created
    }

    private companion object {
        /** How long a Stop may take before the link is closed anyway. */
        const val STOP_TIMEOUT_MS = 3_000L

        /** A link that lasted less than this before dropping counts as a failed attempt for the backoff. */
        const val STABLE_LINK_MS = 10_000L

        /** Longer than the 4 s Android keeps an LE link after its last GATT client closed. */
        const val FRESH_LINK_GAP_MS = 5_000L

        /**
         * Attempts in a row that end in a key failure before they count as a stale bond (the board
         * was erased or bonded to another phone). One can be stray; with the fresh link gap this
         * takes about 10 s. Any other failure, or Bluetooth restarting (which ends this loop),
         * starts the count over, and the Service Changed case fails as a GATT error, not a key failure.
         */
        const val STALE_KEY_ATTEMPTS = 3
    }
}
