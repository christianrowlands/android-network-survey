package com.craxiom.networksurvey.survey.status

import com.craxiom.networksurvey.survey.model.RecordType

/**
 * What is happening to a destination's records, from the user's point of view. The color says
 * whether they need to act.
 */
enum class StatusLevel {
    /** Records reach the destination. Nothing to do. */
    ARRIVING,

    /** Short-lived or scheduled: an upload is coming. */
    ON_ITS_WAY,

    /** Records are kept on the phone, not lost, but won't arrive without a retry or without you. */
    HELD,

    /** Records are not reaching the destination. Needs you now. */
    NOT_ARRIVING,
}

/** The status word shown on a destination row, with its level. */
enum class StatusWord(val level: StatusLevel) {
    /** Log files: writing every record type. */
    SAVING(StatusLevel.ARRIVING),

    /** Log files: some record types couldn't get a file. */
    PARTLY_SAVING(StatusLevel.NOT_ARRIVING),

    /** NS Analytics or Community: an upload is running. */
    UPLOADING(StatusLevel.ON_ITS_WAY),

    /** NS Analytics: records wait for the next automatic upload, or an upload is about to start. */
    QUEUED(StatusLevel.ON_ITS_WAY),

    /** NS Analytics: nothing waiting. */
    UP_TO_DATE(StatusLevel.ARRIVING),

    /** NS Analytics: records wait for the phone to be online. */
    WAITING_FOR_INTERNET(StatusLevel.ON_ITS_WAY),

    /** NS Analytics or Community: automatic upload is off, so records stay on the phone. */
    NOT_UPLOADING(StatusLevel.HELD),

    /** NS Analytics or Community: the last upload failed; it tries again on its own. */
    WILL_RETRY(StatusLevel.HELD),

    /** NS Analytics: the workspace's record quota is reached. */
    QUOTA_PAUSED(StatusLevel.HELD),

    /** MQTT or gRPC: connecting for the first time; records are held until it connects. */
    CONNECTING(StatusLevel.HELD),

    /** MQTT or gRPC: hasn't connected after [DestinationStatusRules.CONNECTING_ESCALATION_MS]. */
    CANT_CONNECT(StatusLevel.NOT_ARRIVING),

    /** MQTT: the broker refused the user name or password. */
    SIGN_IN_FAILED(StatusLevel.NOT_ARRIVING),

    /** MQTT: the broker settings are not valid, so it never tried to connect. */
    SETTINGS_INVALID(StatusLevel.NOT_ARRIVING),

    /** MQTT or gRPC: connected and sending. */
    LIVE(StatusLevel.ARRIVING),

    /** MQTT or gRPC: the connection dropped; records are held until it comes back. */
    RECONNECTING(StatusLevel.HELD),

    /** MQTT or gRPC: the queue is full and new records are thrown away. */
    DROPPING(StatusLevel.NOT_ARRIVING),

    /** Scanning is paused (low battery, or a full MQTT queue with MQTT the only destination). */
    PAUSED(StatusLevel.HELD),

    /** Community: records collect until the next automatic upload. */
    COLLECTING(StatusLevel.ON_ITS_WAY),

    /** Community: OpenCelliD refused the API key. */
    KEY_REJECTED(StatusLevel.NOT_ARRIVING),
}

/** The second line of a destination row, as facts; the screen and the notification word it. */
sealed interface StatusDetail {
    /** The destination's own description (free space, workspace, broker, address, mapping). */
    data object Default : StatusDetail

    /** Log files couldn't create the file for [types]. */
    data class FilesFailed(val types: Set<RecordType>) : StatusDetail

    /** An upload of [count] records is running. */
    data class Uploading(val count: Int) : StatusDetail

    /** [count] records wait on the phone, for [reason]. */
    data class Waiting(val count: Int, val reason: WaitReason) : StatusDetail

    /** Nothing waits; the last upload finished at [lastUploadMs], if any did. */
    data class NothingWaiting(val lastUploadMs: Long?) : StatusDetail

    /** The workspace's record quota is reached; [count] records wait. */
    data class QuotaReached(val count: Int) : StatusDetail

    /** Connecting for the first time; [waiting] records are held. */
    data class StreamConnecting(val waiting: Int) : StatusDetail

    /** Couldn't connect since [sinceMs]; [waiting] records are held. */
    data class StreamUnreachable(val sinceMs: Long, val waiting: Int) : StatusDetail

    /** The connection dropped at [sinceMs]; [waiting] records are held. */
    data class StreamLost(val sinceMs: Long, val waiting: Int) : StatusDetail

    /** The broker refused the user name or password. */
    data object SignInRejected : StatusDetail

    /** The broker settings are not valid. */
    data object SettingsInvalid : StatusDetail

    /** The queue is full, so new records are thrown away. */
    data object QueueFull : StatusDetail

    /** [failures] uploads failed in a row; automatic upload tries again at [retryAtMs]. */
    data class UploadsFailed(val failures: Int, val retryAtMs: Long) : StatusDetail

    /** OpenCelliD refused the API key. */
    data object KeyRejected : StatusDetail
}

/** Why records wait on the phone. */
sealed interface WaitReason {
    /** The next automatic upload is at [atMs], or every [intervalMinutes] when the time is unknown. */
    data class NextUpload(val atMs: Long?, val intervalMinutes: Int) : WaitReason

    /** An upload was asked for and starts as soon as it can. */
    data object UploadStarting : WaitReason

    /** Automatic upload is off. */
    data object AutoUploadOff : WaitReason

    /** The last upload failed. */
    data object LastUploadFailed : WaitReason

    /** The phone is offline; the upload runs once it is back online. */
    data object Offline : WaitReason

    /** Community automatic upload runs once about [records] new records are waiting. */
    data class EveryRecords(val records: Int) : WaitReason

    /** The phone isn't on Wi-Fi; the upload runs once it is. */
    data object OnWifi : WaitReason

    /** No survey runs: the community databases upload on their own during the next survey that sends to them. */
    data object NextCommunitySurvey : WaitReason

    /** No survey runs: NS Analytics uploads on its own during the next survey that sends to it. */
    data object NextSurvey : WaitReason
}

/** Something the user can do right from the status. */
enum class StatusAction {
    /** Upload the waiting records now. */
    UPLOAD_NOW,
}

/**
 * A destination's status: the word (with its level), the detail line, and an action, if any.
 */
data class DestinationStatus(
    val word: StatusWord,
    val detail: StatusDetail = StatusDetail.Default,
    val action: StatusAction? = null,
) {
    /** The level, which picks the color. */
    val level: StatusLevel
        get() = word.level
}
