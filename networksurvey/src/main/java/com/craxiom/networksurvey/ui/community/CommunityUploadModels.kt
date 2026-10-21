package com.craxiom.networksurvey.ui.community

import android.content.Context
import com.craxiom.networksurvey.survey.status.CommunityRetry
import com.craxiom.networksurvey.util.OpenCelliDSigning

/**
 * UI state for the Community databases upload card.
 *
 * @property visible            False when the organization does not allow external uploads.
 * @property cellularQueueCount Cellular records waiting to upload, or -1 while loading.
 * @property wifiQueueCount     Wi-Fi records waiting to upload, or -1 while loading.
 * @property autoUploadLocked   True when the organization sets whether uploads run automatically.
 */
data class UploadUiState(
    val visible: Boolean = true,
    val cellularQueueCount: Int = -1,
    val wifiQueueCount: Int = -1,
    val uploadButtonEnabled: Boolean = false,
    val autoUploadEnabled: Boolean = false,
    val uploadProgress: UploadProgressState = UploadProgressState.Hidden,
    val openCelliDOn: Boolean = false,
    val openCelliDKey: OpenCelliDKey = OpenCelliDKey.ANONYMOUS,
    val beaconDbOn: Boolean = false,
    val autoUploadWifiOnly: Boolean = true,
    val autoUploadLocked: Boolean = false,
)

/** How OpenCelliD uploads are signed, which decides whether they can run at all. */
enum class OpenCelliDKey {
    /** The user's own API key. */
    YOUR_KEY,

    /** The app's shared key, uploading anonymously. */
    ANONYMOUS,

    /** Neither: no valid key, and this build has no shared token, so OpenCelliD uploads cannot run. */
    MISSING;

    companion object {
        /** @return How OpenCelliD uploads are signed now; see [OpenCelliDSigning] for the rule. */
        fun current(context: Context): OpenCelliDKey = when {
            !OpenCelliDSigning.isAnonymous(context) -> YOUR_KEY
            OpenCelliDSigning.hasSharedToken(context) -> ANONYMOUS
            else -> MISSING
        }
    }
}

/** Where a community upload is: not running, running, or just finished with its results. */
sealed class UploadProgressState {
    data object Hidden : UploadProgressState()

    /**
     * [running] is false while the upload waits to start (for Wi-Fi, or to retry after a failure);
     * [retry] is set while it waits to try again after a failed attempt.
     */
    data class InProgress(
        val progress: Int = 0,
        val maxProgress: Int = 100,
        val statusMessage: String = "",
        val running: Boolean = true,
        val retry: CommunityRetry? = null,
    ) : UploadProgressState()

    data class Finished(
        val cancelled: Boolean = false,
        val ocidResult: String = "",
        val beaconDbResult: String = "",
        val ocidResultMessage: String = "",
        val beaconDbResultMessage: String = "",
    ) : UploadProgressState()
}
