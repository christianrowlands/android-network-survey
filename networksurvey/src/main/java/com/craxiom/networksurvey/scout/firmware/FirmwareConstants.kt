package com.craxiom.networksurvey.scout.firmware

/** Preference keys, URLs, timings and IDs for the NS Scout firmware update. */
object FirmwareConstants {
    /** The signed manifest of the latest full firmware release, a release asset in the firmware repository. */
    const val MANIFEST_URL =
        "https://github.com/christianrowlands/network-survey-esp32/releases/latest/download/firmware-manifest.json"

    /** Appended to the manifest URL's path for its signature file. */
    const val SIGNATURE_SUFFIX = ".sig"

    /** The highest release version this phone verified. */
    const val PREFERENCE_HIGHEST_RELEASE = "scout_firmware_highest_release"

    /** The highest `min_version` this phone verified. */
    const val PREFERENCE_MINIMUM_RELEASE = "scout_firmware_minimum_release"

    /** The last verified manifest, base64, for the background "Update available" check. */
    const val PREFERENCE_CACHED_MANIFEST = "scout_firmware_cached_manifest"

    /** The cached manifest's signature, base64. */
    const val PREFERENCE_CACHED_SIGNATURE = "scout_firmware_cached_signature"

    /** When the background check last fetched the manifest, Unix epoch milliseconds. */
    const val PREFERENCE_CHECKED_AT = "scout_firmware_checked_at"

    /** After a successful fetch, the background check waits this long before the next one. */
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** The ongoing notification while firmware installs. */
    const val NOTIFICATION_ID = 110

    /** The partial wake lock held while firmware installs. */
    const val WAKE_LOCK_TAG = "networksurvey:scout_firmware_update"

    /** Longer than any install takes; only a stuck service ever reaches it. */
    const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L
}
