package com.craxiom.networksurvey.ui.community

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.status.CommunityUploadFailure
import com.craxiom.networksurvey.util.MdmUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Backs the Community databases page: the records waiting to upload, starting an upload, and the
 * running or finished upload from [CommunityUploadRepository]. Turning the community databases on
 * or off is part of the survey setup, not this page.
 */
class CommunityUploadViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val repository = CommunityUploadRepository.get(application)
    private val restrictions = AndroidRestrictionsSource(application)
    private val queue = MutableStateFlow(UploadUiState(visible = MdmUtils.isExternalDataUploadAllowed(application)))

    /** The page's state, refreshed every few seconds while it is on screen. */
    val state: StateFlow<UploadUiState> = combine(queue.onStart { startPolling() }, repository.state) { queue, upload ->
        queue.copy(uploadProgress = upload)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), queue.value)

    private var polling = false

    fun shouldShowUploadConfigDialog(): Boolean = CommunityUploadLauncher.shouldConfirm(context)

    /** Saves the "don't show again" choice from the upload dialog. */
    fun setDontShowUploadDialog(dontShowAgain: Boolean) = CommunityUploadLauncher.setSkipConfirmation(context, dontShowAgain)

    /** Uploads with the targets saved in the settings, skipping the dialog. */
    fun startUploadWithSavedSettings(): Boolean = started(CommunityUploadLauncher.startWithSavedSettings(context))

    /**
     * Saves the upload choices and enqueues the upload.
     *
     * @return false if there is no network, so the caller can say so.
     */
    fun startUpload(uploadToOpenCellId: Boolean, anonymously: Boolean, uploadToBeaconDb: Boolean, retry: Boolean): Boolean =
        started(CommunityUploadLauncher.startWithChoices(context, uploadToOpenCellId, anonymously, uploadToBeaconDb, retry))

    /** Disables the upload button once an upload was enqueued. */
    private fun started(enqueued: Boolean): Boolean {
        if (enqueued) queue.value = queue.value.copy(uploadButtonEnabled = false)
        return enqueued
    }

    fun cancelUpload() = repository.cancel()

    /** The last upload's failure, which this page shows in its results; see [acknowledgeUploadFailure]. */
    val uploadFailure: StateFlow<CommunityUploadFailure?> = repository.lastFailure

    /** The page showed the failure, so the Survey screen stops repeating it. */
    fun acknowledgeUploadFailure() = repository.acknowledge()

    /** Turns uploads to OpenCelliD on or off. */
    fun setOpenCelliD(on: Boolean) = setPreference(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, on)

    /** Turns uploads to BeaconDB on or off. */
    fun setBeaconDb(on: Boolean) = setPreference(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, on)

    /** Turns automatic uploads on or off. Ignored while the organization sets it. */
    fun setAutoUpload(on: Boolean) {
        if (queue.value.autoUploadLocked) return
        setPreference(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, on)
    }

    /** Turns on "Override organization settings", which unlocks automatic uploads. */
    fun useOwnSettings() = setPreference(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, true)

    private fun setPreference(key: String, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferences.edit(commit = true) { putBoolean(key, value) }
            refreshQueueCounts()
        }
    }

    private fun uploadToOpenCelliD() = preferences.getBoolean(
        NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
    )

    private fun uploadToBeaconDb() = preferences.getBoolean(
        NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
    )

    private fun openCelliDKey(): OpenCelliDKey = OpenCelliDKey.current(context)

    private fun startPolling() {
        if (polling) return
        polling = true
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshQueueCounts()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshQueueCounts() {
        try {
            val (cellular, wifi) = CommunityUploadLauncher.pending(context)
            val snapshot = restrictions.snapshot()
            val autoUploadLocked = !preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false) &&
                snapshot.contains(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED)
            queue.value = queue.value.copy(
                cellularQueueCount = cellular,
                wifiQueueCount = wifi,
                uploadButtonEnabled = cellular + wifi > 0 && repository.state.value !is UploadProgressState.InProgress,
                autoUploadEnabled = if (autoUploadLocked) {
                    snapshot.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED) == true &&
                        MdmUtils.isExternalDataUploadAllowed(context)
                } else {
                    PreferenceUtils.isAutoUploadEnabled(context)
                },
                autoUploadLocked = autoUploadLocked,
                autoUploadWifiOnly = PreferenceUtils.isAutoUploadWifiOnly(context),
                openCelliDOn = uploadToOpenCelliD(),
                openCelliDKey = openCelliDKey(),
                beaconDbOn = uploadToBeaconDb(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Could not read the community upload queue")
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 6_000L
    }
}
