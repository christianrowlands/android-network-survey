package com.craxiom.networksurvey.data.oui

import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.OuiBatchResponse
import com.craxiom.networksurvey.data.api.OuiLookupResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.io.IOException

/**
 * Exercises the server-response → [OuiResult] mapping path inside [OuiLookupCoordinator].
 * Doesn't cover the debounce / backoff logic. Those are time-sensitive and out of scope for
 * MVP unit coverage.
 */
class OuiLookupCoordinatorTest {

    private val datasetManager: OuiDatasetManager = mock()

    private fun successResponse(result: OuiLookupResult) = Response.success(
        OuiBatchResponse(datasetVersion = "abc", count = 1, results = listOf(result))
    )

    private fun successItem(
        vendor: String = "",
        isUnknown: Boolean = false,
        reason: String? = null,
        isLaa: Boolean = false
    ) = OuiLookupResult(
        mac = "AA:BB:CC",
        vendor = vendor,
        matchedPrefixLen = if (vendor.isNotEmpty()) 24 else 0,
        isLocallyAdministered = isLaa,
        isMulticast = false,
        isUnknown = isUnknown,
        reason = reason
    )

    @Test
    fun `resolved vendor maps to RESOLVED`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenReturn(
            successResponse(successItem(vendor = "Intel Corporate"))
        )

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.RESOLVED, result.status)
        assertEquals("Intel Corporate", result.vendor)
    }

    @Test
    fun `no_match maps to UNKNOWN`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenReturn(
            successResponse(successItem(isUnknown = true, reason = "no_match"))
        )

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.UNKNOWN, result.status)
    }

    @Test
    fun `registered_to_ieee_subregistry maps to SHARED_VENDOR_BLOCK`() =
        runTest(StandardTestDispatcher()) {
            val api: Api = mock()
            whenever(api.getOuiBatch(any())).thenReturn(
                successResponse(
                    successItem(isUnknown = true, reason = "registered_to_ieee_subregistry")
                )
            )

            val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

            assertEquals(OuiStatus.SHARED_VENDOR_BLOCK, result.status)
        }

    @Test
    fun `Private literal maps to PRIVATE`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenReturn(
            successResponse(successItem(vendor = "Private"))
        )

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.PRIVATE, result.status)
        assertEquals("Private", result.vendor)
    }

    @Test
    fun `IOException maps to OFFLINE`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenAnswer { throw IOException("boom") }

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.OFFLINE, result.status)
    }

    @Test
    fun `HTTP 503 maps to OFFLINE`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenReturn(Response.error(503, "".toResponseBody()))

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.OFFLINE, result.status)
    }

    @Test
    fun `HTTP 400 maps to TRANSIENT_FAILURE`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenReturn(Response.error(400, "".toResponseBody()))

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.TRANSIENT_FAILURE, result.status)
    }

    @Test
    fun `LAA reason maps to LAA`() = runTest(StandardTestDispatcher()) {
        val api: Api = mock()
        whenever(api.getOuiBatch(any())).thenReturn(
            successResponse(
                successItem(isUnknown = true, reason = "locally_administered", isLaa = true)
            )
        )

        val result = OuiLookupCoordinator(api, datasetManager, this).lookupHigh("AA:BB:CC")

        assertEquals(OuiStatus.LAA, result.status)
    }
}
