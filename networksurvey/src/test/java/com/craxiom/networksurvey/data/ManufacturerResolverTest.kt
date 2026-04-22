package com.craxiom.networksurvey.data

import com.craxiom.networksurvey.data.oui.OuiRepository
import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.data.oui.OuiStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ManufacturerResolverTest {

    private val companyResolver: BluetoothCompanyResolver = mock {
        on { getCompanyName("004C") } doReturn "Apple, Inc."
        on { getCompanyName("0075") } doReturn "Samsung Electronics"
    }

    private val uuidResolver: BluetoothUuidResolver = mock {
        on { getNameForUuid("FE07") } doReturn "Google LLC"
    }

    private val ouiRepository: OuiRepository = mock()

    private val resolver = ManufacturerResolver(companyResolver, uuidResolver, ouiRepository)

    @Test
    fun `resolve populates all three sources when all resolve`() = runTest {
        whenever(ouiRepository.lookup(any())).thenReturn(OuiResult.resolved("Broadcom"))

        val sources = resolver.resolve(
            mac = "AA:BB:CC:DD:EE:FF",
            serviceUuids = listOf("0000FE07-0000-1000-8000-00805F9B34FB"),
            companyId = "004C"
        )

        assertEquals("Apple, Inc.", sources.companyIdVendor)
        assertEquals("Google LLC", sources.uuidVendor)
        assertEquals("Broadcom", sources.ouiVendor)
        assertEquals(OuiStatus.RESOLVED, sources.ouiStatus)
    }

    @Test
    fun `resolve swallows repository exceptions and reports TRANSIENT_FAILURE`() = runTest {
        whenever(ouiRepository.lookup(any())).thenAnswer { throw RuntimeException("boom") }

        val sources = resolver.resolve(
            mac = "AA:BB:CC:DD:EE:FF",
            serviceUuids = null,
            companyId = "004C"
        )

        assertEquals("Apple, Inc.", sources.companyIdVendor)
        assertNull(sources.ouiVendor)
        assertEquals(OuiStatus.TRANSIENT_FAILURE, sources.ouiStatus)
    }

    @Test
    fun `resolve skips OUI call when MAC is blank`() = runTest {
        val sources = resolver.resolve(
            mac = "",
            serviceUuids = null,
            companyId = "004C"
        )

        assertEquals("Apple, Inc.", sources.companyIdVendor)
        assertNull(sources.ouiVendor)
        assertEquals(OuiStatus.UNKNOWN, sources.ouiStatus)
    }

    @Test
    fun `resolve propagates LAA status from repository`() = runTest {
        whenever(ouiRepository.lookup(any())).thenReturn(OuiResult.LAA)

        val sources = resolver.resolve(
            mac = "02:11:22:33:44:55",
            serviceUuids = null,
            companyId = null
        )

        assertNull(sources.companyIdVendor)
        assertNull(sources.uuidVendor)
        assertNull(sources.ouiVendor)
        assertEquals(OuiStatus.LAA, sources.ouiStatus)
    }

    @Test
    fun `resolveSynchronousSources returns only YAML results with LOADING OUI status`() {
        val sources = resolver.resolveSynchronousSources(
            serviceUuids = listOf("0000FE07-0000-1000-8000-00805F9B34FB"),
            companyId = "0075"
        )

        assertEquals("Samsung Electronics", sources.companyIdVendor)
        assertEquals("Google LLC", sources.uuidVendor)
        assertNull(sources.ouiVendor)
        assertEquals(OuiStatus.LOADING, sources.ouiStatus)
    }

    @Test
    fun `resolveSynchronousSources returns null vendors when YAML lookups miss`() {
        val sources = resolver.resolveSynchronousSources(
            serviceUuids = null,
            companyId = null
        )

        assertNull(sources.companyIdVendor)
        assertNull(sources.uuidVendor)
        assertEquals(OuiStatus.LOADING, sources.ouiStatus)
    }
}
