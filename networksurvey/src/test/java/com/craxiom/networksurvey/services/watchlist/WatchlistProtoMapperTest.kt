package com.craxiom.networksurvey.services.watchlist

import com.craxiom.messaging.watchlist.WatchlistMatchField
import com.craxiom.messaging.watchlist.WatchlistMatchType
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [WatchlistProtoMapper], the conversion layer between the app's watchlist Room entities and
 * the network-survey-messaging protobuf types. These pin the wire contract: unset optional fields must
 * stay unset (not become empty strings), and enabled must survive as an explicit false.
 */
class WatchlistProtoMapperTest {

    private fun entity(
        uuid: String? = "test-uuid",
        label: String? = "Test Label",
        ssid: String? = null,
        bssid: String? = null,
        matchType: String? = WatchlistEntryEntity.MATCH_TYPE_EXACT,
        enabled: Boolean = true,
        cooldownSeconds: Int = 900,
        createdAt: Long = 850_000_000_000L,
        updatedAt: Long = 850_000_000_000L
    ): WatchlistEntryEntity = WatchlistEntryEntity().apply {
        this.uuid = uuid
        this.label = label
        this.ssid = ssid
        this.bssid = bssid
        this.matchType = matchType
        this.enabled = enabled
        this.cooldownSeconds = cooldownSeconds
        this.createdAt = createdAt
        this.updatedAt = updatedAt
    }

    @Test
    fun `an ssid-only entry leaves bssid unset`() {
        val proto = WatchlistProtoMapper.toProtoEntry(entity(ssid = "My Network"))

        assertTrue(proto.hasSsid())
        assertEquals("My Network", proto.ssid.value)
        assertFalse(
            "An entry that does not watch by BSSID must leave the field unset",
            proto.hasBssid()
        )
    }

    @Test
    fun `a bssid-only entry leaves ssid unset`() {
        val proto = WatchlistProtoMapper.toProtoEntry(entity(bssid = "68:7f:74:b0:14:98"))

        assertTrue(proto.hasBssid())
        assertEquals("68:7f:74:b0:14:98", proto.bssid.value)
        assertFalse(
            "An entry that does not watch by SSID must leave the field unset",
            proto.hasSsid()
        )
    }

    @Test
    fun `a disabled entry carries an explicit enabled false`() {
        val proto = WatchlistProtoMapper.toProtoEntry(entity(ssid = "My Network", enabled = false))

        assertTrue(
            "enabled must always be set so false survives JSON serialization",
            proto.hasEnabled()
        )
        assertFalse(proto.enabled.value)
    }

    @Test
    fun `an enabled entry carries enabled true and its cooldown`() {
        val proto = WatchlistProtoMapper.toProtoEntry(entity(ssid = "My Network"))

        assertTrue(proto.hasEnabled())
        assertTrue(proto.enabled.value)
        assertEquals(900, proto.cooldownSeconds)
    }

    @Test
    fun `null uuid and label map to empty strings`() {
        val proto = WatchlistProtoMapper.toProtoEntry(
            entity(
                uuid = null,
                label = null,
                ssid = "My Network"
            )
        )

        assertEquals("", proto.entryUuid)
        assertEquals("", proto.label)
    }

    @Test
    fun `match type maps exact and falls back to unknown`() {
        assertEquals(
            WatchlistMatchType.EXACT,
            WatchlistProtoMapper.toMatchType(WatchlistEntryEntity.MATCH_TYPE_EXACT)
        )
        assertEquals(WatchlistMatchType.UNKNOWN, WatchlistProtoMapper.toMatchType(null))
        assertEquals(WatchlistMatchType.UNKNOWN, WatchlistProtoMapper.toMatchType("bogus"))
    }

    @Test
    fun `matched field maps ssid and bssid and falls back to unknown`() {
        assertEquals(
            WatchlistMatchField.SSID,
            WatchlistProtoMapper.toMatchField(WatchlistHitEntity.MATCHED_FIELD_SSID)
        )
        assertEquals(
            WatchlistMatchField.BSSID,
            WatchlistProtoMapper.toMatchField(WatchlistHitEntity.MATCHED_FIELD_BSSID)
        )
        assertEquals(WatchlistMatchField.UNKNOWN, WatchlistProtoMapper.toMatchField("bogus"))
    }

    @Test
    fun `rfc3339 produces a parseable rfc3339 timestamp`() {
        val formatted = WatchlistProtoMapper.rfc3339(850_000_000_000L)

        assertTrue(
            "Expected an RFC 3339 timestamp but was: $formatted",
            Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.*""").matches(formatted)
        )
    }
}
