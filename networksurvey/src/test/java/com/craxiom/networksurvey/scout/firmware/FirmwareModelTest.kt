package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Versions, manifest decoding, image headers and the download limits. */
class FirmwareModelTest {

    @Test
    fun versions_followSemVerPrecedence() {
        val ordered = listOf("0.1.0-alpha", "0.1.0-alpha.1", "0.1.0-alpha.beta", "0.1.0-beta.2", "0.1.0-beta.11", "0.1.0-rc.1", "0.1.0", "0.1.1", "0.10.0", "1.0.0")
        val parsed = ordered.map { FirmwareVersion.parse(it)!! }
        assertEquals(parsed, parsed.shuffled().sorted())
        assertEquals(FirmwareVersion.parse("1.2.3"), FirmwareVersion.parse("v1.2.3+build.7"))
        listOf("1.2", "01.2.3", "1.2.3-", "1.2.3-01", "a.b.c", "").forEach { assertNull(it, FirmwareVersion.parse(it)) }
        assertEquals("0.2.0", FirmwareVersion.higher("0.1.0", "0.2.0"))
        assertEquals("0.1.0", FirmwareVersion.higher("0.1.0", "garbage"))
        assertEquals(FirmwareComparison.UNKNOWN, FirmwareComparison.of("dev", "0.1.0"))
        assertEquals(FirmwareComparison.SAME, FirmwareComparison.of("dev", "dev"))
    }

    @Test
    fun aManifest_decodes_andIgnoresUnknownFields() {
        val image = FirmwareTestData.image()
        val manifest = FirmwareManifest.decode(FirmwareTestData.manifestJson(image).toByteArray())

        assertEquals("0.2.0", manifest.version)
        assertEquals("0.0.0", manifest.minVersion)
        assertEquals(1, manifest.images.size)
        assertEquals("ESP32_C5_DEVKITC_1", manifest.images[0].boardModel)
    }

    @Test
    fun anotherManifestFormat_isUnsupported() {
        val json = FirmwareTestData.manifestJson(FirmwareTestData.image(), format = 2)

        val error = decodeFails(json)

        assertTrue(error is FirmwareUpdateError.UnsupportedManifestFormat)
    }

    @Test
    fun brokenManifests_areInvalid() {
        val image = FirmwareTestData.image()
        val good = FirmwareTestData.manifestJson(image)
        val broken = listOf(
            "not json",
            "[]",
            "{}",
            good.replace("\"version\": \"0.2.0\"", "\"version\": \"\""),
            FirmwareTestData.manifestJson(image, minVersion = "0.3.0"),
            good.replace(FirmwareHex.encode(FirmwarePackage.sha256(image)), "abcd"),
            good.replace(FirmwareTestData.signBase64(image), "!!"),
            FirmwareTestData.manifestJson(image, size = 0),
        )
        broken.forEach { json -> assertTrue(json, decodeFails(json) is FirmwareUpdateError.ManifestInvalid) }
    }

    @Test
    fun theImageHeader_givesTheChipAndVersion() {
        val header = EspAppImage.parse(FirmwareTestData.image(version = "1.4.2"))!!

        assertEquals(Chip.CHIP_ESP32_C5, header.chip)
        assertEquals("1.4.2", header.version)
        assertNull(EspAppImage.parse(ByteArray(200)))
        assertNull(EspAppImage.parse(FirmwareTestData.image(version = null))!!.version)
        assertEquals("ESP32-C6", FirmwareBoardNames.displayChip(EspAppImage.parse(FirmwareTestData.image(chipId = FirmwareTestData.CHIP_ID_C6))!!.chip))
    }

    @Test
    fun theFetcher_refusesHttp_oversizedBodies_andErrors() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(100)))
            server.enqueue(MockResponse().setChunkedBody("y".repeat(2_000), 128))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("ok"))
            val url = server.url("/firmware-manifest.json")

            assertEquals(FirmwareFetchException.Kind.INSECURE, fetchFails { OkHttpFirmwareFetcher().fetch(url, 1_000) }.kind)
            val testFetcher = OkHttpFirmwareFetcher(allowInsecureHttp = true)
            assertEquals(FirmwareFetchException.Kind.TOO_LARGE, fetchFails { testFetcher.fetch(url, 50) }.kind)
            assertEquals(FirmwareFetchException.Kind.TOO_LARGE, fetchFails { testFetcher.fetch(url, 1_000) }.kind)
            assertEquals(FirmwareFetchException.Kind.HTTP, fetchFails { testFetcher.fetch(url, 1_000) }.kind)
            assertEquals("ok", String(testFetcher.fetch(url, 1_000)))
        }
    }

    @Test
    fun theCallTimeout_endsASlowDownload() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(1_000)).throttleBody(1, 1, TimeUnit.SECONDS))
            val client = OkHttpClient.Builder().callTimeout(200, TimeUnit.MILLISECONDS).build()
            val fetcher = OkHttpFirmwareFetcher(client, allowInsecureHttp = true)

            assertEquals(FirmwareFetchException.Kind.NETWORK, fetchFails { fetcher.fetch(server.url("/image.bin"), 2_000) }.kind)
        }
    }

    @Test
    fun canceling_stopsTheDownload() = runBlocking<Unit> {
        MockWebServer().use { server ->
            // At this speed the body takes over 16 minutes, so only the cancel can end the call
            server.enqueue(MockResponse().setBody("x".repeat(1_000)).throttleBody(1, 1, TimeUnit.SECONDS))
            val client = OkHttpClient()
            val fetcher = OkHttpFirmwareFetcher(client, allowInsecureHttp = true)

            val download = launch(Dispatchers.IO) { fetcher.fetch(server.url("/image.bin"), 2_000) }
            withTimeout(WAIT_MS) { while (client.dispatcher.runningCallsCount() == 0) delay(POLL_MS) }
            download.cancelAndJoin()

            withTimeout(WAIT_MS) { while (client.dispatcher.runningCallsCount() > 0) delay(POLL_MS) }
        }
    }

    private fun decodeFails(json: String): FirmwareUpdateError = fileFails { FirmwareManifest.decode(json.toByteArray()) }

    private fun fileFails(block: () -> Unit): FirmwareUpdateError = try {
        block()
        fail("Expected a FirmwareUpdateError")
        throw IllegalStateException()
    } catch (e: FirmwareUpdateError) {
        e
    }

    private suspend fun fetchFails(block: suspend () -> Unit): FirmwareFetchException = try {
        block()
        fail("Expected a FirmwareFetchException")
        throw IllegalStateException()
    } catch (e: FirmwareFetchException) {
        e
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
