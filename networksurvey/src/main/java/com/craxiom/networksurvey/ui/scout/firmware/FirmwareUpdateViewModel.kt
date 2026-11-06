package com.craxiom.networksurvey.ui.scout.firmware

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.firmware.FirmwareCatalog
import com.craxiom.networksurvey.scout.firmware.FirmwareCatalogs
import com.craxiom.networksurvey.scout.firmware.FirmwareConsent
import com.craxiom.networksurvey.scout.firmware.FirmwarePackage
import com.craxiom.networksurvey.scout.firmware.FirmwareReleaseCache
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateError
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateRun
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateService
import com.craxiom.networksurvey.scout.link.ScoutBluetooth
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutState
import com.craxiom.networksurvey.services.NetworkSurveyService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The firmware update screen's view model. It checks the signed release for the Scout as soon as
 * the Scout's Info is known, downloads and verifies the image on Update, and hands the package to
 * the manager's updater, which runs the install in the Scout's own scope (so it survives this
 * screen), with [FirmwareUpdateService] keeping it alive in the background.
 *
 * The screen keeps the Scout connected while it is in front, and no update starts while a survey
 * records, with Bluetooth off, or before the Scout is connected. A download that finishes while the
 * app is in the background waits for the screen to return: the foreground service can only start
 * from the foreground.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareUpdateViewModel(application: Application) : AndroidViewModel(application) {

    // Overridable for tests
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var catalog: FirmwareCatalog = FirmwareCatalogs.official(application)

    private val manager = ScoutManager.getInstance(application)
    private val updater = manager.firmwareUpdater
    private val service = MutableStateFlow<NetworkSurveyService?>(null)
    private val recording = service.flatMapLatest { it?.surveyStateFlow ?: flowOf(null) }.map { it?.isRecording == true }
    private val environment = MutableStateFlow(Environment())
    private val localStage = MutableStateFlow<FirmwareStage>(FirmwareStage.Checking)
    private val accepted = MutableStateFlow<Set<FirmwareConsent>>(emptySet())
    private val linkOwner = Any()
    private var work: Job? = null
    private var resumed = false

    /** A verified package that waits for the screen to return before it installs. */
    private var waitingPackage: FirmwarePackage? = null

    /** The phone's side of reaching the Scout, re-read on every resume and after a fix. */
    private data class Environment(val permission: Boolean = true, val bluetooth: Boolean = true)

    val uiState: StateFlow<FirmwareUpdateUi> = combine(
        combine(manager.state, manager.linkDetails, ::Pair),
        updater.state, localStage, accepted,
        combine(recording, environment, ::Pair),
    ) { (state, details), run, local, accepted, (recording, environment) ->
        val stage = stageOf(run, local)
        FirmwareUpdateUi(
            scoutName = state.scout?.name.orEmpty(),
            modelName = state.scout?.modelDisplayName.orEmpty(),
            installedVersion = details.firmwareVersion.ifEmpty { state.scout?.firmwareVersion.orEmpty() },
            stage = stage,
            blocker = blocker(state, details, recording, environment),
            consents = (stage as? FirmwareStage.Offer)?.let { offerConsents(it.offer) } ?: emptyList(),
            accepted = accepted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FirmwareUpdateUi())

    init {
        if (!updater.isInstalling) {
            // Check as soon as the Scout's Info is known (it may come from the link this screen asks for)
            viewModelScope.launch {
                manager.linkDetails.map { it.info }.filterNotNull().first()
                if (localStage.value == FirmwareStage.Checking) check()
            }
        }
    }

    /** The screen's service binding, for the recording lock. */
    fun onServiceChanged(newService: NetworkSurveyService?) {
        service.value = newService
    }

    /** The screen is in front: keep the Scout connected, re-read Bluetooth and the permission, and start a waiting install. */
    fun onResume() {
        resumed = true
        manager.requestLink(linkOwner)
        refreshEnvironment()
        waitingPackage?.let {
            waitingPackage = null
            startInstall(it)
        }
    }

    fun onPause() {
        resumed = false
        manager.release(linkOwner)
    }

    /** Re-reads Bluetooth and the permission, after a fix. */
    fun refreshEnvironment() {
        val context = getApplication<Application>()
        environment.value = Environment(ScoutBluetooth.hasConnectPermission(context), ScoutBluetooth.isEnabled(context))
        manager.refreshEnvironment()
    }

    /** Fetches and checks the release for the Scout. */
    fun check() {
        val info = manager.linkDetails.value.info ?: return
        val model = uiState.value.modelName
        accepted.value = emptySet()
        localStage.value = FirmwareStage.Checking
        work = viewModelScope.launch {
            localStage.value = try {
                withContext(ioDispatcher) {
                    val (release, signed) = catalog.fetchLatest()
                    FirmwareReleaseCache.create(getApplication()).store(signed)
                    FirmwareStage.Offer(catalog.offer(release, info, model))
                }
            } catch (e: FirmwareUpdateError) {
                FirmwareStage.Failed(e, resumable = false)
            }
        }
    }

    fun setAccepted(consent: FirmwareConsent, on: Boolean) {
        accepted.value = if (on) accepted.value + consent else accepted.value - consent
    }

    /** Update on an offer: download, verify, and install once every consent was given. */
    fun update() {
        val offer = (localStage.value as? FirmwareStage.Offer)?.offer ?: return
        val state = uiState.value
        val info = manager.linkDetails.value.info
        if (state.blocker != null || !state.allAccepted || info == null || work?.isActive == true) return
        localStage.value = FirmwareStage.Downloading(offer)
        work = viewModelScope.launch {
            try {
                // The SHA-256 and signature checks read the whole image: keep them off the main thread
                val pkg = withContext(ioDispatcher) { catalog.download(offer, info) }
                if (!accepted.value.containsAll(pkg.requiredConsents(info))) {
                    // The Scout's firmware changed since the offer: offer again, with what is needed now
                    check()
                } else if (resumed) {
                    startInstall(pkg)
                } else {
                    waitingPackage = pkg
                }
            } catch (e: FirmwareUpdateError) {
                localStage.value = FirmwareStage.Failed(e, resumable = false)
            }
        }
    }

    /** Stops the download, or the install while it can still be stopped. */
    fun cancel() {
        val downloading = localStage.value as? FirmwareStage.Downloading
        if (downloading != null && !updater.isInstalling) {
            work?.cancel()
            waitingPackage = null
            localStage.value = FirmwareStage.Offer(downloading.offer)
        } else {
            updater.cancel()
        }
    }

    /** Resume after an interruption: the same package, and the board continues where it stopped. */
    fun resume() {
        val failed = updater.state.value as? FirmwareUpdateRun.Failed ?: return
        if (failed.error.isResumable && uiState.value.blocker == null) startInstall(failed.pkg)
    }

    fun startOver() {
        updater.clear()
        check()
    }

    /** Done: forget the result so the next visit starts fresh. */
    fun done() = updater.clear()

    private fun startInstall(pkg: FirmwarePackage) {
        if (isRecordingNow()) {
            localStage.value = FirmwareStage.Failed(FirmwareUpdateError.SurveyRunning(), resumable = false)
            return
        }
        if (updater.install(pkg)) FirmwareUpdateService.start(getApplication())
    }

    private fun isRecordingNow(): Boolean = service.value?.surveyStateFlow?.value?.isRecording == true

    private fun stageOf(run: FirmwareUpdateRun, local: FirmwareStage): FirmwareStage = when (run) {
        is FirmwareUpdateRun.Installing -> FirmwareStage.Installing(run.pkg, run.phase, run.cancelable)
        is FirmwareUpdateRun.Finished -> FirmwareStage.Finished(run.result)
        is FirmwareUpdateRun.Failed -> FirmwareStage.Failed(run.error, run.error.isResumable)
        FirmwareUpdateRun.Idle -> local
    }

    private fun blocker(state: ScoutState, details: ScoutLinkDetails, recording: Boolean, environment: Environment): FirmwareBlocker? =
        firmwareBlocker(state, hasInfo = details.info != null, recording, environment.permission, environment.bluetooth)

    override fun onCleared() = onPause()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * What stops an update from starting now, in the screen's order. The Scout state follows the radio
 * and the permission live ([permission] and [bluetooth] are re-read only on resume), and the link
 * counts as up only while the state is Connected or Catching up, never from link details, which
 * outlive a canceled or dropped link.
 *
 * @param hasInfo True once the Scout's Info was read on this link.
 */
internal fun firmwareBlocker(state: ScoutState, hasInfo: Boolean, recording: Boolean, permission: Boolean, bluetooth: Boolean): FirmwareBlocker? =
    when {
        recording -> FirmwareBlocker.RECORDING
        !permission || state is ScoutState.PermissionMissing -> FirmwareBlocker.PERMISSION
        !bluetooth || state is ScoutState.BluetoothOff -> FirmwareBlocker.BLUETOOTH_OFF
        state is ScoutState.PairingLost || state is ScoutState.PairingRefused -> FirmwareBlocker.PAIRING_LOST
        !(state is ScoutState.Connected || state is ScoutState.CatchingUp) || !hasInfo -> FirmwareBlocker.WAITING
        else -> null
    }
