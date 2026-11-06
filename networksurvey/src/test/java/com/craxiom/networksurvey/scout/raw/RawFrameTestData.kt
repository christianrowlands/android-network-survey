package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.FramePhy
import com.craxiom.networksurvey.board.v1.RawFrame
import com.craxiom.networksurvey.scout.model.ScoutId
import com.google.protobuf.ByteString
import java.io.File

/** The iOS app's pcapng test frames, so both apps are checked against the same bytes. */
object RawFrameTestData {
    val SCOUT_A = ScoutId("NS-1A2B")
    val SCOUT_B = ScoutId("NS-3C4D")

    /** A beacon for SSID ExampleNet from BSSID 02:00:5e:00:00:01. */
    val BEACON: ByteArray = intArrayOf(
        0x80, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x02, 0x00, 0x5E, 0x00, 0x00, 0x01,
        0x02, 0x00, 0x5E, 0x00, 0x00, 0x01, 0x00, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0x64, 0x00, 0x01, 0x04,
        0x00, 0x0A, 0x45, 0x78, 0x61, 0x6D, 0x70, 0x6C, 0x65, 0x4E, 0x65, 0x74,
        0x01, 0x04, 0x82, 0x84, 0x8B, 0x96, 0x03, 0x01, 0x06,
    ).map { it.toByte() }.toByteArray()

    /** tshark prints the SSID field as hex: "ExampleNet". */
    const val SSID_HEX = "4578616d706c654e6574"

    /** Channel 6, 1 Mb/s legacy, with a noise floor. */
    val LEGACY: RawFrame = RawFrame.newBuilder()
        .setChannel(6)
        .setFrequencyMhz(2437)
        .setBand(Band.BAND_GHZ_2_4)
        .setRssi(-52)
        .setNoiseFloorDbm(-95)
        .setPhy(FramePhy.FRAME_PHY_LEGACY)
        .setRate500Kbps(2)
        .setOrigLen(BEACON.size)
        .setData(ByteString.copyFrom(BEACON))
        .build()

    /** Channel 36, HT MCS 7, with the FCS kept, cut 100 bytes short of its length on air. */
    val HT: RawFrame = run {
        val withFcs = BEACON + crc32(BEACON).let { crc -> ByteArray(4) { i -> (crc ushr (8 * i)).toByte() } }
        RawFrame.newBuilder()
            .setChannel(36)
            .setFrequencyMhz(5180)
            .setBand(Band.BAND_GHZ_5)
            .setRssi(-60)
            .setPhy(FramePhy.FRAME_PHY_HT)
            .setMcs(7)
            .setFcsIncluded(true)
            .setOrigLen(withFcs.size + 100)
            .setData(ByteString.copyFrom(withFcs))
            .build()
    }

    /** The iOS test's three frames: two Scouts, the first heard twice. */
    val FRAMES: List<StampedRawFrame> = listOf(
        StampedRawFrame(SCOUT_A, 1_792_000_987_123_456L, LEGACY),
        StampedRawFrame(SCOUT_B, 1_792_000_988_500_000L, HT),
        StampedRawFrame(SCOUT_A, 1_792_000_989_000_000L, LEGACY),
    )

    fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1 }
        }
        return crc.inv()
    }

    /** The pcapng blocks of [bytes]: (type, offset, length) each. */
    fun blocks(bytes: ByteArray): List<Triple<Int, Int, Int>> {
        val out = mutableListOf<Triple<Int, Int, Int>>()
        var offset = 0
        while (offset < bytes.size) {
            val length = u32(bytes, offset + 4)
            out += Triple(u32(bytes, offset), offset, length)
            offset += length
        }
        return out
    }

    fun u32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)

    /** Wireshark's command line tools, when installed on this computer. */
    fun tool(name: String): File? = listOf(
        "/Applications/Wireshark.app/Contents/MacOS/$name", "/opt/homebrew/bin/$name", "/usr/local/bin/$name", "/usr/bin/$name",
    ).map(::File).firstOrNull { it.canExecute() }

    fun run(tool: File, vararg arguments: String): String {
        val process = ProcessBuilder(listOf(tool.absolutePath) + arguments).redirectErrorStream(false).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return output
    }
}
