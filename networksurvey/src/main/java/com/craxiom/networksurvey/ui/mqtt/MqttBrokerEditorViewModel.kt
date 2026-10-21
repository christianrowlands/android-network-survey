package com.craxiom.networksurvey.ui.mqtt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings
import com.craxiom.networksurvey.mqtt.OrganizationBroker
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfile
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
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
import kotlinx.coroutines.withContext

/**
 * Adds a broker or edits a saved one. The form is validated here; saving and removing write the
 * broker profile store off the main thread, and the page closes with a message for the list.
 */
class MqttBrokerEditorViewModel @JvmOverloads constructor(
    application: Application,
    private val store: MqttBrokerProfileStore = MqttBrokerProfileStore.create(application),
    private val link: DestinationSurveyLink = DestinationSurveyLink(application, Destination.MQTT),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val restrictions = AndroidRestrictionsSource(application)
    private val runtime = MutableStateFlow<SurveyRuntimeState?>(null)
    private var runtimeJob: Job? = null
    private var loadedId: String? = null
    private var existing: MqttBrokerProfile? = null

    private val _form = MutableStateFlow(MqttBrokerForm())
    val form: StateFlow<MqttBrokerForm> = _form.asStateFlow()

    private val _done = MutableSharedFlow<EditorResult>(extraBufferCapacity = 1)

    /** Emits once when the page should close, with what to tell the list. */
    val done: SharedFlow<EditorResult> = _done.asSharedFlow()

    /** Loads [profileId] into the form, or starts a new broker when it is null. Runs once. */
    fun load(profileId: String?) {
        if (loadedId == (profileId ?: NEW)) return
        loadedId = profileId ?: NEW
        viewModelScope.launch(ioDispatcher) {
            val profiles = store.profiles()
            val profile = profileId?.let { id -> profiles.firstOrNull { it.id == id } }
            existing = profile
            val form = if (profile == null) {
                MqttBrokerForm(isNew = true, isLast = profiles.isEmpty())
            } else {
                MqttBrokerForm(
                    isNew = false,
                    name = profile.name,
                    host = profile.host,
                    port = profile.port.toString(),
                    tlsEnabled = profile.tlsEnabled,
                    username = store.login(profile.id).username,
                    topicPrefix = profile.topicPrefix,
                    qos = profile.qos,
                    isActive = store.activeId() == profile.id,
                    isLast = profiles.size == 1,
                    canShare = !(profile.origin == MqttBrokerProfile.Origin.ORGANIZATION_COPY &&
                        OrganizationBroker.setsPassword(restrictions.snapshot())),
                    moreOptionsExpanded = profile.topicPrefix.isNotEmpty() || profile.qos != MqttConstants.DEFAULT_MQTT_QOS.value,
                )
            }
            _form.value = form.withRuntime(runtime.value)
        }
    }

    /** Follows whether MQTT is streaming, for the reconnect note and the remove warning. */
    fun setService(service: NetworkSurveyService?) {
        runtimeJob?.cancel()
        runtimeJob = service?.let {
            viewModelScope.launch {
                it.surveyStateFlow.collect { state ->
                    runtime.value = state
                    _form.update { form -> form.withRuntime(state) }
                }
            }
        }
        if (service == null) runtime.value = null
    }

    /** Applies an edit to the form, clearing the errors it may fix. */
    fun edit(change: (MqttBrokerForm) -> MqttBrokerForm) = _form.update { change(it).copy(hostError = false, portError = false, topicPrefixError = false) }

    /** Validates and saves. A new broker is selected; the first one also turns MQTT on when idle. */
    fun save() {
        val form = _form.value
        val host = form.host.trim()
        val port = form.port.trim().toIntOrNull()?.takeIf { MqttBrokerProfile.isValidPort(it) }
        val topicPrefix = form.topicPrefix.trim()
        val prefixValid = MqttBrokerProfile.isValidTopicPrefix(topicPrefix)
        if (host.isEmpty() || port == null || !prefixValid) {
            _form.update {
                it.copy(
                    hostError = host.isEmpty(),
                    portError = port == null,
                    topicPrefixError = !prefixValid,
                    // The prefix lives under More options, so open it to show the error
                    moreOptionsExpanded = it.moreOptionsExpanded || !prefixValid,
                )
            }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val firstBroker = !store.hasUserProfiles()
            val profile = (existing ?: MqttBrokerProfile(id = MqttBrokerProfileStore.newId(), name = host, host = host)).copy(
                name = form.name.trim().ifEmpty { host },
                host = host,
                port = port,
                tlsEnabled = form.tlsEnabled,
                topicPrefix = topicPrefix,
                qos = form.qos,
            )
            val password = if (form.isNew || form.password.isNotEmpty()) form.password else null
            val saved = store.save(profile, form.username.trim(), password, select = form.isNew)
            val outcome = when {
                firstBroker -> SaveOutcome.of(link.onFirstSetup(runtime.value?.isRecording == true))
                form.isActive && form.streaming -> SaveOutcome.RECONNECTING
                else -> SaveOutcome.NONE
            }
            _done.emit(EditorResult(MqttMessage.Saved(saved.name, outcome)))
        }
    }

    /** Removes the broker being edited, and its saved password. */
    fun remove() {
        val profile = existing ?: return
        viewModelScope.launch(ioDispatcher) {
            store.remove(profile.id)
            _done.emit(EditorResult(MqttMessage.Removed(profile.name)))
        }
    }

    /**
     * @return The QR code payload for the broker being edited (without this phone's client ID,
     * which must stay unique per phone), or null for a new broker.
     */
    suspend fun shareSettings(): MqttConnectionSettings? = withContext(ioDispatcher) { buildShareSettings() }

    private fun buildShareSettings(): MqttConnectionSettings? {
        val profile = existing ?: return null
        val login = store.login(profile.id)
        val streamTypes = SurveyTypeResolver.typesForCurrentSetup(getApplication(), Destination.MQTT)
        // Older app versions still read the record type flags from a shared QR code, so they are
        // filled in from the survey's record types. This app ignores them when importing.
        return MqttConnectionSettings.Builder()
            .host(profile.host)
            .port(profile.port)
            .tlsEnabled(profile.tlsEnabled)
            .mqttUsername(login.username)
            .mqttPassword(login.password)
            .mqttTopicPrefix(profile.topicPrefix)
            .mqttQos(profile.qos)
            .cellularStreamEnabled(RecordType.CELLULAR in streamTypes)
            .wifiStreamEnabled(RecordType.WIFI in streamTypes)
            .bluetoothStreamEnabled(RecordType.BLUETOOTH in streamTypes)
            .gnssStreamEnabled(RecordType.GNSS in streamTypes)
            .phoneStateStreamEnabled(RecordType.PHONE_STATE in streamTypes)
            .deviceStatusStreamEnabled(store.isDeviceStatusEnabled())
            .build()
    }

    private fun MqttBrokerForm.withRuntime(state: SurveyRuntimeState?) = copy(
        streaming = link.inSurvey(state),
        onlyDestination = state?.destinations?.keys == setOf(Destination.MQTT),
    )

    /** What closing the editor tells the broker list. */
    data class EditorResult(val message: MqttMessage)

    private companion object {
        const val NEW = "new"
    }
}

/** The broker editor's form. Port is text so the field can be empty while typing. */
data class MqttBrokerForm(
    val isNew: Boolean = true,
    val name: String = "",
    val host: String = "",
    val port: String = MqttConstants.DEFAULT_MQTT_PORT.toString(),
    val tlsEnabled: Boolean = MqttConstants.DEFAULT_MQTT_TLS_SETTING,
    val username: String = "",
    val password: String = "",
    val topicPrefix: String = MqttConstants.DEFAULT_MQTT_TOPIC_PREFIX,
    val qos: Int = MqttConstants.DEFAULT_MQTT_QOS.value,
    val moreOptionsExpanded: Boolean = false,
    val isActive: Boolean = false,
    val isLast: Boolean = false,
    val streaming: Boolean = false,
    /** True when MQTT is the running survey's only destination, so removing its broker ends the survey. */
    val onlyDestination: Boolean = false,
    val canShare: Boolean = false,
    val hostError: Boolean = false,
    val portError: Boolean = false,
    val topicPrefixError: Boolean = false,
)
