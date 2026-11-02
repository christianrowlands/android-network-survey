package com.craxiom.networksurvey.ui.signals

import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The scan interval each Signals sub-tab shows while a screen makes its radio scan faster. */
class ShownIntervalsTest {

    private val configured = mapOf(
        SignalsSubTab.CELLULAR to 15,
        SignalsSubTab.WIFI to 30,
        SignalsSubTab.BLUETOOTH to 30,
        SignalsSubTab.GNSS to 45,
    )

    @Test
    fun withoutFasterScanning_theSurveyIntervalsShow() {
        assertEquals(configured, shownIntervals(configured, emptyMap()))
    }

    @Test
    fun aRadioScanningFaster_showsItsFasterInterval() {
        val shown = shownIntervals(configured, mapOf(RecordType.CELLULAR to 2_000, RecordType.WIFI to 5_000))

        assertEquals(2, shown[SignalsSubTab.CELLULAR])
        assertEquals(5, shown[SignalsSubTab.WIFI])
        assertEquals(30, shown[SignalsSubTab.BLUETOOTH])
    }

    @Test
    fun aSubSecondInterval_showsAsOneSecond() {
        assertEquals(1, shownIntervals(configured, mapOf(RecordType.CELLULAR to 500))[SignalsSubTab.CELLULAR])
    }
}
