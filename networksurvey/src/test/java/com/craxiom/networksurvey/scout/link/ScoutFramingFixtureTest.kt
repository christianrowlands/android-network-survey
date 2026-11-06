package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.OtaAckStatus
import com.craxiom.networksurvey.board.v1.Record
import com.craxiom.networksurvey.board.v1.ResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notifications produced by the firmware's C framing code (test/fixtures/framing in
 * network-survey-esp32), decoded by the app's decoder: the cross-implementation framing check,
 * the same cases as the iOS FramingFixtureTests.
 */
class ScoutFramingFixtureTest {
    private val decoder = ScoutNotificationDecoder()

    @Test
    fun control_decodesTheProtocolDocPingResponse() {
        val bytes = ScoutFixtureFiles.bytes("framing/control_ping_response_seq0.bin")
        assertEquals(20, bytes.size)

        val responses = decoder.decodeControl(bytes)

        assertEquals(0, decoder.detector(ScoutCharacteristic.CONTROL).lastSequence)
        assertEquals(1, responses.size)
        val response = responses[0]
        assertEquals(5, response.requestId)
        assertEquals(ResultCode.RESULT_CODE_OK, response.result)
        assertTrue(response.hasPing())
        assertEquals(1, response.ping.nonce)
        assertEquals(ScoutFixtureFiles.T0, response.ping.boardTimeMs)
        assertTrue(response.ping.timeSynced)
    }

    @Test
    fun ota_decodesTheProtocolDocAck() {
        val acks = decoder.decodeOtaAcks(ScoutFixtureFiles.bytes("framing/ota_ack_seq258.bin"))

        assertEquals(258, decoder.detector(ScoutCharacteristic.OTA).lastSequence)
        assertEquals(1, acks.size)
        assertEquals(4096, acks[0].nextOffset)
        assertEquals(OtaAckStatus.OTA_ACK_STATUS_OK, acks[0].status)
    }

    @Test
    fun status_decodesTwoMessagesInOneFrame() {
        val bytes = ScoutFixtureFiles.bytes("framing/status_two_messages_seq7.bin")
        assertEquals(106, bytes.size)

        val statuses = decoder.decodeStatus(bytes)

        assertEquals(7, decoder.detector(ScoutCharacteristic.STATUS).lastSequence)
        assertEquals(2, statuses.size)
        assertEquals(statuses[0], statuses[1])
        assertEquals(149, statuses[0].currentChannel)
    }

    @Test
    fun records_atMtu185_spanThreeFramesAcrossTheSequenceWrapWithoutAGap() {
        val sequences = ArrayList<Int>()
        val kinds = ArrayList<List<Record.KindCase>>()
        for (index in 0..2) {
            val bytes = ScoutFixtureFiles.bytes("framing/records_mtu185_frame$index.bin")
            assertTrue(bytes.size <= 185 - ScoutGatt.ATT_HEADER_BYTES)
            val batches = decoder.decodeRecords(bytes, receivedAtMs = 0)
            assertEquals(1, batches.size)
            assertEquals(0, batches[0].missedNotifications)
            assertTrue(batches[0].batch.timeSynced)
            sequences.add(batches[0].sequence)
            kinds.add(batches[0].batch.recordsList.map { it.kindCase })
        }

        assertEquals(listOf(65535, 0, 1), sequences)
        assertEquals(
            listOf(
                listOf(Record.KindCase.AP, Record.KindCase.CLIENT),
                listOf(Record.KindCase.PROBE, Record.KindCase.BLE),
                listOf(Record.KindCase.GPS),
            ),
            kinds,
        )
    }

    @Test
    fun records_atMtu517_carryFiveRecordsWithATwoByteBatchLengthNotSynced() {
        val bytes = ScoutFixtureFiles.bytes("framing/records_mtu517_seq9.bin")
        assertEquals(360, bytes.size)

        val batch = decoder.decodeRecords(bytes, receivedAtMs = 0).single()

        assertEquals(9, batch.sequence)
        assertFalse(batch.batch.timeSynced)
        assertEquals(
            listOf(Record.KindCase.AP, Record.KindCase.CLIENT, Record.KindCase.PROBE, Record.KindCase.BLE, Record.KindCase.GPS),
            batch.batch.recordsList.map { it.kindCase },
        )
    }

    @Test
    fun records_exactFit_fillsAnMtu185NotificationWithOne173ByteApRecord() {
        val bytes = ScoutFixtureFiles.bytes("framing/records_exact_fit_mtu185_seq42.bin")
        assertEquals(185 - ScoutGatt.ATT_HEADER_BYTES, bytes.size)

        val batch = decoder.decodeRecords(bytes, receivedAtMs = 0).single()

        assertEquals(42, batch.sequence)
        assertTrue(batch.batch.timeSynced)
        val record = batch.batch.recordsList.single()
        assertEquals(Record.KindCase.AP, record.kindCase)
        assertEquals(173, record.serializedSize)
    }

    @Test
    fun records_aSkippedSequenceIsReportedOnTheNextBatch() {
        decoder.decodeRecords(ScoutFixtureFiles.bytes("framing/records_mtu185_frame1.bin"), receivedAtMs = 0)
        val next = decoder.decodeRecords(ScoutFixtureFiles.bytes("framing/records_mtu517_seq9.bin"), receivedAtMs = 0)

        // Sequence 0 then 9: frames 1 to 8 were lost.
        assertEquals(8, next.single().missedNotifications)

        decoder.reset(ScoutCharacteristic.RECORDS)
        val afterReset = decoder.decodeRecords(ScoutFixtureFiles.bytes("framing/records_mtu517_seq9.bin"), receivedAtMs = 0)
        assertEquals(0, afterReset.single().missedNotifications)
    }
}
