package com.craxiom.networksurvey.ui.scout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.ScoutPolicy
import com.craxiom.networksurvey.scout.firmware.FirmwareReleaseCache
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateRun
import com.craxiom.networksurvey.scout.firmware.VerifiedRelease
import com.craxiom.networksurvey.scout.link.ScoutBluetooth
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.ScoutPairingState
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.controller.WifiSourceSwitch
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.survey.model.SurveySetup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The NS Scout page's view model. It keeps the Scout connected for status while the page is in
 * front, and sends every Wi-Fi source change through the service, which refuses one while a survey
 * records. Remove is refused here too while recording, since the manager would switch the source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoutPageViewModel(application: Application) : AndroidViewModel(application) {

    // Overridable for tests, which run the reads on the test thread.
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val manager = ScoutManager.getInstance(application)
    private val store = SurveySetupStore(PreferenceManager.getDefaultSharedPreferences(application))
    private val service = MutableStateFlow<NetworkSurveyService?>(null)
    private val runtime = service.flatMapLatest { it?.surveyStateFlow ?: flowOf(null) }
    private val allowed = MutableStateFlow(true)
    private val setups = MutableStateFlow<List<SurveySetup>>(emptyList())
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val scanBlock = MutableStateFlow<ScoutScanBlock?>(null)
    private val mutableEvents = MutableSharedFlow<ScoutPageEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val linkOwner = Any()
    private var tickJob: Job? = null
    private val releaseCache by lazy { FirmwareReleaseCache.create(application) }
    private val release = MutableStateFlow<VerifiedRelease?>(null)
    private val firmware = combine(release, manager.firmwareUpdater.state, ::Pair)

    /** One-shot snackbar messages. */
    val events: SharedFlow<ScoutPageEvent> = mutableEvents.asSharedFlow()

    val uiState: StateFlow<ScoutPageUi> = combine(
        combine(manager.state, manager.linkDetails, manager.wifiSource, ::Triple),
        runtime, combine(allowed, setups, ::Pair), tick, firmware,
    ) { (state, details, source), runtime, (allowed, setups), now, (release, run) ->
        ScoutPageUi(
            allowed = allowed,
            state = state,
            details = details,
            source = source,
            recording = runtime?.isRecording == true,
            stopNeedsConfirmation = runtime != null && runtime.policy.isManaged && !runtime.policy.overrideOn,
            stopRestartsAtBoot = runtime?.policy?.isAutoStartEnabled(store.userAutoStart) == true,
            nowMs = now,
            setupsNeedingScout = ScoutSetups.needingScout(setups),
            firmwareStatus = FirmwareReleaseCache.status(release, details.info),
            firmwareUpdating = run is FirmwareUpdateRun.Installing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScoutPageUi())

    val pairingState: StateFlow<ScoutPairingUi> = combine(
        manager.nearbyScouts, manager.nearbyScanActive, manager.pairing, scanBlock, ::ScoutPairingUi,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScoutPairingUi())

    /** The page's service binding; null when it unbinds. */
    fun onServiceChanged(newService: NetworkSurveyService?) {
        service.value = newService
    }

    /** The page is in front: connect for status, re-read the policy and the saved setups. */
    fun onResume() {
        manager.requestLink(linkOwner)
        refreshScanBlock()
        viewModelScope.launch(ioDispatcher) {
            allowed.value = ScoutPolicy.isAllowed(getApplication())
            setups.value = store.getSavedSetups()
            // "Up to date" or "Update available": the cached release now, a new one at most once a day
            release.value = releaseCache.cached()
            if (manager.pairedScout.value != null) {
                releaseCache.refreshIfStale()
                release.value = releaseCache.cached()
            }
        }
        if (tickJob?.isActive != true) {
            tickJob = viewModelScope.launch {
                while (isActive) {
                    tick.value = System.currentTimeMillis()
                    delay(TICK_INTERVAL_MS)
                }
            }
        }
    }

    /** The page left: nothing here needs the Scout or the pairing scan any more. */
    fun onPause() {
        manager.release(linkOwner)
        manager.stopNearbyScan()
        tickJob?.cancel()
        tickJob = null
    }

    /** "Wi-Fi comes from": refused by the service while a survey records. */
    fun setSource(source: WifiSource) {
        val bound = service.value ?: return
        viewModelScope.launch(ioDispatcher) {
            when (bound.requestWifiSource(source)) {
                WifiSourceSwitch.SWITCHED -> emit(ScoutPageEvent.SourceChanged(source, uiState.value.scoutName))
                WifiSourceSwitch.REJECTED_RECORDING -> emit(ScoutPageEvent.LockedWhileRecording)
                WifiSourceSwitch.UNCHANGED, WifiSourceSwitch.REJECTED_NO_SCOUT -> Unit
            }
        }
    }

    /** Stops the running survey, as the Survey tab's Stop does. */
    fun stopSurvey() {
        val bound = service.value ?: return
        viewModelScope.launch(ioDispatcher) { bound.stopSurvey(SurveyOrigin.USER) }
    }

    /** Remove Scout: refused while a survey records, so a Mission never changes Wi-Fi source. */
    fun remove() {
        val state = uiState.value
        if (isRecordingNow()) return emit(ScoutPageEvent.RemoveLockedWhileRecording)
        manager.removePairedScout()
        emit(ScoutPageEvent.Removed(state.scoutName))
    }

    /** Erase pairings on the Scout, then remove it here. */
    fun erasePairings() {
        val name = uiState.value.scoutName
        if (isRecordingNow()) return emit(ScoutPageEvent.EraseLockedWhileRecording)
        if (!uiState.value.linkReady) return emit(ScoutPageEvent.NeedsLink)
        manager.erasePairingsOnScout { erased ->
            emit(if (erased) ScoutPageEvent.Erased(name) else ScoutPageEvent.EraseFailed(name))
        }
    }

    /** Opens the pairing scan when nothing blocks it. */
    fun startScan() {
        refreshScanBlock()
        manager.clearPairingResult()
        if (scanBlock.value == null) manager.startNearbyScan()
    }

    fun stopScan() = manager.stopNearbyScan()

    /** Pairs with [nearby]; Android asks the user. Replacing the paired Scout is refused while a survey records. */
    fun pair(nearby: NearbyScout) {
        if (replacesWhileRecording(isRecordingNow(), manager.pairedScout.value, nearby)) return emit(ScoutPageEvent.ReplaceLockedWhileRecording)
        manager.pair(nearby)
    }

    /** The sheet showed the pairing result; a success becomes a snackbar. */
    fun onPairingShown(result: ScoutPairingState) {
        if (result is ScoutPairingState.Paired) emit(ScoutPageEvent.Paired(result.scout.name, duringSurvey = isRecordingNow()))
        manager.clearPairingResult()
    }

    /** Re-reads the permission and Bluetooth, after a permission answer or turning Bluetooth on. */
    fun refreshScanBlock() {
        val context = getApplication<Application>()
        scanBlock.value = when {
            !ScoutBluetooth.hasScanPermission(context) || !ScoutBluetooth.hasConnectPermission(context) -> ScoutScanBlock.PERMISSION
            !ScoutBluetooth.isEnabled(context) -> ScoutScanBlock.BLUETOOTH_OFF
            else -> null
        }
        manager.refreshEnvironment()
    }

    private fun isRecordingNow(): Boolean = service.value?.surveyStateFlow?.value?.isRecording == true || uiState.value.recording

    private fun emit(event: ScoutPageEvent) {
        mutableEvents.tryEmit(event)
    }

    override fun onCleared() = onPause()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_INTERVAL_MS = 15_000L
        const val EVENT_BUFFER = 8
    }
}
