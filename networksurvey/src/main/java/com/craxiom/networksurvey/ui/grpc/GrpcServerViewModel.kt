package com.craxiom.networksurvey.ui.grpc

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings
import com.craxiom.networksurvey.services.GrpcConnectionService
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.survey.DestinationSurveyLink
import com.craxiom.networksurvey.ui.survey.FirstSetupOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The gRPC page: loads and validates the server settings, saves them, and follows how the saved
 * server is doing in a running survey. Saving the first server also turns gRPC on for the next
 * survey, the way the prototype's "Save and turn on" does. The survey service reacts to the saved
 * settings itself: it moves a running stream to a new server without ending the survey.
 */
class GrpcServerViewModel @JvmOverloads constructor(
    application: Application,
    private val link: DestinationSurveyLink = DestinationSurveyLink(application, Destination.GRPC),
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application),
    private val lastFailure: () -> GrpcConnectionService.ConnectionFailure? = GrpcConnectionService::getLastFailure,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val runtime = MutableStateFlow<SurveyRuntimeState?>(null)
    private var runtimeJob: Job? = null

    private val _form = MutableStateFlow(GrpcServerForm())
    val form: StateFlow<GrpcServerForm> = _form.asStateFlow()

    private val _saved = MutableSharedFlow<GrpcSaved>(extraBufferCapacity = 1)

    /** Emits once per save, with what the save did. */
    val saved: SharedFlow<GrpcSaved> = _saved.asSharedFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            val settings = GrpcConnectionSettings.fromPreferences(preferences)
            _form.update {
                it.copy(
                    host = settings?.host.orEmpty(),
                    port = (settings?.port ?: it.port.toIntOrNull())?.toString().orEmpty(),
                    deviceName = settings?.deviceName.orEmpty(),
                )
            }
            refreshStatus()
        }
    }

    /** Follows the running survey, for the live status and to know whether a survey is recording. */
    fun setService(service: NetworkSurveyService?) {
        runtimeJob?.cancel()
        runtimeJob = service?.let {
            viewModelScope.launch { it.surveyStateFlow.collect(::onRuntime) }
        }
        if (service == null) onRuntime(null)
    }

    /** Takes the latest survey state; the service binding calls it, and so do the tests. */
    internal fun onRuntime(state: SurveyRuntimeState?) {
        runtime.value = state
        refresh()
    }

    /** Re-reads the setup, the policy and the connection, for example when the page comes back. */
    fun refresh() {
        viewModelScope.launch(ioDispatcher) { refreshStatus() }
    }

    /** Applies an edit to the fields, clearing the errors it may fix. */
    fun edit(change: (GrpcServerForm) -> GrpcServerForm) = _form.update { change(it).copy(hostError = false, portError = false) }

    /** Validates and saves. The first server saved also turns gRPC on for the next survey. */
    fun save() {
        val validation = _form.value.validate()
        val settings = validation.settings
        if (settings == null) {
            _form.update {
                it.copy(hostError = validation.hostError, portError = validation.portError, errorEpoch = it.errorEpoch + 1)
            }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val before = GrpcConnectionSettings.fromPreferences(preferences)
            val streaming = link.inSurvey(runtime.value)
            GrpcConnectionSettings.save(preferences, settings)
            val result = when {
                before == null -> GrpcSaveResult.of(link.onFirstSetup(runtime.value?.isRecording == true))
                streaming && (before.host != settings.host || before.port != settings.port) -> GrpcSaveResult.RECONNECTING
                else -> GrpcSaveResult.SAVED
            }
            _form.update { it.copy(host = settings.host, port = settings.port.toString(), deviceName = settings.deviceName) }
            refreshStatus()
            _saved.emit(GrpcSaved(result, firstSetup = before == null))
        }
    }

    /** Turns on "Override organization settings", which lets the user turn gRPC on. */
    fun useOwnSettings() {
        viewModelScope.launch(ioDispatcher) {
            OrganizationOverride.set(preferences, true)
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val saved = GrpcConnectionSettings.fromPreferences(preferences)
        val state = runtime.value
        val inSetup = link.isInSetup()
        val status = grpcStatus(saved, inSetup, link.inSurvey(state), lastFailure())
        _form.update {
            it.copy(
                loading = false,
                configured = saved != null,
                inSetup = inSetup,
                recording = state?.isRecording == true,
                organizationOff = link.isTurnedOffByOrganization(),
                status = status,
            )
        }
    }
}

/** What saving the gRPC server did, so the page can say what happens next. */
enum class GrpcSaveResult {
    /** Saved; nothing else changed. */
    SAVED,

    /** The first server: gRPC was turned on for the next survey. */
    TURNED_ON,

    /** The first server, saved while recording: gRPC was not added to the running survey. */
    TURN_ON_TO_STREAM,

    /** The first server, and the running survey already sends to gRPC: the stream starts now. */
    STARTING,

    /** The address changed while streaming, so the stream moves to the new server. */
    RECONNECTING;

    companion object {
        /** @return The result for what setting up the first server did to the survey. */
        fun of(firstSetup: FirstSetupOutcome): GrpcSaveResult = when (firstSetup) {
            FirstSetupOutcome.NONE -> SAVED
            FirstSetupOutcome.TURNED_ON -> TURNED_ON
            FirstSetupOutcome.TURN_ON_TO_STREAM -> TURN_ON_TO_STREAM
            FirstSetupOutcome.STARTING -> STARTING
        }
    }
}

/**
 * One save of the gRPC page.
 *
 * @property firstSetup True when no server was saved before; the page then returns to the Survey
 *                      screen, otherwise it stays so the user can watch the stream reconnect.
 */
data class GrpcSaved(val result: GrpcSaveResult, val firstSetup: Boolean)
