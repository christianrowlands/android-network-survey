package com.craxiom.networksurvey.scout.model

import java.util.Locale

/**
 * Identifies a Scout: `NS-` plus its BLE identity address in uppercase hex (`NS-02005E00ABCD`),
 * the same on every phone and after reinstalls (it matches the iOS app's board ID), so it can go
 * into files (the pcapng interface name). Every Scout model and store is keyed by it,
 * so more than one Scout can be supported later without a data change.
 */
data class ScoutId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Every Scout ID starts with this. */
        const val PREFIX = "NS-"

        private const val ADDRESS_BYTES = 6
        private const val BYTE_MASK = 0xFF
        private const val ADDRESS_SEPARATOR = ":"
        private const val HEX_BYTE_FORMAT = "%02X"

        /** @return The ID for the 6-byte `BoardInfo.bt_address`, or null for any other length. */
        @JvmStatic
        fun fromBtAddress(address: ByteArray): ScoutId? {
            if (address.size != ADDRESS_BYTES) return null
            return ScoutId(PREFIX + address.joinToString("") { String.format(Locale.US, HEX_BYTE_FORMAT, it.toInt() and BYTE_MASK) })
        }

        /**
         * @return The ID for an Android address ("02:00:5E:00:AB:CD"), or null when it is not one.
         * Android reports the identity address once a Scout is bonded, so this matches
         * [fromBtAddress] for the same board.
         */
        @JvmStatic
        fun fromAddress(address: String): ScoutId? {
            val parts = address.split(ADDRESS_SEPARATOR)
            if (parts.size != ADDRESS_BYTES || parts.any { it.length != 2 || it.toIntOrNull(HEX_RADIX) == null }) return null
            return ScoutId(PREFIX + parts.joinToString("").uppercase(Locale.US))
        }

        /** @return The short ID ("ABCD"): the last two bytes of an Android address in uppercase hex. */
        @JvmStatic
        fun shortIdFromAddress(address: String): String =
            address.split(ADDRESS_SEPARATOR).takeLast(2).joinToString("").uppercase(Locale.US)

        private const val HEX_RADIX = 16
    }
}
