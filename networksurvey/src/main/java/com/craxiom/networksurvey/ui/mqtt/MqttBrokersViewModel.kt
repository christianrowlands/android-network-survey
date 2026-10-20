package com.craxiom.networksurvey.ui.mqtt

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.mqtt.OrganizationBroker
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.survey.DestinationSurveyLink
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
 * The MQTT page: lists the saved brokers, selects one, adds one from a QR code, and edits this
 * phone's client ID and device status setting. Reads the survey's live state from the bound
 * service so the selected broker can show how it is doing.
 */
class MqttBrokersViewModel @JvmOverloads constructor(
    application: Application,
    private val store: MqttBrokerProfileStore = MqttBrokerProfileStore.create(application),
    private val link: DestinationSurveyLink = DestinationSurveyLink(application, Destination.MQTT),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val restrictions = AndroidRestrictionsSource(application)
    private val runtime = MutableStateFlow<SurveyRuntimeState?>(null)
    private var runtimeJob: Job? = null

    private val _uiState = MutableStateFlow(MqttBrokersUiState())
    val uiState: StateFlow<MqttBrokersUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 1)
    val messages: SharedFlow<MqttMessage> = _messages.asSharedFlow()

    // Held as a field: SharedPreferences keeps its listeners weakly.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in WATCHED_KEYS) refresh()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        refresh()
    }

    /** Follows the survey's live state while the service is bound; null when it unbinds. */
    fun setService(service: NetworkSurveyService?) {
        runtimeJob?.cancel()
        runtimeJob = null
        if (service == null) {
            runtime.value = null
            _uiState.update { it.copy(liveStatus = null) }
            return
        }
        runtimeJob = viewModelScope.launch {
            service.surveyStateFlow.collect { state ->
                runtime.value = state
                _uiState.update { it.copy(liveStatus = link.liveStatus(state)) }
            }
        }
    }

    /** Selects the broker [id]. While streaming, the message offers Undo. */
    fun select(id: String) {
        val previous = _uiState.value.activeId
        if (previous == id) return
        val name = _uiState.value.brokers.firstOrNull { it.id == id }?.name ?: return
        val streaming = _uiState.value.liveStatus != null
        viewModelScope.launch(ioDispatcher) {
            store.setActive(id)
            _messages.emit(if (streaming) MqttMessage.StreamingTo(name, previous) else MqttMessage.Using(name))
        }
    }

    /** Selects [previousId] again after a switch the user undid. */
    fun undoSelect(previousId: String?) {
        viewModelScope.launch(ioDispatcher) { store.setActive(previousId) }
    }

    /** Sets this phone's client ID. A blank value is ignored: every broker needs one. */
    fun setClientId(clientId: String) {
        val trimmed = clientId.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            preferences.edit(commit = true) { putString(MqttConstants.PROPERTY_MQTT_CLIENT_ID, trimmed) }
        }
    }

    /** Turns streaming this phone's device status on or off. */
    fun setDeviceStatusEnabled(enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            preferences.edit(commit = true) {
                putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, enabled)
            }
        }
    }

    /** Adds (or updates) the broker in a scanned QR code and selects it. */
    fun importFromQr(settings: MqttConnectionSettings) {
        viewModelScope.launch(ioDispatcher) {
            val wasEmpty = !store.hasUserProfiles()
            val imported = store.addFromQr(settings)
            if (imported == null) {
                _messages.emit(MqttMessage.NotABrokerCode)
                return@launch
            }
            val outcome = if (wasEmpty) SaveOutcome.of(link.onFirstSetup(isRecording())) else SaveOutcome.NONE
            _messages.emit(MqttMessage.QrImported(imported.profile.name, imported.updatedExisting, outcome))
        }
    }

    /** Turns on "Override organization settings"; the service copies the organization's broker. */
    fun useOwnSettings() {
        viewModelScope.launch(ioDispatcher) {
            OrganizationOverride.set(preferences, true)
            _messages.emit(MqttMessage.OwnSettingsInUse)
        }
    }

    /** Shows a message another page sent back (the editor, after a save or a remove). */
    fun show(message: MqttMessage) {
        _messages.tryEmit(message)
    }

    private fun isRecording(): Boolean = runtime.value?.isRecording == true

    private fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            val snapshot = restrictions.snapshot()
            val overrideOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
            val organization = if (overrideOn) null else OrganizationBroker.from(snapshot)
            val profiles = store.profiles()
            val rows = profiles.map {
                BrokerRowUi(it.id, it.name, it.host, it.port, it.tlsEnabled, store.login(it.id).username)
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    brokers = rows,
                    activeId = store.activeId(),
                    organizationBroker = organization?.let { info ->
                        OrganizationBrokerUi(info.mqttBrokerHost, info.portNumber, info.isTlsEnabled)
                    },
                    hasOwnBrokers = rows.isNotEmpty(),
                    clientId = store.clientId(),
                    deviceStatusEnabled = store.isDeviceStatusEnabled(),
                )
            }
        }
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private companion object {
        val WATCHED_KEYS = setOf(
            NetworkSurveyConstants.PROPERTY_MQTT_BROKER_PROFILES,
            NetworkSurveyConstants.PROPERTY_MQTT_ACTIVE_BROKER_PROFILE,
            NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED,
            NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY,
            MqttConstants.PROPERTY_MQTT_CLIENT_ID,
        )
    }
}
