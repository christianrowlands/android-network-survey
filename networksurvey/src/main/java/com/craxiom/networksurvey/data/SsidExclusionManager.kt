package com.craxiom.networksurvey.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the SSID exclusion list for filtering WiFi scan results.
 *
 * This class handles adding, removing, and checking SSIDs that should be excluded
 * from WiFi survey data. The exclusion list is persisted using SharedPreferences
 * and has a maximum size limit to prevent performance issues.
 */
@Singleton
class SsidExclusionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREF_NAME = "network_survey_ssid_exclusion"
        private const val KEY_EXCLUDED_SSIDS = "excluded_ssids"
        const val MAX_EXCLUSION_LIST_SIZE = 30
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Adds an SSID to the exclusion list.
     *
     * @param ssid The SSID to exclude
     * @return true if the SSID was added successfully, false if the list is full or SSID already exists
     */
    fun addExcludedSsid(ssid: String): Boolean {
        val currentSet = getExcludedSsids().toMutableSet()

        if (currentSet.size >= MAX_EXCLUSION_LIST_SIZE) {
            Timber.w("Cannot add SSID to exclusion list - maximum size reached")
            return false
        }

        if (currentSet.contains(ssid)) {
            Timber.d("SSID already in exclusion list: $ssid")
            return false
        }

        currentSet.add(ssid)
        saveExcludedSsids(currentSet)
        Timber.i("Added SSID to exclusion list: $ssid")
        return true
    }

    /**
     * Removes an SSID from the exclusion list.
     *
     * @param ssid The SSID to remove from exclusion
     * @return true if the SSID was removed, false if it wasn't in the list
     */
    fun removeExcludedSsid(ssid: String): Boolean {
        val currentSet = getExcludedSsids().toMutableSet()
        val removed = currentSet.remove(ssid)

        if (removed) {
            saveExcludedSsids(currentSet)
            Timber.i("Removed SSID from exclusion list: $ssid")
        }

        return removed
    }

    /**
     * Checks if an SSID is in the exclusion list.
     *
     * @param ssid The SSID to check
     * @return true if the SSID should be excluded
     */
    fun isExcluded(ssid: String?): Boolean {
        if (ssid == null) return false
        return getExcludedSsids().contains(ssid)
    }

    /**
     * Gets all excluded SSIDs as an immutable set.
     *
     * @return Set of excluded SSIDs
     */
    fun getExcludedSsids(): Set<String> {
        return sharedPreferences.getStringSet(KEY_EXCLUDED_SSIDS, emptySet()) ?: emptySet()
    }

    /**
     * Gets all excluded SSIDs as a sorted list for display purposes.
     *
     * @return Sorted list of excluded SSIDs
     */
    fun getExcludedSsidsList(): List<String> {
        return getExcludedSsids().sorted()
    }

    /**
     * Clears all SSIDs from the exclusion list.
     */
    fun clearExcludedSsids() {
        saveExcludedSsids(emptySet())
        Timber.i("Cleared all SSIDs from exclusion list")
    }

    /**
     * Checks if the exclusion list is at maximum capacity.
     *
     * @return true if no more SSIDs can be added
     */
    fun isAtMaxCapacity(): Boolean {
        return getExcludedSsids().size >= MAX_EXCLUSION_LIST_SIZE
    }

    /**
     * Gets the current number of excluded SSIDs.
     *
     * @return The count of excluded SSIDs
     */
    fun getExcludedCount(): Int {
        return getExcludedSsids().size
    }

    private fun saveExcludedSsids(ssids: Set<String>) {
        sharedPreferences.edit {
            putStringSet(KEY_EXCLUDED_SSIDS, ssids)
        }
    }
}