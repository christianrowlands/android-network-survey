package com.craxiom.networksurvey.notification

import com.craxiom.mqttlibrary.connection.ConnectionState

/**
 * An immutable snapshot of everything the survey notification can reflect.
 *
 * The [com.craxiom.networksurvey.services.NetworkSurveyService] fills one of these in from its
 * existing getters each time something changes, and [SurveyNotificationBuilder] turns it into the
 * notification. Keeping the snapshot separate from the service makes the title and text rules a
 * pure function that can be unit tested without a running service.
 *
 * @property fileLogging True when any protocol is being written to a CSV or GeoPackage file.
 * @property communitySurvey True when records are being collected for OpenCelliD / BeaconDB.
 * @property nsAnalytics True when records are being collected for an NS Analytics workspace.
 * @property mqttState The MQTT broker connection state.
 * @property mqttDropping True when MQTT is dropping messages because its queue is full while other
 * outputs keep running (scanning continues).
 * @property grpcState The gRPC server connection state.
 * @property scanningCellular True when cellular scanning is running.
 * @property scanningWifi True when Wi-Fi scanning is running.
 * @property scanningBluetooth True when Bluetooth scanning is running.
 * @property scanningGnss True when GNSS scanning is running.
 * @property phoneStateActive True when phone state events are being collected.
 * @property cdrActive True when CDR events are being collected.
 * @property pausedForBattery True when scanning is paused because the battery is low.
 * @property batteryLevel The battery percentage, only meaningful when [pausedForBattery] is true.
 * @property pausedForBackpressure True when scanning is paused because the MQTT queue is full and
 * MQTT is the only output.
 * @property stalledMinutes Minutes since the last record when the stall watchdog has fired, or null
 * while records are flowing (or the watchdog is not applicable).
 * @property sessionStartMs Epoch millis of the survey session start, or null when nothing is running.
 */
data class SurveyNotificationState(
    val fileLogging: Boolean = false,
    val communitySurvey: Boolean = false,
    val nsAnalytics: Boolean = false,
    val mqttState: ConnectionState = ConnectionState.DISCONNECTED,
    val mqttDropping: Boolean = false,
    val grpcState: ConnectionState = ConnectionState.DISCONNECTED,
    val scanningCellular: Boolean = false,
    val scanningWifi: Boolean = false,
    val scanningBluetooth: Boolean = false,
    val scanningGnss: Boolean = false,
    val phoneStateActive: Boolean = false,
    val cdrActive: Boolean = false,
    val pausedForBattery: Boolean = false,
    val batteryLevel: Int = -1,
    val pausedForBackpressure: Boolean = false,
    val stalledMinutes: Int? = null,
    val sessionStartMs: Long? = null
) {
    /** True when the MQTT connection is up or being (re)established. */
    val mqttActive: Boolean
        get() = mqttState == ConnectionState.CONNECTED || mqttState == ConnectionState.CONNECTING

    /** True when the gRPC connection is up or being (re)established. */
    val grpcActive: Boolean
        get() = grpcState == ConnectionState.CONNECTED || grpcState == ConnectionState.CONNECTING

    /** True when at least one survey output is running. */
    val anyOutputActive: Boolean
        get() = fileLogging || communitySurvey || nsAnalytics || mqttActive || grpcActive

    /** True when scanning has been deliberately paused by the app. */
    val paused: Boolean
        get() = pausedForBattery || pausedForBackpressure
}
