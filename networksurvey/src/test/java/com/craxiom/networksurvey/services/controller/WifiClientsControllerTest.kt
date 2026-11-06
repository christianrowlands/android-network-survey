package com.craxiom.networksurvey.services.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craxiom.messaging.WifiClientRecord
import com.craxiom.messaging.WifiProbeRequestRecord
import com.craxiom.networksurvey.data.SsidExclusionManager
import com.craxiom.networksurvey.listeners.IWifiClientSurveyRecordListener
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.clients.ScoutClientTestData
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.scout.wifi.ScoutHeard
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.model.RecordType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * What the controller does with a closed window: nothing without a sink (no record numbers, no
 * counts), and a source that starts during a pause stays paused until the resume. It also reads
 * the user's SSID exclusion list for the pipeline.
 */
@RunWith(RobolectricTestRunner::class)
class WifiClientsControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val service = mock<NetworkSurveyService>()
    private val manager = mock<ScoutManager>()
    private val wifiSource = MutableStateFlow(WifiSource.PHONE)
    private val clientRecords = ArrayList<WifiClientRecord>()
    private val probeRecords = ArrayList<WifiProbeRequestRecord>()
    private val sink = object : IWifiClientSurveyRecordListener {
        override fun onWifiClientRecords(clientRecords: List<WifiClientRecord>) {
            this@WifiClientsControllerTest.clientRecords += clientRecords
        }

        override fun onWifiProbeRequestRecords(probeRecords: List<WifiProbeRequestRecord>) {
            this@WifiClientsControllerTest.probeRecords += probeRecords
        }
    }
    private lateinit var controller: WifiClientsController

    @Before
    fun setUp() {
        whenever(service.getSharedPreferences(anyOrNull(), any()))
            .thenAnswer { app.getSharedPreferences(it.getArgument<String>(0), it.getArgument(1)) }
        whenever(service.surveyDeviceId).thenReturn("8C2E1F7A")
        whenever(service.missionIdForRecords).thenReturn("")
        whenever(manager.pairedScout).thenReturn(MutableStateFlow(SCOUT))
        whenever(manager.wifiSource).thenReturn(wifiSource)
        whenever(manager.linkDetails).thenReturn(MutableStateFlow(ScoutLinkDetails()))
        controller = WifiClientsController(service, mock<WifiController>(), manager)
    }

    @After
    fun tearDown() {
        controller.onDestroy()
    }

    private fun <O> heard(observation: O) = ScoutHeard(observation, "NS-ABCD", HEARD, null, false)

    @Test
    fun `a window with no sink takes no record numbers and adds nothing to the counts`() {
        controller.onScoutClients(listOf(heard(ScoutClientTestData.client(1, -60, HEARD))))
        controller.onScoutProbes(listOf(heard(ScoutClientTestData.probe(1, "eduroam", -60, HEARD))))

        verify(service, never()).onRecordsProcessed(any(), any())

        controller.registerListener(sink)
        controller.onScoutClients(listOf(heard(ScoutClientTestData.client(1, -60, HEARD))))
        controller.onScoutProbes(listOf(heard(ScoutClientTestData.probe(1, "eduroam", -60, HEARD))))

        // The numbers start at 1 as if the windows before the sink never happened
        assertEquals(1, clientRecords.single().data.recordNumber)
        assertEquals(1, probeRecords.single().data.recordNumber)
        verify(service, times(2)).onRecordsProcessed(eq(RecordType.WIFI_CLIENTS), eq(1))
    }

    @Test
    fun `a source that starts during a pause stays paused until the resume`() {
        wifiSource.value = WifiSource.SCOUT
        controller.pauseScanning()
        controller.registerListener(sink)

        // Started and let go at once: the board is not left capturing
        verify(manager).requestCapture(any(), any())
        verify(manager).release(any())

        controller.resumeScanning()
        verify(manager, times(2)).requestCapture(any(), any())
        verify(manager).release(any())
    }

    @Test
    fun `a pause and resume with no source running asks the board for nothing`() {
        controller.pauseScanning()
        controller.resumeScanning()
        verify(manager, never()).requestCapture(any(), any())

        // After the resume a new source runs normally
        wifiSource.value = WifiSource.SCOUT
        controller.registerListener(sink)
        verify(manager).requestCapture(any(), any())
        verify(manager, never()).release(any())
    }

    @Test
    fun `the pipeline reads the user's exclusion list`() {
        SsidExclusionManager(app).addExcludedSsid("HOME-5G")

        assertTrue(controller.isExcludedSsid("HOME-5G"))
        assertFalse(controller.isExcludedSsid("eduroam"))
    }

    private companion object {
        const val HEARD = 1_792_000_030_000L

        val SCOUT = PairedScout("NS-02000000A1B2", "02:00:00:00:A1:B2", "A1B2", 0, "", "0.1.0", 1, emptyList(), 0L)
    }
}
