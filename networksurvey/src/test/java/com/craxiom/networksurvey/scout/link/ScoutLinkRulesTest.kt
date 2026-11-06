package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.OtaEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/** Reconnect backoff, advertisement parsing, protocol helpers and the GATT status rules. */
class ScoutLinkRulesTest {

    @Test
    fun backoff_startsAtOneSecondDoublesAndStopsAtThirty() {
        val backoff = ReconnectBackoff()
        val delays = (1..8).map { backoff.delayAfterAttemptMs(it) }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L), delays)
        assertEquals(1_000L, backoff.delayAfterAttemptMs(0))
        assertEquals(30_000L, backoff.delayAfterAttemptMs(10_000))
    }

    @Test
    fun advertisement_parsesFlagsModelAndProtocol() {
        // Protocol 1, flags pairable | 5 GHz, ESP32-C5-DevKitC-1, plus a trailing byte to ignore.
        val parsed = ScoutAdvertisement.parse(byteArrayOf(1, 0x09, 2, 0x7F))!!
        assertEquals(1, parsed.protocolMajor)
        assertTrue(parsed.pairable)
        assertFalse(parsed.hasBond)
        assertEquals(BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1, parsed.model)
    }

    @Test
    fun advertisement_ignoresReservedBitsAndUnknownModels() {
        val parsed = ScoutAdvertisement.parse(byteArrayOf(2, 0xF6.toByte(), 99))!!
        assertFalse(parsed.pairable)
        assertTrue(parsed.hasBond)
        assertEquals(BoardModel.BOARD_MODEL_UNSPECIFIED, parsed.model)
    }

    @Test
    fun advertisement_rejectsAShortPayload() {
        assertNull(ScoutAdvertisement.parse(byteArrayOf(1, 0)))
        assertNull(ScoutAdvertisement.parse(null))
    }

    @Test
    fun shortId_comesOnlyFromAnNsName() {
        assertEquals("ABCD", ScoutAdvertisement.shortIdFromName("NS-abcd"))
        assertNull(ScoutAdvertisement.shortIdFromName("NS-"))
        assertNull(ScoutAdvertisement.shortIdFromName("NS-ZZZZ"))
        assertNull(ScoutAdvertisement.shortIdFromName("Pixel Buds"))
        assertNull(ScoutAdvertisement.shortIdFromName(null))
    }

    @Test
    fun sighting_takesTheFirstScoutPayloadAndFallsBackForTheName() {
        val payload = mapOf(0xFFFF to byteArrayOf(1, 0x01, 2))
        val named = ScoutSighting.from("02:00:5E:00:AB:CD", "NS-5CFA", payload, -50, 10)!!
        assertEquals("5CFA", named.shortId)

        // A later advertisement without the scan response keeps the name it learned.
        val unnamed = ScoutSighting.from("02:00:5E:00:AB:CD", null, payload, -55, 20, previous = named)!!
        assertEquals("5CFA", unnamed.shortId)

        val fromAddress = ScoutSighting.from("02:00:5E:00:AB:CD", null, payload, -55, 20)!!
        assertEquals("ABCD", fromAddress.shortId)

        assertNull(ScoutSighting.from("02:00:5E:00:AB:CD", "NS-5CFA", mapOf(0x004C to byteArrayOf(1)), -50, 10))
    }

    @Test
    fun compatibility_comparesProtocolMajors() {
        assertEquals(ScoutCompatibility.COMPATIBLE, ScoutCompatibility.of(ScoutProtocol.APP_PROTOCOL_MAJOR))
        assertEquals(ScoutCompatibility.NEEDS_FIRMWARE_UPDATE, ScoutCompatibility.of(ScoutProtocol.APP_PROTOCOL_MAJOR - 1))
        assertEquals(ScoutCompatibility.NEEDS_APP_UPDATE, ScoutCompatibility.of(ScoutProtocol.APP_PROTOCOL_MAJOR + 1))
    }

    @Test
    fun hello_cutsTheAppVersionToThirtyOneBytesOnACharacterBoundary() {
        val long = "2.0-" + "é".repeat(20)
        val version = ScoutProtocol.hello(long).hello.appVersion
        assertTrue(version.toByteArray(StandardCharsets.UTF_8).size <= ScoutGatt.APP_VERSION_MAX_BYTES)
        assertTrue(long.startsWith(version))
        assertEquals("2.0", ScoutProtocol.hello("2.0").hello.appVersion)
    }

    @Test
    fun timeouts_areFiveSecondsAndThirtyForOtaEnd() {
        assertEquals(5_000L, ScoutProtocol.timeoutFor(ScoutProtocol.start()))
        assertEquals(30_000L, ScoutProtocol.timeoutFor(ControlRequest.newBuilder().setOtaEnd(OtaEnd.getDefaultInstance())))
    }

    @Test
    fun capture_mergeUnionsTypes() {
        val merged = ScoutCapture().merge(ScoutCapture(wifiClients = true))
        assertTrue(merged.wifiAccessPoints && merged.wifiClients)
        assertFalse(merged.rawFrames)
    }

    @Test
    fun capture_neverAsksForBleAndLeavesChannelsToTheBoard() {
        val configure = ScoutCapture().toConfigureRequest().configure
        assertFalse(configure.ble)
        assertEquals(0, configure.channelsCount)
    }

    @Test
    fun gattStatus_securityErrorsOnABondedPhoneMeanAStaleBond() {
        listOf(GattStatus.INSUFFICIENT_AUTHENTICATION, GattStatus.INSUFFICIENT_AUTHORIZATION, GattStatus.INSUFFICIENT_ENCRYPTION, GattStatus.AUTH_FAIL)
            .forEach { status ->
                assertEquals(ScoutLinkFailure.BOND_MISSING, GattStatus.operationFailure(status, phoneBonded = true))
                assertEquals(ScoutLinkFailure.PAIRING_REJECTED, GattStatus.operationFailure(status, phoneBonded = false))
            }
        assertEquals(ScoutLinkFailure.GATT_ERROR, GattStatus.operationFailure(133, phoneBonded = true))
    }

    @Test
    fun gattStatus_keyFailuresOnDisconnectAreKeyRejectionsOnlyWhenBonded() {
        assertEquals(ScoutLinkFailure.KEY_REJECTED, GattStatus.disconnectFailure(GattStatus.HCI_AUTHENTICATION_FAILURE, phoneBonded = true))
        assertEquals(ScoutLinkFailure.KEY_REJECTED, GattStatus.disconnectFailure(GattStatus.HCI_PIN_OR_KEY_MISSING, phoneBonded = true))
        assertEquals(ScoutLinkFailure.KEY_REJECTED, GattStatus.disconnectFailure(GattStatus.HCI_MIC_FAILURE, phoneBonded = true))
        assertFalse(ScoutLinkFailure.KEY_REJECTED.needsUser)
        assertEquals(ScoutLinkFailure.LINK_LOST, GattStatus.disconnectFailure(GattStatus.HCI_PIN_OR_KEY_MISSING, phoneBonded = false))
        assertEquals(ScoutLinkFailure.LINK_LOST, GattStatus.disconnectFailure(GattStatus.HCI_CONNECTION_TIMEOUT, phoneBonded = true))
    }
}
