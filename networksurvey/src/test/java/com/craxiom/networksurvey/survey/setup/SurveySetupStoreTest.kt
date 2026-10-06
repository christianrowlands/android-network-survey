package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyPresets
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests storing and reading survey setups.
 */
@RunWith(RobolectricTestRunner::class)
class SurveySetupStoreTest {

    private lateinit var preferences: SharedPreferences
    private lateinit var store: SurveySetupStore

    @Before
    fun setUp() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("store_test", Context.MODE_PRIVATE)
        preferences.edit { clear() }
        store = SurveySetupStore(preferences)
    }

    @Test
    fun `a phone that never stored a setup gets the NS Analytics preset`() {
        assertEquals(SurveyPresets.nsAnalyticsSurvey(), store.getCurrentSetup())
        assertEquals(SurveyPresets.all(), store.getSavedSetups())
    }

    @Test
    fun `the working setup round trips using wire names`() {
        val setup = SurveySetup.custom(setOf(RecordType.GNSS, RecordType.CDR), setOf(Destination.GRPC))
        store.setCurrentSetup(setup)
        assertEquals(setup, store.getCurrentSetup())
        assertNull(store.getSelectedSetupId())
        val json = preferences.getString(SurveyPreferenceKeys.CURRENT_SETUP, "")!!
        assertTrue(json, json.contains("\"gnss\"") && json.contains("\"grpc\""))
    }

    @Test
    fun `unknown wire names from a newer version are dropped`() {
        preferences.edit {
            putString(
                SurveyPreferenceKeys.CURRENT_SETUP,
                """{"id":"custom","name":"","recordTypes":["wifi","uwb"],"destinations":["log_files","satellite"],"builtIn":false}""",
            )
        }
        val setup = store.getCurrentSetup()
        assertEquals(setOf(RecordType.WIFI), setup.recordTypes)
        assertEquals(setOf(Destination.LOG_FILES), setup.destinations)
    }

    @Test
    fun `corrupt JSON falls back to the default`() {
        preferences.edit { putString(SurveyPreferenceKeys.CURRENT_SETUP, "{nope") }
        assertEquals(SurveyPresets.nsAnalyticsSurvey(), store.getCurrentSetup())
    }

    @Test
    fun `save as adds a user setup and selects it`() {
        val saved = store.saveAs(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)), "Office walk")
        assertEquals("Office walk", saved.name)
        assertFalse(saved.builtIn)
        assertEquals(saved.id, store.getSelectedSetupId())
        assertEquals(3, store.getSavedSetups().size)
    }

    @Test
    fun `built-in presets cannot be deleted`() {
        store.delete(SurveyPresets.DRIVE_TEST_ID)
        assertEquals(2, store.getSavedSetups().size)
    }

    @Test
    fun `flags and hidden destinations round trip`() {
        store.userAutoStart = true
        store.stoppedByUser = true
        store.hiddenDestinations = setOf(Destination.GRPC)
        assertTrue(store.userAutoStart)
        assertTrue(store.stoppedByUser)
        assertEquals(setOf(Destination.GRPC), store.hiddenDestinations)
    }
}
