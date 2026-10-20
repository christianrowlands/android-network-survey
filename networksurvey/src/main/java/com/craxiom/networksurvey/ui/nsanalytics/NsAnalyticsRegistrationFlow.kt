package com.craxiom.networksurvey.ui.nsanalytics

import android.content.Context
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The steps from a scanned NS Analytics code (or an opened registration link) to a connected
 * phone: confirm the workspace, register, and on failure explain why with Retry. A code scanned
 * while already connected only explains how to switch workspaces.
 *
 * @param onConnected Called with the workspace name once this phone is connected.
 */
internal class NsAnalyticsRegistrationFlow(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<NsAnalyticsConnectionUiState>,
    private val registration: NsAnalyticsRegistration,
    private val onConnected: (String) -> Unit,
) {
    /**
     * Check for pending QR data and show appropriate dialog.
     * Shows registration confirmation if not registered,
     * or already registered dialog if user is already in a workspace.
     */
    fun checkAndProcessQrData() {
        scope.launch {
            try {
                val qrData = NsAnalyticsSecureStorage.getQrData(context)
                if (qrData != null) {
                    if (!NsAnalyticsSecureStorage.isRegistered(context)) {
                        // Not registered - show confirmation dialog to proceed
                        state.value = state.value.copy(
                            pendingQrData = qrData,
                            showRegistrationConfirmDialog = true
                        )
                    } else {
                        // Already registered - show conflict dialog
                        Timber.i(
                            "Device already registered, showing conflict dialog for QR workspace: %s",
                            qrData.workspaceName ?: qrData.workspaceId
                        )
                        state.value = state.value.copy(
                            pendingQrData = qrData,
                            showAlreadyRegisteredDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for QR data")
            }
        }
    }

    /**
     * User confirmed registration - proceed with registering the device
     */
    fun confirmRegistration() {
        val qrData = state.value.pendingQrData
        if (qrData != null) {
            Timber.i("User confirmed registration for workspace: %s", qrData.workspaceId)
            state.value = state.value.copy(showRegistrationConfirmDialog = false)
            scope.launch {
                registerDevice(qrData)
            }
        } else {
            Timber.w("confirmRegistration called but no pending QR data")
        }
    }

    /**
     * User canceled registration - clear pending QR data
     */
    fun cancelRegistration() {
        // The confirmation dialog also dismisses after Connect; only a real cancel clears the code.
        if (!state.value.showRegistrationConfirmDialog) return
        Timber.i("User canceled registration")
        state.value = state.value.copy(
            pendingQrData = null,
            showRegistrationConfirmDialog = false
        )
        // Clear the stored QR data
        NsAnalyticsSecureStorage.clearQrData(context)
    }

    /**
     * Registers this phone with the pending code. On success the page closes back to the Survey
     * screen (`onConnected`); on failure the typed error is shown with Retry.
     */
    private suspend fun registerDevice(qrData: NsAnalyticsQrData) {
        state.value = state.value.copy(registering = true, registrationError = null)
        when (val result = withContext(Dispatchers.IO) { registration.register(qrData) }) {
            is RegistrationResult.Success -> {
                state.value = state.value.copy(
                    registering = false,
                    isRegistered = true,
                    isConnected = true,
                    workspaceName = result.workspaceName,
                    pendingQrData = null,
                )
                onConnected(result.workspaceName)
            }

            // Don't clear QR data on failure so user can retry
            is RegistrationResult.Failure -> state.value = state.value.copy(
                registering = false,
                registrationError = result.error,
            )
        }
    }

    /**
     * User wants to retry registration after an error
     */
    fun retryRegistration() {
        val qrData = state.value.pendingQrData
        if (qrData != null) {
            Timber.i("Retrying registration for workspace: %s", qrData.workspaceId)
            state.value = state.value.copy(registrationError = null)
            scope.launch {
                registerDevice(qrData)
            }
        } else {
            Timber.w("retryRegistration called but no pending QR data")
            state.value = state.value.copy(registrationError = null)
        }
    }

    /**
     * User canceled registration after an error
     */
    fun dismissRegistrationError() {
        // The error dialog also dismisses after Try again; only a real dismissal clears the code.
        if (state.value.registrationError == null) return
        Timber.i("User dismissed registration error")
        state.value = state.value.copy(
            registrationError = null,
            pendingQrData = null
        )
        // Clear the stored QR data
        NsAnalyticsSecureStorage.clearQrData(context)
    }

    /**
     * User dismissed the already registered dialog - clear pending QR data.
     */
    fun dismissAlreadyRegisteredDialog() {
        Timber.i("User dismissed already registered dialog")
        state.value = state.value.copy(
            showAlreadyRegisteredDialog = false,
            pendingQrData = null
        )
        // Clear the stored QR data since user acknowledged
        NsAnalyticsSecureStorage.clearQrData(context)
    }
}
