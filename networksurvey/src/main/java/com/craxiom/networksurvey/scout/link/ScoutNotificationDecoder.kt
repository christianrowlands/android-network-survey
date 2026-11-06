package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.board.v1.ControlResponse
import com.craxiom.networksurvey.board.v1.OtaAck
import com.craxiom.networksurvey.board.v1.RawFrame
import com.craxiom.networksurvey.board.v1.RecordBatch

/** One `RecordBatch` as it arrived, with what the framing layer saw. */
class ReceivedRecordBatch(
    /** Sequence number of the notification that carried the batch. */
    val sequence: Int,
    /** Records notifications lost just before this one (only set on a frame's first batch). */
    val missedNotifications: Int,
    val batch: RecordBatch,
    /** Phone wall clock time when the notification arrived, Unix epoch milliseconds. */
    val receivedAtMs: Long,
)

/** The raw 802.11 frames of one Raw notification. */
class ReceivedRawFrames(
    val sequence: Int,
    /** Raw notifications lost just before this one. */
    val missedNotifications: Int,
    val frames: List<RawFrame>,
    /** Phone wall clock time when the notification arrived, Unix epoch milliseconds. */
    val receivedAtMs: Long,
)

/**
 * Turns framed notifications and indications from a Scout into protobuf messages and tracks the
 * sequence gaps of each characteristic. Every transport (the real GATT link and the debug fake)
 * goes through this one decoder, so the fake exercises the same path as hardware.
 *
 * Not thread safe: the Scout thread owns it. Call [reset] for a characteristic whenever the phone
 * subscribes to it, since the board restarts that characteristic's sequence numbers then.
 *
 * Every decode method throws [ScoutFramingException] for a bad frame and
 * `InvalidProtocolBufferException` for a message that does not parse.
 */
class ScoutNotificationDecoder {
    private val detectors = ScoutCharacteristic.entries.associateWith { SequenceGapDetector() }

    /** @return The gap detector of [characteristic], for its last sequence and missed total. */
    fun detector(characteristic: ScoutCharacteristic): SequenceGapDetector = detectors.getValue(characteristic)

    /** Starts [characteristic] over, for a new subscription. */
    fun reset(characteristic: ScoutCharacteristic) {
        detector(characteristic).reset()
    }

    /** Decodes one Records notification. A frame may carry several batches; the gap is on the first. */
    fun decodeRecords(notification: ByteArray, receivedAtMs: Long): List<ReceivedRecordBatch> {
        val frame = ScoutFraming.decode(notification)
        val missed = detector(ScoutCharacteristic.RECORDS).observe(frame.sequence)
        return frame.messages.mapIndexed { index, bytes ->
            ReceivedRecordBatch(
                sequence = frame.sequence,
                missedNotifications = if (index == 0) missed else 0,
                batch = RecordBatch.parseFrom(bytes),
                receivedAtMs = receivedAtMs,
            )
        }
    }

    /** Decodes one Status notification. */
    fun decodeStatus(notification: ByteArray): List<BoardStatus> =
        decode(ScoutCharacteristic.STATUS, notification) { BoardStatus.parseFrom(it) }

    /** Decodes one Raw notification. */
    fun decodeRaw(notification: ByteArray, receivedAtMs: Long): ReceivedRawFrames {
        val frame = ScoutFraming.decode(notification)
        val missed = detector(ScoutCharacteristic.RAW).observe(frame.sequence)
        return ReceivedRawFrames(frame.sequence, missed, frame.messages.map { RawFrame.parseFrom(it) }, receivedAtMs)
    }

    /** Decodes one Control indication into its `ControlResponse` messages. */
    fun decodeControl(indication: ByteArray): List<ControlResponse> =
        decode(ScoutCharacteristic.CONTROL, indication) { ControlResponse.parseFrom(it) }

    /** Decodes one OTA notification into its acks. */
    fun decodeOtaAcks(notification: ByteArray): List<OtaAck> =
        decode(ScoutCharacteristic.OTA, notification) { OtaAck.parseFrom(it) }

    private fun <T> decode(characteristic: ScoutCharacteristic, bytes: ByteArray, parse: (ByteArray) -> T): List<T> {
        val frame = ScoutFraming.decode(bytes)
        detector(characteristic).observe(frame.sequence)
        return frame.messages.map(parse)
    }
}
