package com.craxiom.networksurvey.services.controller

import com.craxiom.networksurvey.logging.WifiClientCsvLogger
import com.craxiom.networksurvey.logging.WifiProbeCsvLogger
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.services.NetworkSurveyService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/** A changed log rollover size reaches the client and probe CSV files, as it does the other files. */
@RunWith(RobolectricTestRunner::class)
class WifiClientsControllerRolloverTest {
    private val clientLogger = mock<WifiClientCsvLogger>()
    private val probeLogger = mock<WifiProbeCsvLogger>()
    private val controller = WifiClientsController(mock<NetworkSurveyService>(), mock<WifiController>(), mock<ScoutManager>(), clientLogger, probeLogger)

    @Test
    fun `a rollover setting change reaches both files`() {
        controller.onRolloverPreferenceChanged()

        verify(clientLogger).onSharedPreferenceChanged()
        verify(probeLogger).onSharedPreferenceChanged()
    }

    @Test
    fun `an organization's rollover change reaches both files`() {
        controller.onMdmPreferenceChanged()

        verify(clientLogger).onMdmPreferenceChanged()
        verify(probeLogger).onMdmPreferenceChanged()
    }

    @Test
    fun `nothing reaches the loggers after the service is gone`() {
        controller.onDestroy()

        controller.onRolloverPreferenceChanged()
        controller.onMdmPreferenceChanged()

        verify(clientLogger, never()).onSharedPreferenceChanged()
        verify(probeLogger, never()).onMdmPreferenceChanged()
    }
}
