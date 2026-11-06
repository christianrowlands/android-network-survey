package com.craxiom.networksurvey.scout.firmware

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** Where the Scout's firmware update is, for the firmware update screen and the Scout state. */
sealed interface FirmwareUpdateRun {
    /** No install running and no result to show. */
    data object Idle : FirmwareUpdateRun

    /** Installing [pkg]. [cancelable] is false from OtaEnd on: the board may already switch images. */
    data class Installing(val pkg: FirmwarePackage, val phase: FirmwareInstallPhase) : FirmwareUpdateRun {
        val cancelable: Boolean
            get() = phase is FirmwareInstallPhase.Connecting || phase is FirmwareInstallPhase.Transferring ||
                phase is FirmwareInstallPhase.Reconnecting
    }

    data class Finished(val pkg: FirmwarePackage, val result: FirmwareInstallResult) : FirmwareUpdateRun

    data class Failed(val pkg: FirmwarePackage, val error: FirmwareUpdateError) : FirmwareUpdateRun
}

/**
 * Runs one firmware install at a time in the Scout manager's scope, so it keeps going when the
 * screen is recreated, and holds the Scout for it ([hold]): while held the manager keeps the link
 * up, never captures, reconnects after the restart and shows "Updating firmware" everywhere.
 *
 * @param installerFactory Makes the installer for a package (tests pass shorter timings).
 */
class ScoutFirmwareUpdater internal constructor(
    private val scope: CoroutineScope,
    private val hold: (Boolean) -> Unit,
    private val installerFactory: (FirmwarePackage) -> FirmwareInstaller,
) {
    private val mutableState = MutableStateFlow<FirmwareUpdateRun>(FirmwareUpdateRun.Idle)

    @Volatile
    private var job: Job? = null

    val state: StateFlow<FirmwareUpdateRun> = mutableState.asStateFlow()

    /** True while an install runs. */
    val isInstalling: Boolean get() = job?.isActive == true

    /**
     * Installs [pkg]; a no-op while another install runs.
     *
     * @return False when one already runs.
     */
    fun install(pkg: FirmwarePackage): Boolean {
        if (isInstalling) return false
        mutableState.value = FirmwareUpdateRun.Installing(pkg, FirmwareInstallPhase.Connecting)
        hold(true)
        job = scope.launch {
            mutableState.value = try {
                val result = installerFactory(pkg).run { phase -> mutableState.value = FirmwareUpdateRun.Installing(pkg, phase) }
                Timber.i("Scout firmware %s installed (was %s)", result.newVersion, result.previousVersion)
                FirmwareUpdateRun.Finished(pkg, result)
            } catch (e: FirmwareUpdateError) {
                Timber.w("Scout firmware update failed: %s", e)
                FirmwareUpdateRun.Failed(pkg, e)
            } catch (e: CancellationException) {
                FirmwareUpdateRun.Failed(pkg, FirmwareUpdateError.Canceled())
            } catch (e: Exception) {
                Timber.e(e, "Scout firmware update failed unexpectedly")
                FirmwareUpdateRun.Failed(pkg, FirmwareUpdateError.ProtocolError(e.message ?: e.javaClass.simpleName))
            } finally {
                hold(false)
            }
        }
        return true
    }

    /** Cancels the install while it can still be stopped (before OtaEnd); OtaAbort goes out. */
    fun cancel() {
        val running = mutableState.value as? FirmwareUpdateRun.Installing ?: return
        if (running.cancelable) job?.cancel()
    }

    /**
     * The Scout was forgotten: ends any install and forgets its package and result, so they can
     * never reach another Scout.
     */
    fun reset() {
        val current = job
        if (current == null) {
            mutableState.value = FirmwareUpdateRun.Idle
            return
        }
        current.cancel()
        current.invokeOnCompletion { if (job === current) mutableState.value = FirmwareUpdateRun.Idle }
    }

    /** Forgets a shown result. */
    fun clear() {
        if (!isInstalling) mutableState.value = FirmwareUpdateRun.Idle
    }
}
