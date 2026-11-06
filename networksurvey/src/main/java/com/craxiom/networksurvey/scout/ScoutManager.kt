package com.craxiom.networksurvey.scout

import android.content.Context
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.board.v1.ResultCode
import com.craxiom.networksurvey.scout.firmware.FirmwareInstaller
import com.craxiom.networksurvey.scout.firmware.ScoutFirmwareUpdater
import com.craxiom.networksurvey.scout.link.GattScoutTransport
import com.craxiom.networksurvey.scout.link.ScoutCapture
import com.craxiom.networksurvey.scout.link.ScoutClock
import com.craxiom.networksurvey.scout.link.ScoutLinkException
import com.craxiom.networksurvey.scout.link.ScoutNearbyScanner
import com.craxiom.networksurvey.scout.link.ScoutProtocol
import com.craxiom.networksurvey.scout.link.ScoutScanner
import com.craxiom.networksurvey.scout.link.ScoutSighting
import com.craxiom.networksurvey.scout.link.ScoutTransport
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.ScoutRecordListener
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The process-wide owner of the NS Scout: the paired Scout and the Wi-Fi source (both persisted),
 * the link (connected only while something needs it), pairing, and one [state] that every surface
 * shows. Keyed by Scout ID throughout; today one Scout is paired at a time.
 *
 * Who owns the link: [requestLink] keeps the Scout connected for status (a visible Scout screen),
 * [requestCapture] also makes it capture (a survey or a live Wi-Fi screen, and only while the
 * Scout is the Wi-Fi source). Each owner releases with [release]; with no owner left the board is
 * told to Stop and the link closes. Reconnects with backoff run only while an owner remains.
 *
 * Public methods may be called from any thread; the work runs on the dedicated Scout thread,
 * which also decodes the link and calls [ScoutRecordListener]s.
 */
class ScoutManager internal constructor(
    private val store: ScoutStore,
    private val environment: ScoutEnvironment,
    dispatcher: CoroutineDispatcher,
    transportFactory: () -> ScoutTransport,
    private val scannerFactory: (onSightings: (List<ScoutSighting>) -> Unit, onFailed: (Int) -> Unit) -> ScoutNearbyScanner,
    private val clock: ScoutClock = ScoutClock.SYSTEM,
    appVersion: String = BuildConfig.VERSION_NAME,
    /** Parent of the manager's coroutines; tests pass one they can cancel. The app's never ends. */
    parentJob: Job? = null,
) {
    private val scope = CoroutineScope(
        SupervisorJob(parentJob) + dispatcher + CoroutineExceptionHandler { _, error ->
            // A Scout failure must never take the app down; the link reconnects or shows its state.
            Timber.e(error, "Unexpected NS Scout failure")
        },
    )
    private val linkState = ScoutLinkState(clock)
    private val listeners = CopyOnWriteArraySet<ScoutRecordListener>()
    private val statusOwners = IdentityHashMap<Any, Unit>()
    private val captureOwners = IdentityHashMap<Any, ScoutCapture>()
    private val controller: ScoutLinkController = ScoutLinkController(
        scope, transportFactory, appVersion, clock,
        linkState.events(::onLinkEvent, listeners, { mutablePairedScout.value?.scoutId }, { firmwareLinks.onAck(it) }),
    )
    private val firmwareLinks: ScoutFirmwareLinks = ScoutFirmwareLinks { controller.dropLink() }

    /** True while a firmware update holds the Scout: linked, never capturing. */
    private var updatingFirmware = false
    private val scanner by lazy { scannerFactory(::onSightings, { mutableNearbyScanActive.value = false }) }

    /** True once [start] ran: before that nothing runs on the Scout thread and nothing watches Bluetooth. */
    private val started = AtomicBoolean(false)
    private var pairingJob: Job? = null
    private val linger = ScoutLinkLinger(scope)
    private var graceJob: Job? = null

    private val mutableState = MutableStateFlow<ScoutState>(ScoutState.NotPaired)
    private val mutablePairedScout = MutableStateFlow(store.pairedScout())
    private val mutableWifiSource = MutableStateFlow(initialSource())
    private val mutableNearby = MutableStateFlow<List<NearbyScout>>(emptyList())
    private val mutableNearbyScanActive = MutableStateFlow(false)
    private val mutablePairing = MutableStateFlow<ScoutPairingState>(ScoutPairingState.Idle)

    /** The one Scout status for every surface. */
    val state: StateFlow<ScoutState> = mutableState.asStateFlow()

    /** The paired Scout, null when none is. */
    val pairedScout: StateFlow<PairedScout?> = mutablePairedScout.asStateFlow()

    /** Where Wi-Fi comes from. Always [WifiSource.PHONE] without a paired Scout. */
    val wifiSource: StateFlow<WifiSource> = mutableWifiSource.asStateFlow()

    /** Details for the Scout page. */
    val linkDetails: StateFlow<ScoutLinkDetails> get() = linkState.details

    /** Scouts the pairing scan hears, strongest first. */
    val nearbyScouts: StateFlow<List<NearbyScout>> = mutableNearby.asStateFlow()

    /** True while the pairing scan runs. */
    val nearbyScanActive: StateFlow<Boolean> = mutableNearbyScanActive.asStateFlow()

    /** The "Add a Scout" pairing. */
    val pairing: StateFlow<ScoutPairingState> = mutablePairing.asStateFlow()

    /**
     * Installs firmware on the paired Scout over Bluetooth. While it runs the Scout stays linked,
     * never captures and reads "Updating firmware"; callers refuse to start one while recording.
     */
    val firmwareUpdater = ScoutFirmwareUpdater(
        scope,
        hold = { held ->
            post {
                updatingFirmware = held
                reconcile()
            }
        },
        installerFactory = { pkg -> FirmwareInstaller(firmwareLinks, pkg, appVersion) },
    )

    init {
        if (mutablePairedScout.value != null) start()
    }

    /** True once the manager started working (a Scout is paired or one was asked for). */
    internal val isStarted: Boolean get() = started.get()

    /** Receives the paired Scout's records, raw frames and status on the Scout thread. */
    fun addRecordListener(listener: ScoutRecordListener) {
        listeners.add(listener)
    }

    fun removeRecordListener(listener: ScoutRecordListener) {
        listeners.remove(listener)
    }

    /** Keeps the Scout connected for status while [owner] needs it (a visible Scout screen). */
    fun requestLink(owner: Any) = startAndPost {
        statusOwners[owner] = Unit
        reconcile()
    }

    /**
     * Makes the Scout capture [capture] while [owner] needs it (a survey or a live Wi-Fi screen).
     * Captures from several owners are merged. Calling again replaces this owner's capture.
     */
    fun requestCapture(owner: Any, capture: ScoutCapture) = startAndPost {
        if (captureOwners.isEmpty()) linkState.startCapture()
        captureOwners[owner] = capture
        reconcile()
    }

    /** Ends what [owner] asked for. The board is told to Stop when no capture owner is left. */
    fun release(owner: Any) = postIfStarted {
        statusOwners.remove(owner)
        captureOwners.remove(owner)
        reconcile()
    }

    /**
     * Chooses where Wi-Fi comes from. [WifiSource.SCOUT] needs a paired Scout. Callers keep the
     * source locked while recording (the Wi-Fi controller rejects a switch).
     */
    fun setWifiSource(source: WifiSource) = postIfStarted {
        if (source == WifiSource.SCOUT && mutablePairedScout.value == null) return@postIfStarted
        store.setWifiSource(source)
        mutableWifiSource.value = source
        reconcile()
    }

    /**
     * Forgets the paired Scout: stops it, closes the link and makes the phone the Wi-Fi source.
     * Android keeps its bond (`removeBond` is hidden); the UI points to Bluetooth settings.
     */
    fun removePairedScout() = postIfStarted { forgetPairedScout() }

    /**
     * Erases every pairing on the paired Scout (ForgetBonds, for giving it away) and then forgets it
     * here, as [removePairedScout] does. Needs the link up. [onResult] gets true once the Scout
     * confirmed, on the Scout thread.
     */
    fun erasePairingsOnScout(onResult: (Boolean) -> Unit) = post {
        val session = controller.activeSession
        if (mutablePairedScout.value == null || session == null || linkState.phase != ScoutLinkPhase.READY) {
            onResult(false)
            return@post
        }
        scope.launch {
            val erased = try {
                session.send(ScoutProtocol.forgetBonds()).result == ResultCode.RESULT_CODE_OK
            } catch (e: ScoutLinkException) {
                Timber.w(e, "The Scout did not erase its pairings")
                false
            }
            if (erased) forgetPairedScout()
            onResult(erased)
        }
    }

    private fun forgetPairedScout() {
        val scout = mutablePairedScout.value ?: return
        firmwareUpdater.reset()
        controller.disconnect()
        store.remove(scout.scoutId)
        store.setWifiSource(WifiSource.PHONE)
        mutablePairedScout.value = null
        mutableWifiSource.value = WifiSource.PHONE
        mutablePairing.value = ScoutPairingState.Idle
        linkState.forgetScout()
        reconcile()
    }

    /** Starts the pairing scan (it needs BLUETOOTH_SCAN and Bluetooth on). */
    fun startNearbyScan() = startAndPost {
        mutableNearbyScanActive.value = scanner.start()
    }

    fun stopNearbyScan() = postIfStarted {
        scanner.stop()
        mutableNearbyScanActive.value = false
    }

    /**
     * Pairs with [nearby] (Android shows its prompt) and makes it the paired Scout and the Wi-Fi
     * source, replacing any other. The result arrives in [pairing].
     */
    fun pair(nearby: NearbyScout) = startAndPost {
        if (pairingJob?.isActive == true) return@startAndPost
        scanner.stop()
        mutableNearbyScanActive.value = false
        mutablePairing.value = ScoutPairingState.Pairing(nearby.name)
        pairingJob = scope.launch {
            try {
                val info = controller.pair(nearby.address)
                val scout = PairedScout.from(nearby.address, info.info, clock.wallMs())
                firmwareUpdater.reset()
                store.replacePairedScout(scout)
                store.setWifiSource(WifiSource.SCOUT)
                mutablePairedScout.value = scout
                mutableWifiSource.value = WifiSource.SCOUT
                linkState.clearIssue()
                mutablePairing.value = ScoutPairingState.Paired(scout)
            } catch (e: ScoutLinkException) {
                mutablePairing.value = ScoutPairingState.Failed(nearby.name, e.failure)
            } finally {
                pairingJob = null
                reconcile()
            }
        }
    }

    /** Clears a shown pairing result. */
    fun clearPairingResult() = postIfStarted {
        if (pairingJob == null) mutablePairing.value = ScoutPairingState.Idle
    }

    /** Re-reads the permission and Bluetooth state, for example after the permission was granted. */
    fun refreshEnvironment() = postIfStarted { reconcile() }

    /**
     * Starts the Bluetooth receiver and the first reconcile when first needed (a paired Scout, the
     * pairing scan, a pairing, a link or a capture), so a phone that never pairs a Scout has neither.
     */
    private fun start() {
        if (!started.compareAndSet(false, true)) return
        environment.watchBluetooth { post { reconcile() } }
        post { reconcile() }
    }

    private fun startAndPost(block: () -> Unit) = start().also { post(block) }

    /** Runs [block] once started; before that there is no Scout, no owner and nothing to change. */
    private fun postIfStarted(block: () -> Unit) {
        if (started.get()) post(block)
    }

    private fun post(block: () -> Unit) {
        scope.launch { block() }
    }

    private fun initialSource(): WifiSource =
        if (store.pairedScout() == null) WifiSource.PHONE else store.wifiSource()

    private fun scoutCaptureWanted(): Boolean =
        captureOwners.isNotEmpty() && mutableWifiSource.value == WifiSource.SCOUT

    /** Connects or disconnects and sets the capture to match what is wanted now, then republishes. */
    private fun reconcile() {
        if (pairingJob == null) {
            val scout = mutablePairedScout.value
            val reachable = scout != null && environment.isBluetoothOn() && environment.hasConnectPermission() && linkState.issue == null
            val owned = statusOwners.isNotEmpty() || scoutCaptureWanted() || updatingFirmware
            val capture = scoutCaptureWanted() && !updatingFirmware
            controller.setCapture(if (capture) captureOwners.values.reduce(ScoutCapture::merge) else null)
            when {
                reachable && owned -> linger.cancel().also { controller.connect(scout!!.address) }
                // The last owner just left: keep the link a moment for the next screen (UX review 8)
                reachable && linkState.phase != ScoutLinkPhase.IDLE -> linger.letGoLater { if (pairingJob == null) controller.disconnect().also { publish() } }
                else -> linger.cancel().also { controller.disconnect() }
            }
        }
        publish()
    }

    private fun onLinkEvent(event: ScoutLinkState.Event) {
        when (event) {
            is ScoutLinkState.Event.Ready -> mutablePairedScout.value?.let { scout ->
                val updated = scout.updatedWith(event.info.info)
                if (updated != scout) {
                    store.update(updated)
                    mutablePairedScout.value = updated
                }
            }

            ScoutLinkState.Event.Changed -> Unit
        }
        publish()
    }

    /**
     * Publishes what the scan heard. A paired Scout advertising no bond only changes its row:
     * anyone can send that advertisement, so Pairing lost comes from the link's own key failures.
     */
    private fun onSightings(heard: List<ScoutSighting>) {
        val paired = mutablePairedScout.value
        mutableNearby.value = heard.map { NearbyScout.from(it, paired) }
    }

    private fun publish() {
        val inputs = linkState.inputs(
            scout = mutablePairedScout.value,
            source = mutableWifiSource.value,
            hasPermission = environment.hasConnectPermission(),
            bluetoothOn = environment.isBluetoothOn(),
            capturing = controller.capturing,
        ).copy(updatingFirmware = updatingFirmware)
        mutableState.value = ScoutStateMapper.map(inputs)
        val firmwareBlock = when {
            linkState.issue != null -> FirmwareLinkBlock.PAIRING_LOST
            !inputs.bluetoothOn || !inputs.hasPermission -> FirmwareLinkBlock.NO_BLUETOOTH
            else -> null
        }
        firmwareLinks.update(controller.activeSession, linkState.phase == ScoutLinkPhase.READY, firmwareBlock)
        graceJob?.cancel()
        graceJob = ScoutStateMapper.graceEndsAtMs(inputs)?.let { end ->
            scope.launch {
                delay(end - inputs.nowMs)
                publish()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: ScoutManager? = null

        /** @return The process-wide manager, created on first use with its own Scout thread. */
        @JvmStatic
        fun getInstance(context: Context): ScoutManager = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        /**
         * Makes [manager] the process-wide manager if none was created yet, for the debug-only fake
         * Scout (`ScoutDebugActivity`). @return False when a manager already exists.
         */
        internal fun installIfAbsent(manager: ScoutManager): Boolean = synchronized(this) {
            if (instance != null) return false
            instance = manager
            true
        }

        private fun create(context: Context): ScoutManager {
            // Started on first use, so a phone that never pairs a Scout never runs it.
            val thread = ScoutThread()
            return ScoutManager(
                store = ScoutStore(PreferenceManager.getDefaultSharedPreferences(context)),
                environment = AndroidScoutEnvironment(context) { thread.handler },
                dispatcher = thread.dispatcher,
                transportFactory = { GattScoutTransport(context, thread.handler) },
                scannerFactory = { onSightings, onFailed -> ScoutScanner(context, thread.handler, onSightings, onFailed) },
            )
        }
    }
}
