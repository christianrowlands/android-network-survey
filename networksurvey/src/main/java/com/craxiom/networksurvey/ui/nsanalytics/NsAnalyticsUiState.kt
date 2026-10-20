package com.craxiom.networksurvey.ui.nsanalytics

import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData

/**
 * Represents the current state of the upload system.
 * Used to provide clear feedback to users about upload availability.
 */
enum class UploadState {
    /** Ready to upload, has records in queue */
    IDLE,

    /** No records in queue */
    EMPTY,

    /** Upload currently in progress */
    UPLOADING,

    /** Cannot upload (no network, MDM blocked, etc.) */
    UNAVAILABLE
}

/**
 * Information about device deregistration (when detected from backend)
 */
data class DeregistrationInfo(
    val deregisteredAt: String?,
    val source: String?, // "web" or "device"
    val deregisteredBy: String?,
    val reason: String?
)

/**
 * UI state for the NS Analytics page: the walkthrough while this phone is not connected, and the
 * connection and its uploads once it is.
 */
data class NsAnalyticsConnectionUiState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val isConnected: Boolean = false,
    val workspace: String? = null,
    val workspaceName: String? = null,
    val apiUrl: String? = null,
    val autoUploadEnabled: Boolean = NsAnalyticsConstants.DEFAULT_AUTO_UPLOAD_ENABLED,
    val uploadFrequencyMinutes: Int = NsAnalyticsConstants.DEFAULT_UPLOAD_FREQUENCY,
    val lastUploadTime: Long = 0,
    val queuedRecords: Int = 0,
    val isUploading: Boolean = false,
    val uploadState: UploadState = UploadState.IDLE,
    val uploadStatusMessage: String = "",
    val uploadProgress: Float = 0f, // Upload progress 0-1
    val uploadedRecords: Int = 0,
    val totalRecordsToUpload: Int = 0,
    val lastUploadResult: String? = null, // e.g., "234 records" or "Failed: quota exceeded"
    val message: String? = null,
    /** True while a survey sends to NS Analytics; disconnecting waits until it stops. */
    val isSurveyActive: Boolean = false,
    val deregistrationInfo: DeregistrationInfo? = null,
    val showQuotaExceededDialog: Boolean = false,
    val quotaCurrentUsage: Int = 0,
    val quotaMaxRecords: Int = 0,
    val quotaMessage: String? = null,
    val quotaWebUrl: String? = null,
    // True when automatic uploads are paused because the workspace record quota was exceeded.
    // Drives the in-app paused banner and keeps the manual "Upload now to resume" action enabled.
    val uploadPausedForQuota: Boolean = false,
    // Registration confirmation dialog states
    val pendingQrData: NsAnalyticsQrData? = null,
    val showRegistrationConfirmDialog: Boolean = false,
    val registering: Boolean = false,
    val registrationError: RegistrationError? = null,
    // Already registered dialog state (shown when user scans QR while registered)
    val showAlreadyRegisteredDialog: Boolean = false,
)
