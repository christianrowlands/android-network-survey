package com.craxiom.networksurvey.scout.raw

import com.craxiom.networksurvey.board.v1.Band
import com.craxiom.networksurvey.board.v1.FramePhy
import com.craxiom.networksurvey.board.v1.RawFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The radiotap header and pcapng blocks, byte for byte against the iOS app's tests. */
class PcapngTest {

    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun `radiotap fields are aligned and flagged as on iOS`() {
        // v0, len 16, present Flags|Rate|Channel|Signal|Noise, flags, rate, pad, channel, signal, noise
        assertArrayEquals(
            bytes(0, 0, 16, 0, 0x6E, 0, 0, 0, 0x00, 0x02, 0x85, 0x09, 0xA0, 0x00, 0xCC, 0xA1),
            Radiotap.header(RawFrameTestData.LEGACY),
        )
        // Flags (FCS), pad, channel 5180 OFDM 5 GHz, signal, MCS known=index, flags, 7
        assertArrayEquals(
            bytes(0, 0, 18, 0, 0x2A, 0, 0x08, 0, 0x10, 0x00, 0x3C, 0x14, 0x40, 0x01, 0xC4, 0x02, 0x00, 0x07),
            Radiotap.header(RawFrameTestData.HT),
        )
    }

    @Test
    fun `radiotap without a frequency or rate keeps only flags and signal, and clamps the signal`() {
        val bare = RawFrame.newBuilder().setRssi(-200).setPhy(FramePhy.FRAME_PHY_VHT).build()
        assertArrayEquals(bytes(0, 0, 10, 0, 0x22, 0, 0, 0, 0x00, 0x80), Radiotap.header(bare))
    }

    @Test
    fun `an unbanded frequency above 5 GHz is flagged 5 GHz OFDM, and a CCK rate at 2_4 GHz is CCK`() {
        val unbanded = RawFrame.newBuilder().setFrequencyMhz(5745).setBand(Band.BAND_UNSPECIFIED).setRssi(-70).build()
        val header = Radiotap.header(unbanded)
        // Flags at 8, a pad byte, the frequency at 10, then the channel flags at 12
        assertEquals(5745, (header[10].toInt() and 0xFF) or ((header[11].toInt() and 0xFF) shl 8))
        assertEquals(0x0140, (header[12].toInt() and 0xFF) or ((header[13].toInt() and 0xFF) shl 8))

        val cck = RawFrameTestData.LEGACY.toBuilder().setRate500Kbps(22).build()
        val cckHeader = Radiotap.header(cck)
        assertEquals(0x00A0, (cckHeader[12].toInt() and 0xFF) or ((cckHeader[13].toInt() and 0xFF) shl 8))
    }

    @Test
    fun `a section header names the app and leaves the section length unknown`() {
        val block = Pcapng.sectionHeader("Network Survey Android 2.0")
        assertEquals(Pcapng.SECTION_HEADER_BLOCK, RawFrameTestData.u32(block, 0))
        assertEquals(block.size, RawFrameTestData.u32(block, 4))
        assertEquals(block.size, RawFrameTestData.u32(block, block.size - 4))
        assertEquals(0x1A2B3C4D, RawFrameTestData.u32(block, 8))
        assertEquals(0, block.size % 4)
        // Section length -1, then shb_userappl (4) with the name
        assertEquals(-1, RawFrameTestData.u32(block, 16))
        assertEquals(4, block[24].toInt())
        assertEquals("Network Survey Android 2.0", String(block, 28, block[26].toInt(), Charsets.UTF_8))
    }

    @Test
    fun `an interface is radiotap, unlimited, named and in microseconds`() {
        val block = Pcapng.interfaceDescription("NS-02000000A1B2")
        assertEquals(Pcapng.INTERFACE_DESCRIPTION_BLOCK, RawFrameTestData.u32(block, 0))
        assertEquals(127, block[8].toInt())
        assertEquals(0, RawFrameTestData.u32(block, 12))
        assertEquals(2, block[16].toInt())
        assertEquals("NS-02000000A1B2", String(block, 20, block[18].toInt(), Charsets.UTF_8))
        // if_tsresol 6 after the 15 name bytes and one pad byte
        assertEquals(9, block[36].toInt())
        assertEquals(6, block[40].toInt())
        assertEquals(0, block.size % 4)
    }

    @Test
    fun `an enhanced packet splits the microseconds and pads the packet`() {
        val packet = ByteArray(5) { it.toByte() }
        val block = Pcapng.enhancedPacket(interfaceId = 1, timestampUs = 1_792_000_987_123_456L, packet = packet, originalLength = 3)
        assertEquals(Pcapng.ENHANCED_PACKET_BLOCK, RawFrameTestData.u32(block, 0))
        assertEquals(1, RawFrameTestData.u32(block, 8))
        val micros = (RawFrameTestData.u32(block, 12).toLong() shl 32) or (RawFrameTestData.u32(block, 16).toLong() and 0xFFFFFFFFL)
        assertEquals(1_792_000_987_123_456L, micros)
        assertEquals(5, RawFrameTestData.u32(block, 20))
        // The original length is never less than what was captured
        assertEquals(5, RawFrameTestData.u32(block, 24))
        assertEquals(12 + 20 + 8, block.size)
        assertEquals(3, Pcapng.padding(5))
        assertEquals(0, Pcapng.padding(8))
    }
}
