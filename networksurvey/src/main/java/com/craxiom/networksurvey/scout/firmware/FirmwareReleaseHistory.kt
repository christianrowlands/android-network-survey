package com.craxiom.networksurvey.scout.firmware

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * What this phone has learned from verified release manifests, so a stale or replayed manifest (an
 * old but validly signed release) is noticed (release-manifest.md, "Limitations").
 *
 * @property highestVersion The highest release version verified so far.
 * @property minimumVersion The highest `min_version` seen: releases below it are refused.
 */
data class FirmwareReleaseRecord(val highestVersion: String? = null, val minimumVersion: String? = null) {
    /** @return This record with a verified manifest's [version] and [minimum] folded in. */
    fun merging(version: String, minimum: String?): FirmwareReleaseRecord = FirmwareReleaseRecord(
        highestVersion = FirmwareVersion.higher(highestVersion, version),
        minimumVersion = FirmwareVersion.higher(minimumVersion, minimum),
    )
}

/** Where the [FirmwareReleaseRecord] is kept. */
interface FirmwareReleaseHistory {
    fun load(): FirmwareReleaseRecord
    fun save(record: FirmwareReleaseRecord)
}

/** [FirmwareReleaseHistory] in the app's preferences. */
class PreferenceFirmwareReleaseHistory(private val preferences: SharedPreferences) : FirmwareReleaseHistory {
    override fun load(): FirmwareReleaseRecord = FirmwareReleaseRecord(
        highestVersion = preferences.getString(FirmwareConstants.PREFERENCE_HIGHEST_RELEASE, null),
        minimumVersion = preferences.getString(FirmwareConstants.PREFERENCE_MINIMUM_RELEASE, null),
    )

    override fun save(record: FirmwareReleaseRecord) = preferences.edit {
        putString(FirmwareConstants.PREFERENCE_HIGHEST_RELEASE, record.highestVersion)
        putString(FirmwareConstants.PREFERENCE_MINIMUM_RELEASE, record.minimumVersion)
    }
}
