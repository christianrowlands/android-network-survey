package com.craxiom.networksurvey.survey.status

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LinkFailure
import com.craxiom.networksurvey.survey.engine.LinkState
import com.craxiom.networksurvey.survey.engine.StreamLink
import com.craxiom.networksurvey.survey.model.Destination

/**
 * Works out what is happening to a destination's records: the status the Survey screen, the
 * destination pages and the survey notification all show. A word says what happens to the data,
 * not what the app is doing, so "Sending" never shows while records sit on the phone.
 */
object DestinationStatusRules {

    /**
     * How long a first connection attempt shows as Connecting (amber) before it turns into
     * Can't connect (red). A broker or server that hasn't answered by then is treated as unreachable.
     */
    const val CONNECTING_ESCALATION_MS = 20_000L

    /** Whether automatic upload is on for the destinations that upload later. */
    data class AutoUpload(val nsAnalytics: Boolean = true, val community: Boolean = true)

    /**
     * @param destination The destination.
     * @param runtime     What it is doing in the running survey, or null when it isn't running
     *                    (the uploading destinations still have a status for their queue).
     * @param paused      True while scanning is paused.
     * @param queues      What waits on the phone for NS Analytics and the community databases.
     * @param autoUpload  Whether automatic upload is on.
     * @param nowMs       The current time, for the Connecting to Can't connect escalation.
     */
    fun status(
        destination: Destination,
        runtime: DestinationRuntime?,
        paused: Boolean,
        queues: DeliveryQueues,
        autoUpload: AutoUpload,
        nowMs: Long,
    ): DestinationStatus = when (destination) {
        Destination.LOG_FILES -> logFiles(runtime, paused)
        Destination.NS_ANALYTICS -> nsAnalytics(queues.nsAnalytics, autoUpload.nsAnalytics, paused)
        Destination.MQTT, Destination.GRPC -> stream(runtime?.link, paused, nowMs)
        Destination.COMMUNITY -> community(runtime, queues.community, autoUpload.community, paused)
    }

    private fun logFiles(runtime: DestinationRuntime?, paused: Boolean): DestinationStatus {
        val failed = runtime?.failedTypes.orEmpty()
        return when {
            failed.isNotEmpty() -> DestinationStatus(StatusWord.PARTLY_SAVING, StatusDetail.FilesFailed(failed))
            paused -> DestinationStatus(StatusWord.PAUSED)
            else -> DestinationStatus(StatusWord.SAVING)
        }
    }

    /**
     * NS Analytics uploads later, so its status is about the queue on the phone. Records waiting
     * with automatic upload off are held (amber) with an Upload now action.
     */
    fun nsAnalytics(queue: NsAnalyticsQueue, autoUpload: Boolean, paused: Boolean = false): DestinationStatus {
        val pending = queue.pending
        return when {
            queue.quotaPaused -> DestinationStatus(StatusWord.QUOTA_PAUSED, StatusDetail.QuotaReached(pending))
            queue.uploading -> DestinationStatus(StatusWord.UPLOADING, StatusDetail.Uploading(pending))
            pending > 0 && queue.uploadQueued -> waiting(StatusWord.QUEUED, pending, WaitReason.UploadStarting)
            !autoUpload -> DestinationStatus(
                StatusWord.NOT_UPLOADING,
                StatusDetail.Waiting(pending, WaitReason.AutoUploadOff),
                StatusAction.UPLOAD_NOW.takeIf { pending > 0 },
            )

            pending > 0 && queue.lastUploadFailed -> DestinationStatus(
                StatusWord.WILL_RETRY,
                StatusDetail.Waiting(pending, WaitReason.LastUploadFailed),
                StatusAction.UPLOAD_NOW,
            )

            pending > 0 && !queue.online -> waiting(StatusWord.WAITING_FOR_INTERNET, pending, WaitReason.Offline)
            pending > 0 -> waiting(
                StatusWord.QUEUED,
                pending,
                WaitReason.NextUpload(queue.nextUploadMs, queue.uploadIntervalMinutes),
            )

            paused -> DestinationStatus(StatusWord.PAUSED)
            else -> DestinationStatus(StatusWord.UP_TO_DATE, StatusDetail.NothingWaiting(queue.lastUploadMs))
        }
    }

    /**
     * MQTT and gRPC hold records while they connect, so not being connected is amber; a first
     * attempt that hasn't connected after [CONNECTING_ESCALATION_MS] turns red, because nothing has
     * arrived and the settings likely need fixing.
     */
    private fun stream(link: StreamLink?, paused: Boolean, nowMs: Long): DestinationStatus = when {
        link == null -> DestinationStatus(if (paused) StatusWord.PAUSED else StatusWord.LIVE)
        link.failure == LinkFailure.INVALID_SETTINGS ->
            DestinationStatus(StatusWord.SETTINGS_INVALID, StatusDetail.SettingsInvalid)

        link.state == LinkState.FAILED || link.failure == LinkFailure.SIGN_IN_REJECTED ->
            DestinationStatus(StatusWord.SIGN_IN_FAILED, StatusDetail.SignInRejected)

        link.dropping -> DestinationStatus(StatusWord.DROPPING, StatusDetail.QueueFull)
        link.state == LinkState.CONNECTING && link.everConnected ->
            DestinationStatus(StatusWord.RECONNECTING, StatusDetail.StreamLost(link.sinceMs, link.waiting))

        link.state == LinkState.CONNECTING && nowMs - link.sinceMs < CONNECTING_ESCALATION_MS ->
            DestinationStatus(StatusWord.CONNECTING, StatusDetail.StreamConnecting(link.waiting))

        link.state == LinkState.CONNECTING ->
            DestinationStatus(StatusWord.CANT_CONNECT, StatusDetail.StreamUnreachable(link.sinceMs, link.waiting))

        paused -> DestinationStatus(StatusWord.PAUSED)
        else -> DestinationStatus(StatusWord.LIVE)
    }

    /**
     * The community databases collect records and upload them in batches, so collecting is "on
     * its way"; a failed upload, repeated failures and automatic upload being off hold the records
     * (amber).
     */
    private fun community(
        runtime: DestinationRuntime?,
        queue: CommunityQueue,
        autoUpload: Boolean,
        paused: Boolean,
    ): DestinationStatus {
        val upload = runtime?.upload
        val pending = queue.pending
        return when {
            upload?.keyRejected == true -> DestinationStatus(StatusWord.KEY_REJECTED, StatusDetail.KeyRejected)
            queue.uploading -> DestinationStatus(StatusWord.UPLOADING, StatusDetail.Uploading(pending))
            queue.uploadQueued -> waiting(StatusWord.QUEUED, pending, queuedReason(queue))
            queue.lastFailure != null -> uploadFailed(queue.lastFailure)
            upload != null && upload.retryAtMs > 0 -> DestinationStatus(
                StatusWord.WILL_RETRY,
                StatusDetail.UploadsFailed(upload.failures, upload.retryAtMs),
                StatusAction.UPLOAD_NOW.takeIf { pending > 0 },
            )

            !autoUpload -> DestinationStatus(
                StatusWord.NOT_UPLOADING,
                StatusDetail.Waiting(pending, WaitReason.AutoUploadOff),
                StatusAction.UPLOAD_NOW.takeIf { pending > 0 },
            )

            paused -> DestinationStatus(StatusWord.PAUSED)
            else -> waiting(
                StatusWord.COLLECTING,
                pending,
                WaitReason.EveryRecords(NetworkSurveyConstants.AUTO_UPLOAD_RECORD_THRESHOLD),
            )
        }
    }

    /**
     * NS Analytics while no survey runs, for the "Still to do" panel: the periodic upload only runs
     * during a survey, so records left after Stop upload when asked or during the next survey.
     */
    fun nsAnalyticsIdle(queue: NsAnalyticsQueue, autoUpload: Boolean): DestinationStatus {
        val pending = queue.pending
        val reason = when {
            queue.uploadQueued && !queue.online -> WaitReason.Offline
            queue.uploadQueued -> WaitReason.UploadStarting
            !autoUpload -> WaitReason.AutoUploadOff
            queue.lastUploadFailed -> WaitReason.LastUploadFailed
            queue.nextUploadMs != null -> WaitReason.NextUpload(queue.nextUploadMs, queue.uploadIntervalMinutes)
            else -> WaitReason.NextSurvey
        }
        return when {
            queue.quotaPaused -> DestinationStatus(StatusWord.QUOTA_PAUSED, StatusDetail.QuotaReached(pending))
            queue.uploading -> DestinationStatus(StatusWord.UPLOADING, StatusDetail.Uploading(pending))
            queue.uploadQueued -> DestinationStatus(StatusWord.QUEUED, StatusDetail.Waiting(pending, reason))
            else -> DestinationStatus(StatusWord.NOT_UPLOADING, StatusDetail.Waiting(pending, reason), StatusAction.UPLOAD_NOW)
        }
    }

    /**
     * The community databases while no survey runs. With automatic upload on, a survey uploads what
     * is left when it stops, so records still waiting upload when asked or during the next survey.
     * A failed upload says so until the user opens the Community databases page or uploads again.
     */
    fun communityIdle(queue: CommunityQueue, autoUpload: Boolean): DestinationStatus {
        val pending = queue.pending
        return when {
            queue.uploading -> DestinationStatus(StatusWord.UPLOADING, StatusDetail.Uploading(pending))
            queue.uploadQueued -> waiting(StatusWord.QUEUED, pending, queuedReason(queue))
            queue.lastFailure != null -> uploadFailed(queue.lastFailure)
            else -> DestinationStatus(
                StatusWord.NOT_UPLOADING,
                StatusDetail.Waiting(
                    pending,
                    if (autoUpload) WaitReason.NextCommunitySurvey else WaitReason.AutoUploadOff,
                ),
                StatusAction.UPLOAD_NOW,
            )
        }
    }

    /**
     * @return When the next first connection attempt turns from Connecting into Can't connect, so
     * the notification can be refreshed then; null if no connection is waiting to escalate.
     */
    fun nextEscalationMs(destinations: Collection<DestinationRuntime>, nowMs: Long): Long? =
        destinations.mapNotNull { it.link }
            .filter { it.state == LinkState.CONNECTING && !it.everConnected }
            .map { it.sinceMs + CONNECTING_ESCALATION_MS }
            .filter { it > nowMs }
            .minOrNull()

    /**
     * @return The status of each destination the survey service can speak for on its own, in row
     * order, for the notification. NS Analytics is left out: its queue lives in the database and
     * WorkManager, which the notification doesn't read.
     */
    fun serviceStatuses(
        destinations: Map<Destination, DestinationRuntime>,
        paused: Boolean,
        communityAutoUpload: Boolean,
        nowMs: Long,
    ): Map<Destination, DestinationStatus> = Destination.entries
        .filter { it in destinations && it != Destination.NS_ANALYTICS }
        .associateWith {
            status(it, destinations[it], paused, DeliveryQueues(), AutoUpload(community = communityAutoUpload), nowMs)
        }

    /** @return Why a queued community upload hasn't started: the network it waits for, or nothing. */
    private fun queuedReason(queue: CommunityQueue): WaitReason = when (queue.networkWait) {
        NetworkWait.WIFI -> WaitReason.OnWifi
        NetworkWait.ANY -> WaitReason.Offline
        NetworkWait.NONE -> WaitReason.UploadStarting
    }

    /** "Last upload failed: ...", with the way to the Community databases page that explains it. */
    private fun uploadFailed(failure: CommunityUploadFailure) =
        DestinationStatus(StatusWord.UPLOAD_FAILED, StatusDetail.UploadFailed(failure.message), StatusAction.OPEN)

    private fun waiting(word: StatusWord, pending: Int, reason: WaitReason) =
        DestinationStatus(word, StatusDetail.Waiting(pending, reason))
}
