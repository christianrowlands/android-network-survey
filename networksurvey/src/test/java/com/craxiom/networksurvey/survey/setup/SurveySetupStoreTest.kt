package com.craxiom.networksurvey.survey.setup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.survey.engine.LastSession
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveyDefaults
import com.craxiom.networksurvey.survey.model.SurveySetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `a phone that never stored a setup gets the fresh install default and no saved setups`() {
        assertEquals(SurveyDefaults.freshInstallSetup(), store.getCurrentSetup())
        assertTrue(store.getSavedSetups().isEmpty())
        assertFalse(store.onboardingPending)
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
        assertEquals(SurveyDefaults.freshInstallSetup(), store.getCurrentSetup())
    }

    @Test
    fun `save adds a setup, trims its name and selects it`() {
        val saved = store.save(WIFI_TO_FILES, "  Office walk ")
        assertEquals("Office walk", saved.name)
        assertFalse(saved.starred)
        assertEquals(saved.id, store.getSelectedSetupId())
        assertEquals(listOf(saved), store.getSavedSetups())
        assertEquals(saved, store.getCurrentSetup())
    }

    @Test
    fun `saving under a taken name replaces that setup and keeps its id and star`() {
        val first = store.save(WIFI_TO_FILES, "Office walk")
        val other = store.save(GNSS_TO_MQTT, "Drive")
        store.setStarred(first.id, true)

        val replaced = store.save(GNSS_TO_MQTT, "office WALK")

        assertEquals(first.id, replaced.id)
        assertTrue(replaced.starred)
        assertEquals("office WALK", replaced.name)
        assertEquals(GNSS_TO_MQTT.recordTypes, replaced.recordTypes)
        assertEquals(listOf(first.id, other.id), store.getSavedSetups().map { it.id })
    }

    @Test
    fun `rename, star and delete change only that setup`() {
        val a = store.save(WIFI_TO_FILES, "A")
        val b = store.save(GNSS_TO_MQTT, "B")

        store.rename(a.id, " Renamed ")
        store.setStarred(b.id, true)
        assertEquals(listOf("Renamed", "B"), store.getSavedSetups().map { it.name })
        assertEquals(listOf(false, true), store.getSavedSetups().map { it.starred })

        store.rename(b.id, "   ")
        assertEquals("B", store.getSavedSetups().last().name)

        store.delete(a.id)
        assertEquals(listOf(b.id), store.getSavedSetups().map { it.id })
        // The working setup is left as it is
        assertEquals(GNSS_TO_MQTT.recordTypes, store.getCurrentSetup().recordTypes)
    }

    @Test
    fun `display order puts starred setups first and keeps the saved order within each group`() {
        val a = store.save(WIFI_TO_FILES, "A")
        val b = store.save(GNSS_TO_MQTT, "B")
        val c = store.save(WIFI_TO_FILES, "C")
        store.setStarred(c.id, true)
        store.setStarred(b.id, true)
        assertEquals(listOf(b.id, c.id, a.id), store.orderedSavedSetups().map { it.id })
    }

    @Test
    fun `names are found trimmed and ignoring case`() {
        val saved = store.save(WIFI_TO_FILES, "Office walk")
        assertEquals(saved.id, store.findByName(" OFFICE walk ")?.id)
        assertNull(store.findByName("Office"))
    }

    @Test
    fun `setups stored with built-in presets are read, and the migration can remove the presets`() {
        preferences.edit {
            putString(
                SurveyPreferenceKeys.SAVED_SETUPS,
                """[{"id":"builtin_drive_test","name":"Drive test","recordTypes":["gnss"],"destinations":["log_files"],"builtIn":true},""" +
                    """{"id":"mine","name":"Mine","recordTypes":["wifi"],"destinations":["mqtt"],"starred":true}]""",
            )
        }
        assertEquals(listOf("builtin_drive_test", "mine"), store.getSavedSetups().map { it.id })

        val removed = store.removeLegacyBuiltIns()

        assertEquals(listOf("builtin_drive_test"), removed.map { it.id })
        assertEquals(listOf("mine"), store.getSavedSetups().map { it.id })
        assertTrue(store.getSavedSetups().single().starred)
        val json = preferences.getString(SurveyPreferenceKeys.SAVED_SETUPS, "")!!
        assertFalse(json, json.contains("builtIn"))
    }

    @Test
    fun `corrupt saved setups read as none`() {
        preferences.edit { putString(SurveyPreferenceKeys.SAVED_SETUPS, "[{nope") }
        assertTrue(store.getSavedSetups().isEmpty())
    }

    @Test
    fun `selecting a saved setup remembers its id and a custom edit clears it`() {
        val saved = store.save(WIFI_TO_FILES, "A")
        assertEquals(saved.id, store.getSelectedSetupId())
        store.setCurrentSetup(SurveySetup.custom(saved.recordTypes, saved.destinations))
        assertNull(store.getSelectedSetupId())
        assertNotEquals(saved.id, store.getCurrentSetup().id)
    }

    @Test
    fun `flags and hidden destinations round trip`() {
        store.onboardingPending = true
        assertTrue(store.onboardingPending)
        store.userAutoStart = true
        store.stoppedByUser = true
        store.hiddenDestinations = setOf(Destination.GRPC)
        assertTrue(store.userAutoStart)
        assertTrue(store.stoppedByUser)
        assertEquals(setOf(Destination.GRPC), store.hiddenDestinations)
    }

    @Test
    fun `the last session round trips and starts out empty`() {
        assertNull(store.lastSession)
        val session = LastSession(durationMs = 2_520_000L, totalRecords = 3_118L, endedAtMs = 1_791_300_000_000L, missionId = "NS-1")
        store.lastSession = session
        assertEquals(session, SurveySetupStore(preferences).lastSession)
        store.lastSession = null
        assertNull(store.lastSession)
    }

    private companion object {
        val WIFI_TO_FILES = SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES))
        val GNSS_TO_MQTT = SurveySetup.custom(setOf(RecordType.GNSS), setOf(Destination.MQTT))
    }
}
