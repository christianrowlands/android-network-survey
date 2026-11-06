package com.craxiom.networksurvey.scout.model

/**
 * Where Wi-Fi comes from: a paired Scout or this phone, never both. The choice is explicit and
 * persisted; the Scout link flickering never changes it, only the Scout's status word.
 */
enum class WifiSource(
    /** The value saved in preferences; never change it once shipped. */
    val preferenceValue: String,
) {
    /** The paired Scout is the Wi-Fi radio; the phone's Wi-Fi scanner never runs. */
    SCOUT("scout"),

    /** The phone's own Wi-Fi scanner, as without a Scout. */
    PHONE("phone");

    companion object {
        /** @return The source saved as [value], or [PHONE] for anything unknown. */
        @JvmStatic
        fun fromPreference(value: String?): WifiSource = entries.firstOrNull { it.preferenceValue == value } ?: PHONE
    }
}
