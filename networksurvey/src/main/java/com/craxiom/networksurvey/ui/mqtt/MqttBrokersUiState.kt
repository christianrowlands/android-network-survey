package com.craxiom.networksurvey.ui.mqtt

import com.craxiom.networksurvey.ui.survey.FirstSetupOutcome
import com.craxiom.networksurvey.survey.status.DestinationStatus

/**
 * What the MQTT page shows: the saved brokers (or the organization's one), which is selected and
 * how it is doing, and this phone's own MQTT settings.
 *
 * @property organizationBroker The organization's broker while it is in force (managed, not
 *                              overridden). The user's own brokers are then kept but not listed.
 * @property liveStatus The selected broker's live status while a survey streams to it.
 */
data class MqttBrokersUiState(
    val loading: Boolean = true,
    val brokers: List<BrokerRowUi> = emptyList(),
    val activeId: String? = null,
    val organizationBroker: OrganizationBrokerUi? = null,
    val hasOwnBrokers: Boolean = false,
    val liveStatus: DestinationStatus? = null,
    val clientId: String = "",
    val deviceStatusEnabled: Boolean = true,
) {
    /** True when the organization's broker is the one in use, so nothing here can be changed. */
    val organizationInForce: Boolean get() = organizationBroker != null

    /** True when brokers are saved but none is selected, so MQTT has nowhere to send. */
    val noneSelected: Boolean get() = !organizationInForce && brokers.isNotEmpty() && activeId == null
}

/** One saved broker in the list. */
data class BrokerRowUi(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tlsEnabled: Boolean,
    val username: String,
)

/** The organization's broker, as the managed configuration provides it. */
data class OrganizationBrokerUi(val host: String, val port: Int, val tlsEnabled: Boolean)

/** One-off messages the MQTT pages show in a snackbar. */
sealed interface MqttMessage {
    data class Using(val name: String) : MqttMessage

    /** The stream moved to another broker; [previousId] is what Undo selects again. */
    data class StreamingTo(val name: String, val previousId: String?) : MqttMessage

    data class Saved(val name: String, val outcome: SaveOutcome) : MqttMessage
    data class Removed(val name: String) : MqttMessage
    data class QrImported(val name: String, val updatedExisting: Boolean, val outcome: SaveOutcome) : MqttMessage
    data object NotABrokerCode : MqttMessage
    data object OwnSettingsInUse : MqttMessage
}

/** What saving a broker did to the survey, so the message can say what happens next. */
enum class SaveOutcome {
    /** Nothing more to say. */
    NONE,

    /** The first broker: MQTT was turned on for the next survey. */
    MQTT_TURNED_ON,

    /** The first broker, saved while recording: MQTT was not added to the running survey. */
    TURN_ON_TO_STREAM,

    /** The first broker, and the running survey already sends to MQTT: the stream starts now. */
    STARTING,

    /** The selected broker was edited while streaming, so the stream reconnects. */
    RECONNECTING;

    companion object {
        /** @return The message to show for what setting up the first broker did to the survey. */
        fun of(firstSetup: FirstSetupOutcome): SaveOutcome = when (firstSetup) {
            FirstSetupOutcome.NONE -> NONE
            FirstSetupOutcome.TURNED_ON -> MQTT_TURNED_ON
            FirstSetupOutcome.TURN_ON_TO_STREAM -> TURN_ON_TO_STREAM
            FirstSetupOutcome.STARTING -> STARTING
        }
    }
}
