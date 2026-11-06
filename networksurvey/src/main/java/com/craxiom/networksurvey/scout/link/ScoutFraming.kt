package com.craxiom.networksurvey.scout.link

import java.io.ByteArrayOutputStream

/** One notification or indication from a Scout: its sequence number and the messages it carried. */
class ScoutFrame(
    /** The frame's sequence number, 0 to 65535. */
    val sequence: Int,
    /** The protobuf messages in the frame, in order, without their length prefixes. */
    val messages: List<ByteArray>,
)

/** A notification that is not a valid frame (too short, a bad length varint or a cut message). */
class ScoutFramingException(message: String) : Exception(message)

/**
 * The framing on every Scout notify and indicate characteristic (protocol.md section 4): a
 * little-endian u16 sequence number followed by one or more protobuf messages, each preceded by
 * its length as a base-128 varint. A message never spans frames.
 */
object ScoutFraming {
    /** Bytes of the sequence number at the start of every frame. */
    const val HEADER_BYTES = 2

    /** Sequence numbers wrap from 65535 to 0. */
    const val SEQUENCE_MODULUS = 65536

    /** A gap this large or larger is a counter restart (or a duplicate), not loss. */
    const val COUNTER_RESET_GAP = 32768

    private const val BYTE_MASK = 0xFF
    private const val VARINT_PAYLOAD_MASK = 0x7F
    private const val VARINT_CONTINUE = 0x80
    private const val VARINT_SHIFT = 7
    private const val MAX_VARINT_SHIFT = 35
    private const val BITS_PER_BYTE = 8

    /**
     * Splits one notification into its sequence number and messages.
     *
     * @throws ScoutFramingException When the bytes are not a valid frame.
     */
    @JvmStatic
    fun decode(notification: ByteArray): ScoutFrame {
        if (notification.size < HEADER_BYTES) throw ScoutFramingException("frame shorter than its header")
        val sequence = (notification[0].toInt() and BYTE_MASK) or
            ((notification[1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)
        val messages = ArrayList<ByteArray>()
        var offset = HEADER_BYTES
        while (offset < notification.size) {
            val start = offset
            var length = 0L
            var shift = 0
            while (true) {
                if (offset >= notification.size || shift >= MAX_VARINT_SHIFT) {
                    throw ScoutFramingException("malformed length at offset $start")
                }
                val byte = notification[offset++].toInt() and BYTE_MASK
                length = length or ((byte and VARINT_PAYLOAD_MASK).toLong() shl shift)
                if (byte and VARINT_CONTINUE == 0) break
                shift += VARINT_SHIFT
            }
            if (length > notification.size - offset) {
                throw ScoutFramingException("message of $length bytes at offset $start runs past the frame")
            }
            messages.add(notification.copyOfRange(offset, offset + length.toInt()))
            offset += length.toInt()
        }
        return ScoutFrame(sequence, messages)
    }

    /** @return One frame with [sequence] (taken mod 65536) holding [messages]; the fake Scout and tests use it. */
    @JvmStatic
    fun encode(sequence: Int, messages: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        val wrapped = Math.floorMod(sequence, SEQUENCE_MODULUS)
        out.write(wrapped and BYTE_MASK)
        out.write(wrapped shr BITS_PER_BYTE)
        for (message in messages) {
            var rest = message.size
            while (rest >= VARINT_CONTINUE) {
                out.write((rest and VARINT_PAYLOAD_MASK) or VARINT_CONTINUE)
                rest = rest ushr VARINT_SHIFT
            }
            out.write(rest)
            out.write(message)
        }
        return out.toByteArray()
    }

    /**
     * @return The frames lost between a received sequence [previous] and the next received
     * [current]: `(current - previous - 1) mod 65536`, or 0 when that is 32768 or more, which means
     * the board restarted its counter or repeated a frame.
     */
    @JvmStatic
    fun lostFrames(previous: Int, current: Int): Int {
        val gap = Math.floorMod(current - previous - 1, SEQUENCE_MODULUS)
        return if (gap >= COUNTER_RESET_GAP) 0 else gap
    }
}

/**
 * Tracks the sequence numbers of one characteristic to find missed frames. Reset it whenever
 * the phone (re)subscribes, because the board restarts that characteristic's counter then.
 */
class SequenceGapDetector {
    /** The last sequence number seen, null before the first frame. */
    var lastSequence: Int? = null
        private set

    /** Records [sequence] and returns how many frames were missed just before it. */
    fun observe(sequence: Int): Int {
        val last = lastSequence
        lastSequence = sequence
        return if (last == null) 0 else ScoutFraming.lostFrames(last, sequence)
    }

    /** Forgets the last sequence, for a new subscription. */
    fun reset() {
        lastSequence = null
    }
}
