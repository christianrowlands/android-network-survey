package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.OtaAckStatus
import com.craxiom.networksurvey.board.v1.OtaBeginResult
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Why a transfer stopped short of the last byte. */
class OtaTransferException(val kind: Kind, message: String, val status: OtaAckStatus? = null) : Exception(message) {
    enum class Kind {
        /** The board's OtaBegin answer leaves no room for a chunk, or starts past the end. */
        BAD_PARAMETERS,

        /** The board stopped acknowledging. */
        STALLED,

        /** WRITE_FAILED, OVERFLOW or NOT_ACTIVE: start again with OtaBegin. */
        BOARD_ABORTED,
    }
}

/**
 * Sends a firmware image after a successful OtaBegin (protocol.md section 10): chunks of
 * `offset (u32 LE) + bytes` written without response, at most `window_bytes` unacknowledged,
 * resending from the last acknowledged offset on OUT_OF_ORDER or after [ackTimeoutMs] of silence.
 *
 * Chunk boundaries never move: every chunk starts at `start_offset` plus a whole number of chunks,
 * and the window is rounded down to whole chunks. So a resend repeats the same chunks, and the
 * board answers a duplicate that ends at its `next_offset` with an ack when the phone lost the last
 * one. A port of the iOS app's `OtaChunkSender`.
 */
class OtaChunkSender(
    private val link: ScoutOtaLink,
    private val image: ByteArray,
    private val parameters: OtaBeginResult,
    private val ackTimeoutMs: Long = DEFAULT_ACK_TIMEOUT_MS,
    /** Resends in a row without progress (after silence or OUT_OF_ORDER) before giving up. */
    private val maxSilentResends: Int = DEFAULT_MAX_SILENT_RESENDS,
) {
    /**
     * Runs the transfer and returns once the board acknowledged every byte. [progress] gets the
     * acknowledged byte count.
     *
     * @throws OtaTransferException When the board stalls or ends the transfer.
     * @throws com.craxiom.networksurvey.scout.link.ScoutLinkException When the link ends.
     */
    suspend fun run(progress: (Int) -> Unit = {}) {
        val total = image.size
        val first = Integer.toUnsignedLong(parameters.startOffset)
        if (first > total) throw OtaTransferException(OtaTransferException.Kind.BAD_PARAMETERS, "start offset past the image end")
        val (chunkSize, window) = layout(
            Integer.toUnsignedLong(parameters.maxChunkSize), Integer.toUnsignedLong(parameters.windowBytes), link.maxWriteBytes,
        )
        if (chunkSize <= 0) throw OtaTransferException(OtaTransferException.Kind.BAD_PARAMETERS, "no room for image bytes in a write")

        var acked = first.toInt()
        var sent = acked
        var stalls = 0
        progress(acked)
        while (acked < total) {
            while (sent < total) {
                val end = minOf(sent + chunkSize, total)
                if (end - acked > window) break
                coroutineContext.ensureActive()
                link.writeOta(chunk(image, sent, end))
                sent = end
            }
            val ack = link.nextAck(ackTimeoutMs)
            if (ack == null) {
                stalls = countStall(stalls, acked)
                sent = acked
                continue
            }
            // The board only accepts whole chunks, so next_offset is on the chunk grid
            val next = minOf(Integer.toUnsignedLong(ack.nextOffset), total.toLong()).toInt()
            when (ack.status) {
                OtaAckStatus.OTA_ACK_STATUS_OK, OtaAckStatus.OTA_ACK_STATUS_OUT_OF_ORDER -> {
                    val outOfOrder = ack.status == OtaAckStatus.OTA_ACK_STATUS_OUT_OF_ORDER
                    if (next > acked) {
                        acked = next
                        stalls = 0
                        progress(acked)
                    } else if (outOfOrder) {
                        stalls = countStall(stalls, acked)
                    }
                    sent = if (outOfOrder) acked else maxOf(sent, acked)
                }

                else -> throw OtaTransferException(OtaTransferException.Kind.BOARD_ABORTED, "board ended the transfer: ${ack.status}", ack.status)
            }
        }
    }

    private fun countStall(stalls: Int, acked: Int): Int {
        if (stalls + 1 > maxSilentResends) throw OtaTransferException(OtaTransferException.Kind.STALLED, "no progress past $acked")
        return stalls + 1
    }

    companion object {
        /** The board answers at least every `window_bytes / 2`; silence this long means resend. */
        const val DEFAULT_ACK_TIMEOUT_MS = 5_000L
        const val DEFAULT_MAX_SILENT_RESENDS = 4

        /** The 4-byte little-endian offset in front of every chunk. */
        const val OFFSET_BYTES = 4
        private const val BYTE_MASK = 0xFF
        private const val BITS_PER_BYTE = 8

        /**
         * @return The chunk size and window for a write limit: the chunk never exceeds the board's
         * window, and the window is a whole number of chunks.
         */
        @JvmStatic
        fun layout(maxChunkSize: Long, windowBytes: Long, writeLimit: Int): Pair<Int, Int> {
            var chunk = minOf(maxChunkSize, (writeLimit - OFFSET_BYTES).toLong())
            if (windowBytes > 0) chunk = minOf(chunk, windowBytes)
            if (chunk <= 0) return 0 to 0
            val window = maxOf(windowBytes / chunk, 1L) * chunk
            return chunk.toInt() to window.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        /** @return The OTA write for `image[start until end]`: the offset, then the bytes. */
        @JvmStatic
        fun chunk(image: ByteArray, start: Int, end: Int): ByteArray {
            val out = ByteArray(OFFSET_BYTES + end - start)
            for (i in 0 until OFFSET_BYTES) out[i] = ((start ushr (BITS_PER_BYTE * i)) and BYTE_MASK).toByte()
            System.arraycopy(image, start, out, OFFSET_BYTES, end - start)
            return out
        }
    }
}
