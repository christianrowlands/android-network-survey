package com.craxiom.networksurvey.ui.survey

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.ui.community.CommunityUploadLauncher
import com.craxiom.networksurvey.ui.community.CommunityUploadRepository
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import com.craxiom.networksurvey.ui.survey.model.SurveyEvent
import com.craxiom.networksurvey.ui.survey.model.SurveyInputs
import com.craxiom.networksurvey.ui.survey.model.SurveyUiState
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.ui.wifi.model.AndroidWifiEnvironment
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Survey screen's view model. It renders what the service is actually running (so a survey
 * started at boot or by another app shows as Recording with its real setup) and sends every start,
 * change and stop through the service's survey coordinator.
 *
 * While Ready, tapping a tile or a row edits the user's stored working setup; nothing runs until
 * Start. While Recording, edits go through the Change sheet and apply immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyViewModel @JvmOverloads constructor(
    application: Application,
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application),
    private val store: SurveySetupStore = SurveySetupStore(preferences),
    private val infoReader: DestinationInfoReader = DestinationInfoReader(application),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : AndroidViewModel(application) {

    private val gateway = MutableStateFlow<SurveyServiceGateway?>(null)
    private val locationTracker = SurveyLocationTracker(application)
    private val wifiEnvironment = AndroidWifiEnvironment(application)
    private val commands = SurveyCommandRunner(viewModelScope, ioDispatcher, ::emit)
    private val organizationWatcher = OrganizationChangeWatcher()

    private val storeVersion = MutableStateFlow(0)
    private val destinationInfo = MutableStateFlow(DestinationInfo())
    private val tick = MutableStateFlow(clock())

    private val mutableEvents = MutableSharedFlow<SurveyEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** One-shot snackbar messages. */
    val events: SharedFlow<SurveyEvent> = mutableEvents.asSharedFlow()

    /**
     * The service's survey state, null until the first bind. The screen unbinds whenever it leaves
     * (another tab, the background), and that must not read as "stopped": an unbind keeps the last
     * state, so coming back draws Recording at once instead of flashing Ready until the rebind.
     */
    private val runtime: StateFlow<SurveyRuntimeState?> = gateway
        .flatMapLatest { it?.state ?: emptyFlow() }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        storeVersion.value++
        refreshDestinationInfo()
    }

    private var tickerJob: Job? = null

    @Volatile
    private var lastInputs: SurveyInputs? = null

    /** Set when the user leaves to fix a skipped item, so returning adds it back to the survey. */
    private var retryOnResume = false

    /**
     * Everything the screen draws, built only while the service is bound. The screen unbinds
     * whenever it leaves (another tab, the background), and coming back must draw the state it
     * left, not one rebuilt without the service (no record counts, location "Finding").
     */
    val uiState: StateFlow<SurveyUiState> = gateway
        .flatMapLatest { bound -> if (bound == null) emptyFlow() else liveState(bound) }
        .combine(commands.inFlight) { state, inFlight -> state.copy(commandInFlight = inFlight) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SurveyUiState.UNKNOWN)

    /** True while a start, change or stop is on its way to the service. */
    internal val commandInFlight: StateFlow<Boolean> get() = commands.inFlight

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshDestinationInfo()
        viewModelScope.launch {
            runtime.filterNotNull().collect { state ->
                organizationWatcher.onState(state, clock())?.let(::emit)
                if (state.isRecording) startTicker() else stopTicker()
            }
        }
    }

    /** Called by the screen's service binding; null when the screen unbinds. */
    fun onServiceChanged(newGateway: SurveyServiceGateway?) {
        gateway.value = newGateway
        locationTracker.attach(newGateway)
        refreshDestinationInfo()
        if (newGateway != null) commands.onConnected(newGateway) else commands.onDisconnected()
        // Nobody sees the timer while unbound; the same state coming back on the rebind is not a
        // new emission, so the ticker restarts here rather than from the runtime collector
        if (newGateway == null) stopTicker() else if (runtime.value?.isRecording == true) startTicker()
    }

    /** Called when the screen resumes, since permissions and settings may have changed meanwhile. */
    fun onResume() {
        locationTracker.refresh()
        refreshDestinationInfo()
        if (retryOnResume) {
            retryOnResume = false
            retrySkipped()
        }
    }

    /** The user went to fix a skipped item (a radio, a permission); retry it when they come back. */
    fun retryAfterFix() {
        retryOnResume = true
    }

    /** The screen as it would look with a different working setup, for the Change sheet. */
    fun preview(types: Set<RecordType>, destinations: Set<Destination>): SurveyUiState =
        lastInputs?.let { SurveyUiStateMapper.map(it.copy(userSetup = SurveySetup.custom(types, destinations))) }
            ?: SurveyUiState()

    /** "Use my own settings" (on) or Restore (off); the service reacts to the preference. */
    fun setOverride(on: Boolean) {
        organizationWatcher.disarm()
        viewModelScope.launch(ioDispatcher) {
            OrganizationOverride.set(preferences, on)
        }
    }

    /**
     * Uploads what waits for [destination] now. NS Analytics waits for the network when offline;
     * the community databases upload only while online.
     */
    fun uploadNow(destination: Destination) {
        viewModelScope.launch(ioDispatcher) {
            val context = getApplication<Application>()
            when (destination) {
                Destination.NS_ANALYTICS -> when (NsAnalyticsUploadWorker.triggerImmediateUpload(context, waitForNetwork = true)) {
                    is NsAnalyticsUploadWorker.Companion.TriggerResult.WaitingForNetwork -> emit(SurveyEvent.UploadWaitsForNetwork)
                    NsAnalyticsUploadWorker.Companion.TriggerResult.MdmBlocked ->
                        emit(SurveyEvent.UploadBlockedByOrganization(Destination.NS_ANALYTICS))

                    else -> Unit
                }

                Destination.COMMUNITY -> emitCommunityProblem(CommunityUploadLauncher.startWithSavedSettings(context))

                else -> Unit
            }
        }
    }

    /** Uploads to the community databases with the choices made in the upload dialog. */
    fun uploadCommunity(uploadToOpenCellId: Boolean, anonymously: Boolean, uploadToBeaconDb: Boolean, retry: Boolean, dontShowAgain: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val context = getApplication<Application>()
            if (dontShowAgain) CommunityUploadLauncher.setSkipConfirmation(context, true)
            emitCommunityProblem(CommunityUploadLauncher.startWithChoices(context, uploadToOpenCellId, anonymously, uploadToBeaconDb, retry))
        }
    }

    /** Says why a community upload didn't start, if it didn't. */
    private fun emitCommunityProblem(result: CommunityUploadLauncher.StartResult) = when (result) {
        CommunityUploadLauncher.StartResult.NO_NETWORK -> emit(SurveyEvent.UploadNeedsNetwork)
        CommunityUploadLauncher.StartResult.BLOCKED_BY_ORGANIZATION -> emit(SurveyEvent.UploadBlockedByOrganization(Destination.COMMUNITY))
        CommunityUploadLauncher.StartResult.STARTED -> Unit
    }

    /** Turns off the MQTT queue limit, as the old queue card's "Disable limit" did. */
    fun disableQueueLimit() {
        viewModelScope.launch(ioDispatcher) {
            preferences.edit {
                putString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, NetworkSurveyConstants.STREAMING_QUEUE_LIMIT_DISABLED.toString())
            }
        }
    }

    /** Starts the working setup; [afterwards] runs on the main thread once the start is done. */
    fun start(afterwards: () -> Unit = {}) = commands.run(afterwards) { gateway ->
        organizationWatcher.disarm()
        val result = gateway.startWorkingSetup().get()
        SurveyCommandRunner.Outcome.Start(result, gateway.activeMissionId())
    }

    fun stop() = commands.run { gateway ->
        val before = gateway.counts().values.sum()
        gateway.stop().get()
        SurveyCommandRunner.Outcome.Stop(gateway.state.value.lastSession?.totalRecords ?: before)
    }

    /** Ready only: turns [type] on or off in the user's working setup. */
    fun toggleRecordType(type: RecordType) = editWorkingSetup { setup ->
        val types = if (type in setup.recordTypes) setup.recordTypes - type else setup.recordTypes + type
        SurveySetup.custom(types, setup.destinations)
    }

    /** Ready only: turns [destination] on or off in the user's working setup. */
    fun toggleDestination(destination: Destination) = editWorkingSetup { setup ->
        val destinations = if (destination in setup.destinations) {
            setup.destinations - destination
        } else {
            setup.destinations + destination
        }
        SurveySetup.custom(setup.recordTypes, destinations)
    }

    /** The "Use Log files" fix: Log files on, NS Analytics off. */
    fun useLogFiles() = editWorkingSetup { setup ->
        SurveySetup.custom(setup.recordTypes, setup.destinations - Destination.NS_ANALYTICS + Destination.LOG_FILES)
    }

    /**
     * Recording only: stores the Change sheet's draft and applies it to the running survey. Both
     * happen in the one command, so the service always applies this draft and never the one before
     * it (a separate store write could land after the apply had read the store).
     */
    fun applyDraft(types: Set<RecordType>, destinations: Set<Destination>) {
        organizationWatcher.disarm()
        commands.run { gateway ->
            store.setCurrentSetup(SurveySetup.custom(types, destinations))
            gateway.applyWorkingSetup().get()
            SurveyCommandRunner.Outcome.Applied
        }
    }

    /** After a fix (Wi-Fi turned on, permission granted), adds the skipped items back. */
    fun retrySkipped() = commands.run { gateway ->
        // What the running survey asked for, not the working setup, so fixing an item never swaps
        // a survey another app or the organization started for the user's own
        gateway.retrySkipped().get()
        SurveyCommandRunner.Outcome.Silent
    }

    /** Ready only: makes the saved setup with [id] the working setup. */
    fun pickSetup(id: String) {
        organizationWatcher.disarm()
        viewModelScope.launch(ioDispatcher) {
            store.getSavedSetups().firstOrNull { it.id == id }?.let(store::setCurrentSetup)
        }
    }

    /** "Save current": saves the working setup under [name], replacing a setup of the same name. */
    fun saveSetup(name: String) {
        viewModelScope.launch(ioDispatcher) {
            val saved = store.save(store.getCurrentSetup(), name)
            emit(SurveyEvent.SetupSaved(saved.name))
        }
    }

    fun renameSetup(id: String, name: String) {
        viewModelScope.launch(ioDispatcher) { store.rename(id, name) }
    }

    fun starSetup(id: String, starred: Boolean) {
        viewModelScope.launch(ioDispatcher) { store.setStarred(id, starred) }
    }

    fun deleteSetup(id: String) {
        viewModelScope.launch(ioDispatcher) { store.delete(id) }
    }

    fun setDestinationShown(destination: Destination, shown: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            store.hiddenDestinations =
                if (shown) store.hiddenDestinations - destination else store.hiddenDestinations + destination
        }
    }

    fun emit(event: SurveyEvent) {
        mutableEvents.tryEmit(event)
    }

    /**
     * Ready only: edits the stored working setup on its own coroutine. Nothing starts from here; a
     * command that must see the edit writes the store itself (see [applyDraft]).
     */
    private fun editWorkingSetup(change: (SurveySetup) -> SurveySetup) {
        organizationWatcher.disarm()
        viewModelScope.launch(ioDispatcher) { store.setCurrentSetup(change(store.getCurrentSetup())) }
    }

    /** The screen state from [service]'s survey and everything else the screen shows. */
    private fun liveState(service: SurveyServiceGateway): Flow<SurveyUiState> = combine(
        runtime.filterNotNull(), storeVersion, destinationInfo, locationTracker.status, tick,
    ) { runtime, _, info, location, now -> buildInputs(service, runtime, info, location, now) }
        .combine(CommunityUploadRepository.get(getApplication()).progress) { inputs, upload -> inputs.copy(communityUpload = upload) }
        // No empty placeholder first: it would briefly say "Nothing waiting" while records wait
        .combine(DeliveryQueueReader(getApplication()).queues()) { inputs, queues ->
            // Ready has no ticker, so the queue refresh also brings "next upload in" up to date
            val withUpload = inputs.copy(queues = queues, nowMs = maxOf(inputs.nowMs, clock()))
            lastInputs = withUpload
            SurveyUiStateMapper.map(withUpload)
        }

    private fun buildInputs(
        service: SurveyServiceGateway,
        runtime: SurveyRuntimeState,
        info: DestinationInfo,
        location: LocationStatus,
        now: Long,
    ): SurveyInputs {
        return SurveyInputs(
            runtime = runtime,
            userSetup = store.getCurrentSetup(),
            autoIncludePhoneState = SurveyTypeResolver.autoIncludePhoneState(getApplication()),
            destinationInfo = info,
            location = location,
            counts = service.counts(),
            lastRecordTimeMs = service.lastRecordTimeMs(),
            communityCounts = service.communityCounts(),
            communityLastRecordTimeMs = service.communityLastRecordTimeMs(),
            wifiOff = wifiEnvironment.scanState() == WifiScanState.WIFI_OFF,
            nowMs = now,
            hiddenDestinations = store.hiddenDestinations,
            userAutoStart = store.userAutoStart,
            savedSetups = store.orderedSavedSetups(),
            selectedSetupId = store.getSelectedSetupId(),
        )
    }

    private fun refreshDestinationInfo() {
        viewModelScope.launch(ioDispatcher) {
            destinationInfo.value = infoReader.read(gateway.value?.isMqttBrokerConfigured())
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                tick.value = clock()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        tick.value = clock()
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        locationTracker.attach(null)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_INTERVAL_MS = 1_000L
        const val EVENT_BUFFER = 8
    }
}
