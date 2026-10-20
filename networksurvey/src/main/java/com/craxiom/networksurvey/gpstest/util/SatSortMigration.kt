package com.craxiom.networksurvey.gpstest.util

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * Moves the GNSS sort choice from its old stored form, the displayed label of `R.array.sort_sats`,
 * to the option's index under [NEW_KEY].
 *
 * The label was stored in whatever language the app showed when the user chose it, so rewording or
 * translating the options silently reset the sort. The labels the old versions could have stored are
 * frozen here ([LEGACY_LABELS]: English and Simplified Chinese, the only languages that ever had the
 * array) instead of being read from the resources, which now hold newer wording.
 *
 * There is no "done" flag: once the old key is gone a launch finds nothing and writes nothing.
 */
object SatSortMigration {

    /** The old key, which held the label. */
    const val OLD_KEY = "preference_default_sat_sort"

    /** The new key, which holds the index into `R.array.sort_sats` as an Int. */
    const val NEW_KEY = NetworkSurveyConstants.PROPERTY_GNSS_SORT_INDEX

    /** The labels `R.array.sort_sats` had before the index, in option order, per language. */
    val LEGACY_LABELS: List<List<String>> = listOf(
        listOf(
            "Constellation",
            "Carrier Frequency",
            "Signal Strength",
            "Used in Fix",
            "Constellation, Carrier Frequency",
            "Constellation, Signal Strength",
            "Constellation, Used in Fix",
            "AGC",
        ),
        listOf(
            "星座",
            "载波频率",
            "信号强度",
            "用于定位",
            "星座，载波频率",
            "星座，信号强度",
            "星座，用于定位",
            "AGC",
        ),
    )

    /**
     * Turns a stored label into an index under [NEW_KEY] and removes the label. A label that matches
     * none of [labels] is removed and the sort falls back to the first option, as it always did.
     */
    @JvmStatic
    @JvmOverloads
    fun migrate(preferences: SharedPreferences, labels: List<List<String>> = LEGACY_LABELS) {
        if (!preferences.contains(OLD_KEY)) return
        val stored = (preferences.all[OLD_KEY] as? String)?.trim()
        val index = stored?.let { indexOf(it, labels) }
        preferences.edit {
            if (index != null && !preferences.contains(NEW_KEY)) putInt(NEW_KEY, index)
            remove(OLD_KEY)
        }
    }

    private fun indexOf(label: String, labels: List<List<String>>): Int? = labels.firstNotNullOfOrNull { options ->
        options.indexOfFirst { it.equals(label, ignoreCase = true) }.takeIf { it >= 0 }
    }
}
