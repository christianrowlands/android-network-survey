package com.craxiom.networksurvey.services

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.messaging.GnssRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.PhoneState
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.GpsListener
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener
import com.craxiom.networksurvey.listeners.ICdrEventListener
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.listeners.IDeviceStatusListener
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener
import com.craxiom.networksurvey.listeners.ILiveScanView
import com.craxiom.networksurvey.listeners.IPhoneStateListener
import com.craxiom.networksurvey.listeners.IPrivacyZoneExempt
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.model.CdrEvent
import com.craxiom.networksurvey.model.CdrEventType
import com.craxiom.networksurvey.model.ScoutWifiDetails
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.north
import com.craxiom.networksurvey.privacy.PrivacyZoneTestGate
import com.craxiom.networksurvey.privacy.PrivacyZoneTestGate.Companion.HOME
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The record fan-out inside and outside a privacy zone, through the real [SurveyRecordProcessor]:
 * sinks get nothing and nothing is counted inside a zone, the live screens and the Watchlist still
 * get every record, NS Scout records are judged by where they were heard, and device status keeps
 * going out without its locations.
 */
@RunWith(RobolectricTestRunner::class)
class PrivacyZonePipelineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zones = PrivacyZoneTestGate()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val service = mock<NetworkSurveyService>()
    private lateinit var processor: SurveyRecordProcessor

    @Before
    fun setUp() {
        whenever(service.missionIdForRecords).thenReturn("")
        // The processor reads the scan intervals at once, through the (here empty) MDM restrictions
        shadowOf(context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager).setApplicationRestrictions(Bundle())
        processor = SurveyRecordProcessor(GpsListener(zones.gate), "device", context, executor, zones.gate)
        processor.setNetworkSurveyService(service)
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `inside a zone no cellular record reaches a sink or is counted, the live view still sees it`() {
        val sink = mock<ICellularSurveyRecordListener>()
        val live = liveMock(ICellularSurveyRecordListener::class.java)
        processor.registerCellularSurveyRecordListener(sink)
        processor.registerCellularSurveyRecordListener(live)
        zones.enter()

        processor.notifyLteRecordListeners(LteRecord.getDefaultInstance())
        processor.notifyCellularListeners(emptyList(), 1)

        verify(sink, never()).onLteSurveyRecord(any())
        verify(sink, never()).onCellularBatch(any(), any())
        verify(live).onLteSurveyRecord(any())
        verify(live).onCellularBatch(any(), eq(1))
        verify(service, never()).onRecordsProcessed(any(), any())
    }

    @Test
    fun `outside a zone sinks get records and they are counted`() {
        val sink = mock<ICellularSurveyRecordListener>()
        processor.registerCellularSurveyRecordListener(sink)
        zones.leave()

        processor.notifyLteRecordListeners(LteRecord.getDefaultInstance())

        verify(sink).onLteSurveyRecord(any())
        verify(service).onRecordsProcessed(RecordType.CELLULAR, 1)
    }

    @Test
    fun `leaving the zone lets records through again`() {
        val sink = mock<IGnssSurveyRecordListener>()
        processor.registerGnssSurveyRecordListener(sink)
        zones.enter()
        processor.notifyGnssRecordListeners(GnssRecord.getDefaultInstance())
        verify(sink, never()).onGnssSurveyRecord(any())

        zones.leave()
        processor.notifyGnssRecordListeners(GnssRecord.getDefaultInstance())
        verify(sink).onGnssSurveyRecord(any())
    }

    @Test
    fun `before any decision records are kept`() {
        val sink = mock<IPhoneStateListener>()
        processor.registerPhoneStateListener(sink)

        processor.notifyPhoneStateListeners(PhoneState.getDefaultInstance())

        verify(sink).onPhoneState(any())
    }

    @Test
    fun `inside a zone every other record type stays out of the sinks`() {
        val wifi = mock<IWifiSurveyRecordListener>()
        val bluetooth = mock<IBluetoothSurveyRecordListener>()
        val gnss = mock<IGnssSurveyRecordListener>()
        val phoneState = mock<IPhoneStateListener>()
        val cdr = mock<ICdrEventListener>()
        processor.registerWifiSurveyRecordListener(wifi)
        processor.registerBluetoothSurveyRecordListener(bluetooth)
        processor.registerGnssSurveyRecordListener(gnss)
        processor.registerPhoneStateListener(phoneState)
        processor.registerCdrEventListener(cdr)
        zones.enter()

        processor.notifyWifiBeaconRecordListeners(listOf(phoneWifi()))
        processor.notifyBluetoothRecordListeners(BluetoothRecord.getDefaultInstance())
        processor.notifyGnssRecordListeners(GnssRecord.getDefaultInstance())
        processor.notifyPhoneStateListeners(PhoneState.getDefaultInstance())
        processor.notifyCdrListeners(CdrEvent(CdrEventType.INCOMING_SMS, "1", "2", 1, "device"))

        verify(wifi, never()).onWifiBeaconSurveyRecords(any())
        verify(bluetooth, never()).onBluetoothSurveyRecord(any())
        verify(gnss, never()).onGnssSurveyRecord(any())
        verify(phoneState, never()).onPhoneState(any())
        verify(cdr, never()).onCdrEvent(anyOrNull())
        verify(service, never()).onRecordsProcessed(any(), any())
    }

    @Test
    fun `the Watchlist still sees Wi-Fi inside a zone`() {
        val watchlist = Mockito.mock(
            IWifiSurveyRecordListener::class.java,
            Mockito.withSettings().extraInterfaces(IPrivacyZoneExempt::class.java),
        )
        processor.registerPassiveWifiSurveyRecordListener(watchlist)
        zones.enter()

        processor.notifyWifiBeaconRecordListeners(listOf(phoneWifi()))

        verify(watchlist).onWifiBeaconSurveyRecords(any())
    }

    @Test
    fun `an NS Scout record heard inside a zone stays out even after the phone left`() {
        val sink = mock<IWifiSurveyRecordListener>()
        val live = liveMock(IWifiSurveyRecordListener::class.java)
        processor.registerWifiSurveyRecordListener(sink)
        processor.registerWifiSurveyRecordListener(live)
        zones.leave()
        val heardAtHome = scoutWifi(meters = 0.0)
        val heardAway = scoutWifi(meters = PrivacyZoneTestGate.OUTSIDE_METERS)

        processor.notifyWifiBeaconRecordListeners(listOf(heardAtHome, heardAway))

        assertEquals(listOf(heardAway), captureWifi(sink))
        assertEquals(listOf(heardAtHome, heardAway), captureWifi(live))
        verify(service).onRecordsProcessed(RecordType.WIFI, 1)
    }

    @Test
    fun `device status goes out inside a zone with every location cleared`() {
        val sink = mock<IDeviceStatusListener>()
        processor.registerDeviceStatusListener(sink)
        zones.enter()

        processor.notifyDeviceStatusListeners(statusWithLocations())

        val captor = ArgumentCaptor.forClass(DeviceStatus::class.java)
        verify(sink).onDeviceStatus(captor.capture())
        val data = captor.value.data
        assertEquals(0.0, data.latitude, 0.0)
        assertEquals(0.0, data.gnssLatitude, 0.0)
        assertEquals(0.0, data.networkLongitude, 0.0)
        assertEquals(0, data.accuracy)
        assertFalse(data.hasMockLocation())
        assertEquals("Pixel", data.deviceModel)
        assertTrue(data.hasBatteryLevelPercent())
    }

    @Test
    fun `device status outside a zone keeps its location but not a last fix taken inside one`() {
        val sink = mock<IDeviceStatusListener>()
        processor.registerDeviceStatusListener(sink)
        zones.leave()
        val (awayLat, _) = north(HOME, PrivacyZoneTestGate.OUTSIDE_METERS)

        // The GNSS and network blocks are each provider's last fix, still the one taken at home
        processor.notifyDeviceStatusListeners(statusWithLocations(primaryMeters = PrivacyZoneTestGate.OUTSIDE_METERS))

        val captor = ArgumentCaptor.forClass(DeviceStatus::class.java)
        verify(sink).onDeviceStatus(captor.capture())
        val data = captor.value.data
        assertEquals(awayLat, data.latitude, 0.0)
        assertTrue(data.hasMockLocation())
        assertEquals(0.0, data.gnssLatitude, 0.0)
        assertEquals(0.0, data.networkLongitude, 0.0)
    }

    private fun <T> liveMock(type: Class<T>): T = Mockito.mock(type, Mockito.withSettings().extraInterfaces(ILiveScanView::class.java))

    @Suppress("UNCHECKED_CAST")
    private fun captureWifi(listener: IWifiSurveyRecordListener): List<WifiRecordWrapper> {
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<WifiRecordWrapper>>
        verify(listener).onWifiBeaconSurveyRecords(captor.capture())
        return captor.value
    }

    private fun phoneWifi() = WifiRecordWrapper(WifiBeaconRecord.getDefaultInstance(), "")

    private fun scoutWifi(meters: Double): WifiRecordWrapper {
        val (lat, lon) = north(HOME, meters)
        val data = WifiBeaconRecordData.newBuilder().setLatitude(lat).setLongitude(lon).setAccuracy(SCOUT_ACCURACY)
        return WifiRecordWrapper(
            WifiBeaconRecord.newBuilder().setData(data).build(), "", false,
            ScoutWifiDetails("NS-TEST", null, null, 0, null), false,
        )
    }

    /** A device status with every location at [HOME]'s center, except the primary one [primaryMeters] north of it. */
    private fun statusWithLocations(primaryMeters: Double = 0.0): DeviceStatus = DeviceStatus.newBuilder().setData(
        DeviceStatusData.newBuilder()
            .setLatitude(north(HOME, primaryMeters).first).setLongitude(HOME.longitude).setAccuracy(SCOUT_ACCURACY)
            .setGnssLatitude(HOME.latitude).setGnssLongitude(HOME.longitude)
            .setNetworkLatitude(HOME.latitude).setNetworkLongitude(HOME.longitude)
            .setMockLocation(com.google.protobuf.BoolValue.of(true))
            .setBatteryLevelPercent(com.google.protobuf.Int32Value.of(BATTERY))
            .setDeviceModel("Pixel")
    ).build()

    private companion object {
        const val SCOUT_ACCURACY = 8
        const val BATTERY = 80
    }
}
