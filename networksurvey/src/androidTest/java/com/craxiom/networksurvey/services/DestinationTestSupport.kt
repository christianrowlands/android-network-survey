package com.craxiom.networksurvey.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import androidx.test.rule.ServiceTestRule
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.GnssRecord
import com.craxiom.messaging.GnssRecordData
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.PhoneState
import com.craxiom.messaging.PhoneStateData
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.destinations.LOOPBACK_HOST
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.services.controller.CellularController
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfile
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.google.protobuf.BoolValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.StringValue
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for the instrumented destination tests: binding the real
 * [NetworkSurveyService], resetting preferences, starting surveys through the orchestrator, and
 * pushing synthetic records through the same listener fan-out real scans use.
 *
 * Every synthetic record carries [MARKER_SERIAL] so a test can tell it apart from the real records
 * the phone's radios produce while the survey runs.
 */
object DestinationTestSupport {

    const val MARKER_SERIAL = "ns-destination-test-serial"
    const val SURVEY_TIMEOUT_SECONDS = 20L

    /** The PCI and TAC of [injectServingLte]'s cell, chosen so no real cell nearby shows the same. */
    const val INJECTED_PCI = 503
    const val INJECTED_TAC = 65_432

    /** The runtime permissions the survey needs to start every record type. */
    fun permissionRule(): GrantPermissionRule {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return GrantPermissionRule.grant(*permissions.toTypedArray())
    }

    fun context(): Context = ApplicationProvider.getApplicationContext()

    /**
     * Clears the default preferences so each test starts from a fresh install's settings.
     * `clearPackageData` in the runner arguments only works with the Test Orchestrator.
     */
    fun resetPreferences(extra: (android.content.SharedPreferences.Editor) -> Unit = {}) {
        PreferenceManager.getDefaultSharedPreferences(context()).edit(commit = true) {
            clear()
            extra(this)
        }
    }

    /**
     * Saves a broker profile for the loopback broker on [port] (no TLS, no login) the way the
     * broker editor does, and selects it when [select] is true.
     */
    fun saveBrokerProfile(port: Int, name: String = "Test broker $port", select: Boolean = true): MqttBrokerProfile =
        MqttBrokerProfileStore.create(context()).save(
            MqttBrokerProfile(id = MqttBrokerProfileStore.newId(), name = name, host = LOOPBACK_HOST, port = port, tlsEnabled = false),
            username = "",
            password = "",
            select = select,
        )

    fun brokerProfileStore(): MqttBrokerProfileStore = MqttBrokerProfileStore.create(context())

    fun bindService(rule: ServiceTestRule): NetworkSurveyService {
        val binder = rule.bindService(Intent(context(), NetworkSurveyService::class.java))
        return (binder as NetworkSurveyService.SurveyServiceBinder).service as NetworkSurveyService
    }

    fun start(
        service: NetworkSurveyService,
        types: Set<RecordType>,
        destinations: Set<Destination>,
    ): SurveyStartResult = service.startSurvey(SurveySetup.custom(types, destinations), SurveyOrigin.USER)
        .get(SURVEY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    fun apply(
        service: NetworkSurveyService,
        types: Set<RecordType>,
        destinations: Set<Destination>,
    ): SurveyStartResult =
        service.applySurveySetup(SurveySetup.custom(types, destinations), SurveyOrigin.USER)
            .get(SURVEY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    fun stop(service: NetworkSurveyService) {
        service.stopSurvey(SurveyOrigin.USER).get(SURVEY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /**
     * Sends one LTE record the way a real cellular scan does: to the per-protocol listeners (MQTT,
     * gRPC, files) and as a one-record batch (NS Analytics).
     */
    fun injectLte(service: NetworkSurveyService) {
        val record = LteRecord.newBuilder()
            .setData(LteRecordData.newBuilder().setDeviceSerialNumber(MARKER_SERIAL))
            .build()
        val processor = service.surveyRecordProcessorForTest
        processor.notifyLteRecordListeners(record)
        processor.notifyCellularListeners(listOf(CellularRecordWrapper(CellularProtocol.LTE, record)), 0)
    }

    /**
     * Sends one serving LTE cell to the screens only (not to the loggers or streams), on the
     * subscription a phone with one SIM scans, so the Cellular view shows it as the serving cell.
     */
    fun injectServingLte(service: NetworkSurveyService) {
        val record = LteRecord.newBuilder()
            .setData(
                LteRecordData.newBuilder()
                    .setDeviceSerialNumber(MARKER_SERIAL)
                    .setServingCell(BoolValue.of(true))
                    .setPlmn(StringValue.of("001-01"))
                    .setTac(Int32Value.of(INJECTED_TAC))
                    .setPci(Int32Value.of(INJECTED_PCI))
                    .setEarfcn(Int32Value.of(66_811))
                    .setRsrp(FloatValue.of(-77f))
            )
            .build()
        service.surveyRecordProcessorForTest.notifyCellularListeners(
            listOf(CellularRecordWrapper(CellularProtocol.LTE, record)),
            CellularController.DEFAULT_SUBSCRIPTION_ID,
        )
    }

    fun injectWifi(service: NetworkSurveyService) = service.surveyRecordProcessorForTest
        .notifyWifiBeaconRecordListeners(
            listOf(
                WifiRecordWrapper(
                    WifiBeaconRecord.newBuilder()
                        .setData(
                            WifiBeaconRecordData.newBuilder()
                                .setDeviceSerialNumber(MARKER_SERIAL)
                                .setSourceAddress("02:00:00:00:00:01")
                                .setBssid("02:00:00:00:00:01")
                                .setSsid("ns-test-ssid")
                        )
                        .build(),
                    ""
                )
            )
        )

    fun injectBluetooth(service: NetworkSurveyService) = service.surveyRecordProcessorForTest
        .notifyBluetoothRecordListeners(
            BluetoothRecord.newBuilder()
                .setData(
                    BluetoothRecordData.newBuilder()
                        .setDeviceSerialNumber(MARKER_SERIAL)
                        .setSourceAddress("02:00:00:00:00:02")
                )
                .build()
        )

    fun injectGnss(service: NetworkSurveyService) = service.surveyRecordProcessorForTest
        .notifyGnssRecordListeners(
            GnssRecord.newBuilder()
                .setData(GnssRecordData.newBuilder().setDeviceSerialNumber(MARKER_SERIAL))
                .build()
        )

    fun injectPhoneState(service: NetworkSurveyService) = service.surveyRecordProcessorForTest
        .notifyPhoneStateListeners(
            PhoneState.newBuilder()
                .setData(PhoneStateData.newBuilder().setDeviceSerialNumber(MARKER_SERIAL))
                .build()
        )
}
