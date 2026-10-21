package com.craxiom.networksurvey.survey.status

import com.craxiom.networksurvey.constants.NsAnalyticsConstants

/**
 * The records NS Analytics holds on the phone and how its uploads are going.
 *
 * @property pending              Records waiting to upload.
 * @property uploading            True while an upload runs.
 * @property uploadQueued         True while an upload was asked for but hasn't started.
 * @property nextUploadMs         When the next automatic upload runs, if scheduled and known.
 * @property uploadIntervalMinutes How often automatic uploads run.
 * @property lastUploadMs         When the last successful upload finished, if any did.
 * @property lastUploadFailed     True if the last upload failed for a reason other than quota.
 * @property quotaPaused          True while uploads are paused because the quota is reached.
 * @property online               True if the phone has a network connection.
 */
data class NsAnalyticsQueue(
    val pending: Int = 0,
    val uploading: Boolean = false,
    val uploadQueued: Boolean = false,
    val nextUploadMs: Long? = null,
    val uploadIntervalMinutes: Int = NsAnalyticsConstants.DEFAULT_UPLOAD_FREQUENCY,
    val lastUploadMs: Long? = null,
    val lastUploadFailed: Boolean = false,
    val quotaPaused: Boolean = false,
    val online: Boolean = true,
)

/**
 * The records the community databases hold on the phone and how their uploads are going.
 *
 * @property pending      Records waiting to upload (Cellular and Wi-Fi).
 * @property uploading    True while an upload runs.
 * @property uploadQueued True while an upload was asked for and hasn't made its first attempt
 *                        yet (for example it waits for Wi-Fi).
 * @property networkWait  The network the waiting upload needs, if the phone isn't on one.
 * @property lastFailure  The last upload's failure, until the user opens the Community databases
 *                        page or the next upload starts; null when it succeeded or was cancelled.
 * @property retrying     Set while an upload waits to try again after a failed attempt.
 */
data class CommunityQueue(
    val pending: Int = 0,
    val uploading: Boolean = false,
    val uploadQueued: Boolean = false,
    val networkWait: NetworkWait = NetworkWait.NONE,
    val lastFailure: CommunityUploadFailure? = null,
    val retrying: CommunityRetry? = null,
)

/**
 * A community upload that failed or only partly succeeded. [message] is the upload's own result
 * text for the worst failing target ("Invalid API key"), already in the user's language.
 */
data class CommunityUploadFailure(val message: String)

/**
 * A community upload whose last attempt failed and that tries again on its own.
 *
 * @property message   Why the attempt failed, naming the target ("OpenCelliD: Server error"),
 *                     already in the user's language.
 * @property nextTryMs When it tries again, if known.
 */
data class CommunityRetry(val message: String, val nextTryMs: Long?)

/** The network a queued upload waits for before it can start. */
enum class NetworkWait {
    /** It doesn't wait for a network: the phone is on one it may use. */
    NONE,

    /** Any connection; the phone is offline. */
    ANY,

    /** Wi-Fi (an unmetered network); the phone is offline or on mobile data. */
    WIFI,
}

/** What waits on the phone for the destinations that upload later. */
data class DeliveryQueues(
    val nsAnalytics: NsAnalyticsQueue = NsAnalyticsQueue(),
    val community: CommunityQueue = CommunityQueue(),
)
