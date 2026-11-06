package com.craxiom.networksurvey.scout.raw

import java.io.ByteArrayOutputStream

/**
 * pcapng blocks, little endian (a port of the iOS app's `Pcapng`): a Section Header, one Interface
 * Description per Scout (link type 127, IEEE 802.11 with a radiotap header, named by the Scout ID)
 * and an Enhanced Packet per frame. Every file starts with a Section Header, and each Scout's
 * Interface Description comes before its first packet in that file.
 */
object Pcapng {
    /** LINKTYPE_IEEE802_11_RADIOTAP. */
    const val LINK_TYPE_RADIOTAP = 127

    const val SECTION_HEADER_BLOCK = 0x0A0D0D0A
    const val INTERFACE_DESCRIPTION_BLOCK = 1
    const val ENHANCED_PACKET_BLOCK = 6

    private const val BYTE_ORDER_MAGIC = 0x1A2B3C4D
    private const val OPTION_SHB_USER_APPLICATION = 4
    private const val OPTION_IF_NAME = 2
    private const val OPTION_IF_TSRESOL = 9

    /** if_tsresol 6: timestamps are in microseconds. */
    private const val MICROSECONDS = 6

    /** Block type, total length, and the total length again at the end. */
    private const val BLOCK_OVERHEAD = 12

    /** @return A Section Header naming [application] (shb_userappl), section length unknown. */
    fun sectionHeader(application: String): ByteArray {
        val body = LittleEndianBytes()
            .u32(BYTE_ORDER_MAGIC)
            .u16(1) // version 1.0
            .u16(0)
            .u64(-1L) // section length unknown
            .bytes(options(listOf(OPTION_SHB_USER_APPLICATION to application.toByteArray(Charsets.UTF_8))))
        return block(SECTION_HEADER_BLOCK, body.toByteArray())
    }

    /** @return An Interface Description named [name] (if_name), no snaplen limit, microsecond timestamps. */
    fun interfaceDescription(name: String): ByteArray {
        val body = LittleEndianBytes()
            .u16(LINK_TYPE_RADIOTAP)
            .u16(0)
            .u32(0) // snaplen: no limit
            .bytes(
                options(
                    listOf(
                        OPTION_IF_NAME to name.toByteArray(Charsets.UTF_8),
                        OPTION_IF_TSRESOL to byteArrayOf(MICROSECONDS.toByte()),
                    )
                )
            )
        return block(INTERFACE_DESCRIPTION_BLOCK, body.toByteArray())
    }

    /**
     * @param interfaceId    The packet's interface, in the order the file declared them.
     * @param timestampUs    Unix epoch microseconds.
     * @param packet         The radiotap header followed by the captured frame.
     * @param originalLength The packet's length on air (radiotap header plus the frame's `orig_len`).
     * @return An Enhanced Packet block.
     */
    fun enhancedPacket(interfaceId: Int, timestampUs: Long, packet: ByteArray, originalLength: Int): ByteArray {
        val micros = maxOf(0L, timestampUs)
        val body = LittleEndianBytes()
            .u32(interfaceId)
            .u32((micros ushr Int.SIZE_BITS).toInt())
            .u32(micros.toInt())
            .u32(packet.size)
            .u32(maxOf(originalLength, packet.size))
            .bytes(packet)
            .zeros(padding(packet.size))
        return block(ENHANCED_PACKET_BLOCK, body.toByteArray())
    }

    /** @return The zero bytes that bring [count] to a multiple of 4. */
    fun padding(count: Int): Int = (4 - count % 4) % 4

    private fun block(type: Int, body: ByteArray): ByteArray {
        val total = BLOCK_OVERHEAD + body.size
        return LittleEndianBytes().u32(type).u32(total).bytes(body).u32(total).toByteArray()
    }

    private fun options(list: List<Pair<Int, ByteArray>>): ByteArray {
        val out = LittleEndianBytes()
        list.forEach { (code, value) ->
            out.u16(code).u16(value.size).bytes(value).zeros(padding(value.size))
        }
        return out.u32(0).toByteArray() // opt_endofopt
    }
}

/** A growable little endian byte buffer for the pcapng and radiotap encoders. */
internal class LittleEndianBytes {
    private val out = ByteArrayOutputStream()

    val size: Int get() = out.size()

    fun u8(value: Int) = apply { out.write(value and BYTE_MASK) }

    fun u16(value: Int) = apply {
        u8(value)
        u8(value ushr Byte.SIZE_BITS)
    }

    fun u32(value: Int) = apply {
        u16(value)
        u16(value ushr Short.SIZE_BITS)
    }

    fun u64(value: Long) = apply {
        u32(value.toInt())
        u32((value ushr Int.SIZE_BITS).toInt())
    }

    fun bytes(value: ByteArray) = apply { out.write(value) }

    fun zeros(count: Int) = apply { repeat(count) { out.write(0) } }

    fun toByteArray(): ByteArray = out.toByteArray()

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
