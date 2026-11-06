package com.craxiom.networksurvey.scout.link

import java.util.UUID

/**
 * The GATT layout and fixed limits of the NS Board Service (network-survey-esp32 docs/protocol.md
 * sections 2, 4 and 12). Every UUID shares one random base; only the second 16-bit group changes.
 */
object ScoutGatt {
    private const val UUID_PREFIX = "72AA"
    private const val UUID_SUFFIX = "-0D8E-4D18-8877-63C24ABE28D2"
    private const val SERVICE_GROUP = "0001"

    /** The primary service every Scout advertises and that the pairing scan filters on. */
    @JvmField
    val SERVICE_UUID: UUID = uuid(SERVICE_GROUP)

    /** The standard Client Characteristic Configuration Descriptor. */
    @JvmField
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** The ATT MTU the app asks for first; the board never sends Records below [MIN_RECORDS_MTU]. */
    const val PREFERRED_MTU = 517

    /** The smallest negotiated ATT MTU a board supports for Records. */
    const val MIN_RECORDS_MTU = 185

    /** Bytes of the ATT notification header; the usable payload is `MTU - 3`. */
    const val ATT_HEADER_BYTES = 3

    /**
     * The longest attribute value ATT allows. Android's `writeCharacteristic` throws for anything
     * longer, whatever the MTU, so an MTU of 517 still carries at most 512 bytes per write.
     */
    const val MAX_ATTRIBUTE_BYTES = 512

    /** @return The most bytes one write may carry on a link with ATT [mtu]. */
    @JvmStatic
    fun maxWriteBytes(mtu: Int): Int = minOf(mtu - ATT_HEADER_BYTES, MAX_ATTRIBUTE_BYTES)

    /** The largest `ControlRequest` write a board accepts. */
    const val CONTROL_REQUEST_MAX_BYTES = 512

    /** The longest `Hello.app_version` a board accepts, in UTF-8 bytes. */
    const val APP_VERSION_MAX_BYTES = 31

    /** @return The UUID of the board service base with the given second 16-bit group. */
    internal fun uuid(group: String): UUID = UUID.fromString(UUID_PREFIX + group + UUID_SUFFIX)
}

/**
 * The characteristics of the NS Board Service. Everything except [INFO] needs a link encrypted
 * with a stored bond. [CONTROL] answers by indication; the others notify.
 */
enum class ScoutCharacteristic(group: String, val indicates: Boolean) {
    INFO("0002", indicates = false),
    CONTROL("0003", indicates = true),
    STATUS("0004", indicates = false),
    RECORDS("0005", indicates = false),
    RAW("0006", indicates = false),
    OTA("0007", indicates = false);

    /** The characteristic UUID. */
    val uuid: UUID = ScoutGatt.uuid(group)

    companion object {
        /** @return The characteristic with [uuid], or null for anything outside the board service. */
        @JvmStatic
        fun fromUuid(uuid: UUID): ScoutCharacteristic? = entries.firstOrNull { it.uuid == uuid }
    }
}
