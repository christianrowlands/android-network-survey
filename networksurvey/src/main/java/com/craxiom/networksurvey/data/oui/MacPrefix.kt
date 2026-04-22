package com.craxiom.networksurvey.data.oui

/**
 * Utilities for parsing and manipulating MAC addresses used by the OUI lookup pipeline.
 *
 * A MAC is represented internally as a 48-bit value packed into a [Long] (top 16 bits zero).
 * Example: `AA:BB:CC:DD:EE:FF` → `0xAABBCCDDEEFFL`.
 *
 * Prefix encoding follows the server's wire contract: a 24-bit prefix keeps only the top 3
 * octets and zeros the rest (`0xAABBCC000000L`), a 28-bit prefix keeps 28 bits
 * (`0xAABBCCD00000L`), etc.
 */
internal object MacPrefix {

    /** Bit-length of an MA-L (24 bits), MA-M (28 bits), and MA-S (36 bits) prefix. */
    const val MA_L = 24
    const val MA_M = 28
    const val MA_S = 36

    private const val LAA_BIT = 0x02L
    private const val MULTICAST_BIT = 0x01L
    private const val MAC_MASK = 0xFFFFFFFFFFFFL

    /**
     * Parses a MAC string (colon, dash, dot, or bare hex) into a 48-bit Long. Returns null if
     * the input is not a 12-hex-digit MAC.
     */
    fun parse(mac: String): Long? {
        val hex = mac.replace(":", "").replace("-", "").replace(".", "")
        if (hex.length != 12) return null
        if (!hex.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) return null
        return hex.toLongOrNull(16)
    }

    /**
     * Normalizes a MAC to the canonical uppercase colon-grouped form `AA:BB:CC:DD:EE:FF`.
     * Returns null if the input cannot be parsed.
     */
    fun normalize(mac: String): String? {
        val parsed = parse(mac) ?: return null
        val hex = parsed.toString(16).padStart(12, '0').uppercase()
        return hex.chunked(2).joinToString(":")
    }

    /**
     * Masks a 48-bit MAC to a prefix of the given bit length (24/28/36). The returned value has
     * the non-prefix bits zeroed, same encoding used as the cache row key.
     */
    fun maskToPrefix(full48: Long, prefixLen: Int): Long {
        require(prefixLen in 0..48) { "prefixLen must be in [0, 48], got $prefixLen" }
        if (prefixLen == 48) return full48 and MAC_MASK
        val mask = (MAC_MASK shl (48 - prefixLen)) and MAC_MASK
        return full48 and mask
    }

    /**
     * Formats the 24-bit OUI prefix as an uppercase colon-grouped string (e.g. "AA:BB:CC").
     * This is what we send to the server, full MACs are never transmitted.
     */
    fun prefix24String(full48: Long): String {
        val prefix = (full48 ushr 24) and 0xFFFFFFL
        val hex = prefix.toString(16).padStart(6, '0').uppercase()
        return "${hex.substring(0, 2)}:${hex.substring(2, 4)}:${hex.substring(4, 6)}"
    }

    /** The Locally Administered bit (0x02) of the first octet. */
    fun isLocallyAdministered(full48: Long): Boolean {
        val firstByte = (full48 ushr 40) and 0xFFL
        return (firstByte and LAA_BIT) != 0L
    }

    /** The multicast bit (0x01) of the first octet. */
    fun isMulticast(full48: Long): Boolean {
        val firstByte = (full48 ushr 40) and 0xFFL
        return (firstByte and MULTICAST_BIT) != 0L
    }
}
