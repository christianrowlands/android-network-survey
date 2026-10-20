package com.craxiom.networksurvey.ui.bluetooth.list.model

/**
 * What sent a Find My advertisement, by the device type bits of its status byte.
 *
 * @property deviceTypeBits The value of the status byte's bits 4 and 5 that means this kind.
 * @property isTracker True when the list tags the device as a tracker.
 */
enum class FindMyDevice(val deviceTypeBits: Int, val isTracker: Boolean) {
    /** An iPhone, iPad, Mac or Apple Watch. */
    APPLE_DEVICE(0, false),

    /** A first-generation AirTag. */
    AIRTAG(1, true),

    /**
     * A third-party Find My tracker or accessory, a second-generation AirTag or some AirPods. They
     * can only be told apart by connecting to the device, which a passive survey does not do.
     */
    FIND_MY_ACCESSORY(2, true),

    /** AirPods. */
    AIRPODS(3, false),
}

/**
 * Reads Apple Find My advertisements from a record's manufacturer data.
 *
 * Every Find My device (iPhones, Macs, AirPods, AirTags and third-party Find My accessories) sends
 * Apple's company ID with a payload of type 0x12, so the type alone does not make a tracker. The
 * payload is the type, a length and then a status byte whose bits 4 and 5 say what sent it (see
 * [FindMyDevice]), the same rule AirGuard uses before it connects to a device. Type 0x07
 * (Proximity Pairing) comes from AirPods and other accessories, so it is not used.
 *
 * From Android 13 the survey reads the raw advertisement, so the record's manufacturer data starts
 * with the company ID's two bytes (little-endian, "4c00" for Apple) before the payload; before
 * Android 13 it holds the payload alone. Both forms are accepted.
 */
object FindMy {
    private const val APPLE_COMPANY_ID = 0x004C
    private const val APPLE_COMPANY_ID_LITTLE_ENDIAN = "4c00"
    private const val HEX_RADIX = 16
    private const val HEX_CHARS_PER_BYTE = 2
    private const val FIND_MY_TYPE = 0x12
    private const val STATUS_BYTE_INDEX = 2
    private const val DEVICE_TYPE_MASK = 0x30
    private const val DEVICE_TYPE_SHIFT = 4

    /**
     * @param companyIdHex The company ID as the record carries it: unpadded hex ("4c").
     * @param manufacturerDataHex The manufacturer data as a hex string.
     * @return What sent the Find My advertisement, or null when the data is not one.
     */
    fun device(companyIdHex: String, manufacturerDataHex: String): FindMyDevice? {
        if (companyIdHex.toIntOrNull(HEX_RADIX) != APPLE_COMPANY_ID) return null
        val payload = manufacturerDataHex.lowercase().removePrefix(APPLE_COMPANY_ID_LITTLE_ENDIAN)
        if (byteAt(payload, 0) != FIND_MY_TYPE) return null
        val status = byteAt(payload, STATUS_BYTE_INDEX) ?: return null
        val bits = (status and DEVICE_TYPE_MASK) shr DEVICE_TYPE_SHIFT
        return FindMyDevice.entries.first { it.deviceTypeBits == bits }
    }

    /** @return The byte at [index] of [hex], or null when the hex is too short or not hex. */
    private fun byteAt(hex: String, index: Int): Int? {
        val start = index * HEX_CHARS_PER_BYTE
        if (hex.length < start + HEX_CHARS_PER_BYTE) return null
        val pair = hex.substring(start, start + HEX_CHARS_PER_BYTE)
        if (!pair.all { Character.digit(it, HEX_RADIX) >= 0 }) return null
        return pair.toInt(HEX_RADIX)
    }
}
