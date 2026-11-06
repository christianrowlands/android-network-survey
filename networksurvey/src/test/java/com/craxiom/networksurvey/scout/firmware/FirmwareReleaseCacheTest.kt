package com.craxiom.networksurvey.scout.firmware

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The background check waits a day only after a successful fetch: a failed one is tried again. */
@RunWith(RobolectricTestRunner::class)
class FirmwareReleaseCacheTest {
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("firmware-cache-test", Context.MODE_PRIVATE)
    private val manifest = FirmwareTestData.manifestJson(FirmwareTestData.image()).toByteArray()
    private val signature = FirmwareTestData.sign(manifest)
    private var nowMs = 2 * FirmwareConstants.CHECK_INTERVAL_MS
    private var online = false
    private var fetches = 0

    private val fetcher = FirmwareFetching { url, _ ->
        fetches++
        if (!online) throw FirmwareFetchException(FirmwareFetchException.Kind.NETWORK, "offline")
        if (url.toString().endsWith(FirmwareConstants.SIGNATURE_SUFFIX)) signature else manifest
    }
    private val cache = FirmwareReleaseCache(
        preferences,
        FirmwareCatalog(FirmwareTestData.trust, fetcher, InMemoryFirmwareReleaseHistory(), MANIFEST),
        { nowMs },
    )

    @Test
    fun aFailedCheck_doesNotStartTheWait() = runTest {
        cache.refreshIfStale()
        cache.refreshIfStale()

        assertEquals("each offline try reaches for the manifest", 2, fetches)
    }

    @Test
    fun aSuccessfulCheck_waitsADay() = runTest {
        online = true
        cache.refreshIfStale()
        assertEquals("the manifest and its signature", 2, fetches)
        assertNotNull(cache.cached())

        nowMs += FirmwareConstants.CHECK_INTERVAL_MS - 1
        cache.refreshIfStale()
        assertEquals(2, fetches)

        nowMs += 1
        cache.refreshIfStale()
        assertEquals(4, fetches)
    }

    private companion object {
        const val MANIFEST = "https://example.com/fw/firmware-manifest.json"
    }
}
