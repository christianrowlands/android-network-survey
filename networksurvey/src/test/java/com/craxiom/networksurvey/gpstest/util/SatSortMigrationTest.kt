package com.craxiom.networksurvey.gpstest.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.PreferenceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The GNSS sort moves from its stored label to its index once, in either language the label could
 * have been stored in, and an unknown label falls back to the first option as it always did.
 */
@RunWith(RobolectricTestRunner::class)
class SatSortMigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences: SharedPreferences =
        context.getSharedPreferences("sat_sort_migration_test", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun englishLabel_becomesItsIndex() {
        storeLabel("Signal Strength")

        SatSortMigration.migrate(preferences)

        assertEquals(2, PreferenceUtils.getSatSortIndex(preferences))
        assertFalse(preferences.contains(SatSortMigration.OLD_KEY))
    }

    @Test
    fun chineseLabel_becomesItsIndex() {
        storeLabel("星座，用于定位")

        SatSortMigration.migrate(preferences)

        assertEquals(6, PreferenceUtils.getSatSortIndex(preferences))
    }

    @Test
    fun caseAndSurroundingSpace_stillMatch() {
        storeLabel("  constellation, carrier frequency ")

        SatSortMigration.migrate(preferences)

        assertEquals(4, PreferenceUtils.getSatSortIndex(preferences))
    }

    @Test
    fun unknownLabel_isRemovedAndTheFirstOptionApplies() {
        storeLabel("Something else")

        SatSortMigration.migrate(preferences)

        assertFalse(preferences.contains(SatSortMigration.OLD_KEY))
        assertFalse(preferences.contains(SatSortMigration.NEW_KEY))
        assertEquals(0, PreferenceUtils.getSatSortIndex(preferences))
    }

    @Test
    fun nonStringValue_isRemovedWithoutThrowing() {
        preferences.edit(commit = true) { putInt(SatSortMigration.OLD_KEY, 3) }

        SatSortMigration.migrate(preferences)

        assertFalse(preferences.contains(SatSortMigration.OLD_KEY))
        assertEquals(0, PreferenceUtils.getSatSortIndex(preferences))
    }

    @Test
    fun anIndexAlreadyStored_wins() {
        preferences.edit(commit = true) { putInt(SatSortMigration.NEW_KEY, 7) }
        storeLabel("Used in Fix")

        SatSortMigration.migrate(preferences)

        assertEquals(7, PreferenceUtils.getSatSortIndex(preferences))
        assertFalse(preferences.contains(SatSortMigration.OLD_KEY))
    }

    @Test
    fun nothingStored_writesNothing_andASecondRunChangesNothing() {
        SatSortMigration.migrate(preferences)
        assertTrue(preferences.all.isEmpty())

        storeLabel("AGC")
        SatSortMigration.migrate(preferences)
        SatSortMigration.migrate(preferences)

        assertEquals(7, PreferenceUtils.getSatSortIndex(preferences))
        assertEquals(setOf(SatSortMigration.NEW_KEY), preferences.all.keys)
    }

    @Test
    fun everyLegacyLanguage_hasOneLabelPerOption() {
        val options = context.resources.getStringArray(R.array.sort_sats)

        assertEquals(8, options.size)
        SatSortMigration.LEGACY_LABELS.forEach { assertEquals(options.size, it.size) }
    }

    private fun storeLabel(label: String) {
        preferences.edit(commit = true) { putString(SatSortMigration.OLD_KEY, label) }
    }
}
