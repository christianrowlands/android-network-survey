package com.craxiom.networksurvey.scout.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The framing and loss math of protocol.md section 4, independent of the fixtures. */
class ScoutFramingTest {

    @Test
    fun lostFrames_countsTheGapBetweenTwoSequences() {
        assertEquals(0, ScoutFraming.lostFrames(5, 6))
        assertEquals(3, ScoutFraming.lostFrames(5, 9))
    }

    @Test
    fun lostFrames_wrapsFrom65535To0WithoutLoss() {
        assertEquals(0, ScoutFraming.lostFrames(65535, 0))
        assertEquals(2, ScoutFraming.lostFrames(65534, 1))
    }

    @Test
    fun lostFrames_treats32768OrMoreAsACounterReset() {
        // The board restarted its counter (a new subscription) or repeated a frame.
        assertEquals(0, ScoutFraming.lostFrames(100, 0))
        assertEquals(0, ScoutFraming.lostFrames(7, 7))
        assertEquals(0, ScoutFraming.lostFrames(0, 32769))
        assertEquals(32767, ScoutFraming.lostFrames(0, 32768))
    }

    @Test
    fun encode_thenDecode_keepsSequenceAndMessages() {
        val small = byteArrayOf(1, 2, 3)
        val large = ByteArray(300) { it.toByte() }
        val frame = ScoutFraming.decode(ScoutFraming.encode(65537, listOf(small, large)))

        assertEquals(1, frame.sequence)
        assertEquals(2, frame.messages.size)
        assertArrayEquals(small, frame.messages[0])
        assertArrayEquals(large, frame.messages[1])
    }

    @Test
    fun encode_writesTheSequenceLittleEndianAndATwoByteVarint() {
        val bytes = ScoutFraming.encode(0x0102, listOf(ByteArray(200)))
        assertEquals(0x02.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0xC8.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
        assertEquals(2 + 2 + 200, bytes.size)
    }

    @Test
    fun decode_aHeaderOnlyFrameHasNoMessages() {
        assertEquals(0, ScoutFraming.decode(byteArrayOf(9, 0)).messages.size)
    }

    @Test(expected = ScoutFramingException::class)
    fun decode_rejectsAFrameShorterThanItsHeader() {
        ScoutFraming.decode(byteArrayOf(1))
    }

    @Test(expected = ScoutFramingException::class)
    fun decode_rejectsAMessageThatRunsPastTheFrame() {
        ScoutFraming.decode(byteArrayOf(0, 0, 5, 1, 2))
    }

    @Test(expected = ScoutFramingException::class)
    fun decode_rejectsAnUnfinishedVarint() {
        ScoutFraming.decode(byteArrayOf(0, 0, 0x80.toByte()))
    }

    @Test
    fun gapDetector_findsMissedFramesAndResets() {
        val detector = SequenceGapDetector()
        assertEquals(0, detector.observe(10))
        assertEquals(2, detector.observe(13))
        assertEquals(0, detector.observe(14))

        detector.reset()
        assertNull(detector.lastSequence)
        assertEquals(0, detector.observe(500))
    }
}
