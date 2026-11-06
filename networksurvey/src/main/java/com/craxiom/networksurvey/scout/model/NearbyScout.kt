package com.craxiom.networksurvey.scout.model

import com.craxiom.networksurvey.scout.link.ScoutAdvertisement
import com.craxiom.networksurvey.scout.link.ScoutCompatibility
import com.craxiom.networksurvey.scout.link.ScoutSighting

/**
 * What the pairing sheet says about a nearby Scout, in precedence order. The plan's row copy:
 * "Paired with this phone", "Was reset since you used it", "Paired with another phone. Hold its
 * BOOT button for 3 seconds to pair it here.", "Needs a firmware update", "Update Network Survey
 * to use NS-XXXX" and "Ready to pair". The UI phase owns the strings.
 */
enum class NearbyScoutStatus(
    /** True when tapping the row can pair (or reconnect) the Scout now. */
    val canPair: Boolean,
) {
    /** The Scout this phone already paired with. */
    PAIRED_HERE(canPair = true),

    /** Paired here before, but it advertises no bond now: it was erased or reflashed. */
    WAS_RESET(canPair = true),

    /** Bonded to another phone and no pairing window is open: hold BOOT for 3 seconds first. */
    PAIRED_WITH_ANOTHER_PHONE(canPair = false),

    /** Speaks an older protocol; it can pair, but needs a firmware update before it records. */
    NEEDS_FIRMWARE_UPDATE(canPair = true),

    /** Speaks a newer protocol than this app. */
    APP_UPDATE_NEEDED(canPair = false),

    /** No bonds yet, or its pairing window is open. */
    READY_TO_PAIR(canPair = true),
}

/** A distance word from the Scout's signal, so the row never shows dBm. */
enum class ScoutDistance {
    VERY_CLOSE,
    NEARBY,
    WEAK_SIGNAL;

    companion object {
        /** At or above this RSSI the Scout is "very close". */
        const val VERY_CLOSE_RSSI = -60

        /** At or above this RSSI the Scout is "nearby"; weaker is "weak signal". */
        const val NEARBY_RSSI = -75

        /** @return The word for [rssi], the same thresholds as the iOS app. */
        @JvmStatic
        fun of(rssi: Int): ScoutDistance = when {
            rssi >= VERY_CLOSE_RSSI -> VERY_CLOSE
            rssi >= NEARBY_RSSI -> NEARBY
            else -> WEAK_SIGNAL
        }
    }
}

/** A Scout heard by the pairing scan. */
data class NearbyScout(
    /** The Android Bluetooth address it advertised from. */
    val address: String,
    /** "ABCD" from "NS-ABCD". */
    val shortId: String,
    val rssi: Int,
    val advertisement: ScoutAdvertisement,
    /** True when this is the Scout this phone has paired. */
    val pairedHere: Boolean,
) {
    /** "NS-ABCD". */
    val name: String get() = ScoutAdvertisement.nameForShortId(shortId)

    /** How close it is, in words. */
    val distance: ScoutDistance get() = ScoutDistance.of(rssi)

    /** What the row says. */
    val status: NearbyScoutStatus
        get() {
            val compatibility = ScoutCompatibility.of(advertisement.protocolMajor)
            return when {
                pairedHere && !advertisement.hasBond -> NearbyScoutStatus.WAS_RESET
                pairedHere -> NearbyScoutStatus.PAIRED_HERE
                advertisement.hasBond && !advertisement.pairable -> NearbyScoutStatus.PAIRED_WITH_ANOTHER_PHONE
                compatibility == ScoutCompatibility.NEEDS_FIRMWARE_UPDATE -> NearbyScoutStatus.NEEDS_FIRMWARE_UPDATE
                compatibility == ScoutCompatibility.NEEDS_APP_UPDATE -> NearbyScoutStatus.APP_UPDATE_NEEDED
                else -> NearbyScoutStatus.READY_TO_PAIR
            }
        }

    companion object {
        /** @return The row for [sighting]; [paired] (the paired Scout, if any) decides [pairedHere]. */
        @JvmStatic
        fun from(sighting: ScoutSighting, paired: PairedScout?): NearbyScout = NearbyScout(
            sighting.address, sighting.shortId, sighting.rssi, sighting.advertisement,
            pairedHere = paired != null && paired.address == sighting.address,
        )
    }
}
