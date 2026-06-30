package com.craxiom.networksurvey.util

import android.net.Uri
import android.util.Base64
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [WatchlistDeepLinkHandler]. Covers base64url/JSON decoding, schema validation,
 * per-entry normalization, the caps that bound a hostile payload, and the Success/Error/NotApplicable
 * results.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistDeepLinkHandlerTest {

    companion object {
        private const val VALID_HOST = NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_HOST
        private const val VALID_PATH = NetworkSurveyConstants.WATCHLIST_DEEP_LINK_PATH

        private fun encode(json: String): String =
            Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

        /** Build a watchlist import URI whose "d" param is the base64url encoding of [json]. */
        private fun uriFor(
            json: String,
            host: String = VALID_HOST,
            path: String = VALID_PATH
        ): Uri =
            Uri.Builder()
                .scheme("https")
                .authority(host)
                .path(path)
                .appendQueryParameter(
                    NetworkSurveyConstants.WATCHLIST_DEEP_LINK_PARAM_DATA,
                    encode(json)
                )
                .build()

        /** Build a watchlist import URI with a raw (already-encoded or invalid) "d" value. */
        private fun rawUriFor(d: String?): Uri {
            val builder = Uri.Builder().scheme("https").authority(VALID_HOST).path(VALID_PATH)
            d?.let {
                builder.appendQueryParameter(
                    NetworkSurveyConstants.WATCHLIST_DEEP_LINK_PARAM_DATA,
                    it
                )
            }
            return builder.build()
        }

        private fun success(result: WatchlistDeepLinkHandler.Result): WatchlistDeepLinkHandler.Result.Success {
            assertTrue(
                "expected Success but was $result",
                result is WatchlistDeepLinkHandler.Result.Success
            )
            return result as WatchlistDeepLinkHandler.Result.Success
        }

        private fun error(result: WatchlistDeepLinkHandler.Result): WatchlistDeepLinkHandler.Result.Error {
            assertTrue(
                "expected Error but was $result",
                result is WatchlistDeepLinkHandler.Result.Error
            )
            return result as WatchlistDeepLinkHandler.Result.Error
        }
    }

    // === Valid payloads ===

    @Test
    fun `parseUri returns Success with name and entries`() {
        val json = """{"v":1,"name":"Acme networks","entries":[
            {"label":"HQ","ssid":"HQ-Guest"},
            {"label":"Lobby","bssid":"A4:11:22:33:44:1C"}
        ]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertEquals("Acme networks", result.importSet.name)
        assertEquals(2, result.importSet.entries.size)
        assertEquals("HQ", result.importSet.entries[0].label)
        assertEquals("HQ-Guest", result.importSet.entries[0].ssid)
        assertNull(result.importSet.entries[0].bssid)
    }

    @Test
    fun `parseUri lowercases bssid and preserves ssid case`() {
        val json = """{"v":1,"entries":[{"ssid":"MyNetwork","bssid":"A4:B5:C6:D7:E8:F9"}]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertEquals("MyNetwork", result.importSet.entries[0].ssid)
        assertEquals("a4:b5:c6:d7:e8:f9", result.importSet.entries[0].bssid)
    }

    @Test
    fun `parseUri allows a missing name`() {
        val json = """{"v":1,"entries":[{"ssid":"OpenWifi"}]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertNull(result.importSet.name)
        assertEquals(1, result.importSet.entries.size)
    }

    @Test
    fun `parseUri strips control characters from name and label`() {
        // Char(92) is a backslash, so esc is the 6-char JSON escape that Gson decodes to a BEL
        // control character. Built this way to avoid backslash-escaping noise in the test source.
        val esc = Char(92).toString() + "u0007"
        val json = """{"v":1,"name":"Ac${esc}me","entries":[{"label":"H${esc}Q","ssid":"HQ"}]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertEquals("Acme", result.importSet.name)
        assertEquals("HQ", result.importSet.entries[0].label)
    }

    @Test
    fun `parseUri strips bidi and zero-width format characters from name`() {
        // U+202E (right-to-left override) and U+200B (zero-width space) are Unicode format chars that
        // are valid raw in a JSON string but must be stripped so the prompt cannot be spoofed.
        val name = "Ac" + Char(0x202E) + "m" + Char(0x200B) + "e"
        val json = """{"v":1,"name":"$name","entries":[{"ssid":"HQ"}]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertEquals("Acme", result.importSet.name)
    }

    @Test
    fun `parseUri drops entries with neither ssid nor bssid but keeps valid ones`() {
        val json = """{"v":1,"entries":[{"label":"empty"},{"ssid":"Keep"}]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertEquals(1, result.importSet.entries.size)
        assertEquals("Keep", result.importSet.entries[0].ssid)
    }

    @Test
    fun `parseUri drops null entries injected by a JSON null and keeps valid ones`() {
        // Gson places a null element into the list for a JSON null; the parser must not crash on it.
        val json = """{"v":1,"entries":[{"ssid":"Keep"},null]}"""

        val result = success(WatchlistDeepLinkHandler.parseUri(uriFor(json)))

        assertEquals(1, result.importSet.entries.size)
        assertEquals("Keep", result.importSet.entries[0].ssid)
    }

    @Test
    fun `parseUri returns Error when every entry is a JSON null`() {
        val json = """{"v":1,"entries":[null,null]}"""

        assertEquals(
            WatchlistDeepLinkHandler.ERROR_NO_VALID_NETWORKS,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    // === Decoding / structural errors ===

    @Test
    fun `parseUri returns Error when data param is missing`() {
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_NO_DATA,
            error(WatchlistDeepLinkHandler.parseUri(rawUriFor(null))).message
        )
    }

    @Test
    fun `parseUri returns Error when data param is blank`() {
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_NO_DATA,
            error(WatchlistDeepLinkHandler.parseUri(rawUriFor("   "))).message
        )
    }

    @Test
    fun `parseUri returns Error when encoded payload is too large`() {
        val huge = "A".repeat(WatchlistDeepLinkHandler.MAX_ENCODED_LENGTH + 1)
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_TOO_LARGE,
            error(WatchlistDeepLinkHandler.parseUri(rawUriFor(huge))).message
        )
    }

    @Test
    fun `parseUri returns Error for malformed base64`() {
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_UNREADABLE,
            error(WatchlistDeepLinkHandler.parseUri(rawUriFor("@@@not-base64@@@"))).message
        )
    }

    @Test
    fun `parseUri returns Error for malformed JSON`() {
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_UNREADABLE,
            error(WatchlistDeepLinkHandler.parseUri(uriFor("not json {"))).message
        )
    }

    // === Schema validation ===

    @Test
    fun `parseUri returns Error for unsupported version`() {
        val json = """{"v":2,"entries":[{"ssid":"HQ"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_UNSUPPORTED_VERSION,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for missing version`() {
        val json = """{"entries":[{"ssid":"HQ"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_UNSUPPORTED_VERSION,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for empty entries`() {
        val json = """{"v":1,"entries":[]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_NO_NETWORKS,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error when all entries are invalid`() {
        val json = """{"v":1,"entries":[{"label":"a"},{"label":"b"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_NO_VALID_NETWORKS,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for too many entries`() {
        val entries = (1..WatchlistDeepLinkHandler.MAX_ENTRIES + 1)
            .joinToString(",") { """{"ssid":"net$it"}""" }
        val json = """{"v":1,"entries":[$entries]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_TOO_MANY,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for invalid bssid format`() {
        val json = """{"v":1,"entries":[{"bssid":"not-a-mac"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_INVALID_BSSID,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for ssid exceeding max length`() {
        val longSsid = "a".repeat(WatchlistDeepLinkHandler.MAX_SSID_LENGTH + 1)
        val json = """{"v":1,"entries":[{"ssid":"$longSsid"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_SSID_TOO_LONG,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for label exceeding max length`() {
        val longLabel = "a".repeat(WatchlistDeepLinkHandler.MAX_LABEL_LENGTH + 1)
        val json = """{"v":1,"entries":[{"label":"$longLabel","ssid":"HQ"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_LABEL_TOO_LONG,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    @Test
    fun `parseUri returns Error for set name exceeding max length`() {
        val longName = "a".repeat(WatchlistDeepLinkHandler.MAX_SET_NAME_LENGTH + 1)
        val json = """{"v":1,"name":"$longName","entries":[{"ssid":"HQ"}]}"""
        assertEquals(
            WatchlistDeepLinkHandler.ERROR_NAME_TOO_LONG,
            error(WatchlistDeepLinkHandler.parseUri(uriFor(json))).message
        )
    }

    // === NotApplicable ===

    @Test
    fun `parseUri returns NotApplicable for wrong host`() {
        val json = """{"v":1,"entries":[{"ssid":"HQ"}]}"""
        val result = WatchlistDeepLinkHandler.parseUri(uriFor(json, host = "example.com"))
        assertTrue(result is WatchlistDeepLinkHandler.Result.NotApplicable)
    }

    @Test
    fun `parseUri returns NotApplicable for the NS Analytics register path`() {
        val json = """{"v":1,"entries":[{"ssid":"HQ"}]}"""
        val result = WatchlistDeepLinkHandler.parseUri(
            uriFor(json, path = NsAnalyticsConstants.NS_ANALYTICS_DEEP_LINK_PATH)
        )
        assertTrue(result is WatchlistDeepLinkHandler.Result.NotApplicable)
    }
}
