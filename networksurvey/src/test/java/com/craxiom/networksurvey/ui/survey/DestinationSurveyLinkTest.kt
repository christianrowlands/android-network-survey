package com.craxiom.networksurvey.ui.survey

import android.content.Context
import androidx.core.content.edit
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Tests what setting up a streaming destination for the first time does to the survey. */
@RunWith(RobolectricTestRunner::class)
class DestinationSurveyLinkTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var store: SurveySetupStore
    private var restrictions: Map<String, Any?> = emptyMap()
    private lateinit var link: DestinationSurveyLink

    @Before
    fun setUp() {
        val preferences = context.getSharedPreferences("destination_link_test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        store = SurveySetupStore(preferences)
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)))
        link = DestinationSurveyLink(context, Destination.GRPC, store) { SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), false) }
    }

    @Test
    fun `idle, the first setup turns the destination on for the next survey`() {
        assertEquals(FirstSetupOutcome.TURNED_ON, link.onFirstSetup(recording = false))
        assertEquals(setOf(Destination.LOG_FILES, Destination.GRPC), store.getCurrentSetup().destinations)
        assertTrue(link.isInSetup())
    }

    @Test
    fun `while recording, the running survey is left alone`() {
        assertEquals(FirstSetupOutcome.TURN_ON_TO_STREAM, link.onFirstSetup(recording = true))
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
    }

    @Test
    fun `already in the setup, a recording survey starts it now and an idle one needs nothing`() {
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.GRPC)))

        assertEquals(FirstSetupOutcome.STARTING, link.onFirstSetup(recording = true))
        assertEquals(FirstSetupOutcome.NONE, link.onFirstSetup(recording = false))
    }

    @Test
    fun `an organization that turns the destination on counts as in the setup`() {
        restrictions = mapOf(SurveyMdmKeys.DESTINATION_GRPC to "on")

        assertEquals(FirstSetupOutcome.STARTING, link.onFirstSetup(recording = true))
        assertTrue(link.isInSetup())
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
    }

    @Test
    fun `an organization that keeps the destination off changes nothing`() {
        restrictions = mapOf(SurveyMdmKeys.DESTINATION_GRPC to "off")

        assertEquals(FirstSetupOutcome.NONE, link.onFirstSetup(recording = false))
        assertTrue(link.isTurnedOffByOrganization())
        assertFalse(link.isInSetup())
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
    }
}
