package com.craxiom.networksurvey.data

import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.data.oui.OuiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManufacturerSourcesTest {

    @Test
    fun `primary returns CompanyID when all three resolved`() {
        val sources = ManufacturerSources(
            companyIdVendor = "Apple, Inc.",
            uuidVendor = "Apple, Inc.",
            ouiVendor = "Broadcom",
            ouiStatus = OuiStatus.RESOLVED
        )
        assertEquals("Apple, Inc.", sources.primary())
    }

    @Test
    fun `primary falls back to UUID when CompanyID missing`() {
        val sources = ManufacturerSources(
            companyIdVendor = null,
            uuidVendor = "Google",
            ouiVendor = "Broadcom",
            ouiStatus = OuiStatus.RESOLVED
        )
        assertEquals("Google", sources.primary())
    }

    @Test
    fun `primary falls back to OUI when both YAML sources missing`() {
        val sources = ManufacturerSources(
            companyIdVendor = null,
            uuidVendor = null,
            ouiVendor = "Silicon Labs",
            ouiStatus = OuiStatus.RESOLVED
        )
        assertEquals("Silicon Labs", sources.primary())
    }

    @Test
    fun `primary returns null when all sources blank`() {
        val sources = ManufacturerSources(
            companyIdVendor = null,
            uuidVendor = "",
            ouiVendor = "  ",
            ouiStatus = OuiStatus.UNKNOWN
        )
        assertNull(sources.primary())
    }

    @Test
    fun `allAgree true when three identical vendors`() {
        val sources = ManufacturerSources(
            companyIdVendor = "Apple, Inc.",
            uuidVendor = "Apple, Inc.",
            ouiVendor = "apple, inc.", // case-insensitive
            ouiStatus = OuiStatus.RESOLVED
        )
        assertTrue(sources.allAgree())
    }

    @Test
    fun `allAgree false when chipset differs from brand`() {
        val sources = ManufacturerSources(
            companyIdVendor = "Apple, Inc.",
            uuidVendor = "Apple, Inc.",
            ouiVendor = "Broadcom",
            ouiStatus = OuiStatus.RESOLVED
        )
        assertFalse(sources.allAgree())
    }

    @Test
    fun `allAgree true with single resolved source`() {
        val sources = ManufacturerSources(
            companyIdVendor = "Apple, Inc.",
            uuidVendor = null,
            ouiVendor = null,
            ouiStatus = OuiStatus.UNKNOWN
        )
        assertTrue(sources.allAgree())
    }

    @Test
    fun `allAgree false with zero resolved sources`() {
        val sources = ManufacturerSources(null, null, null, OuiStatus.UNKNOWN)
        assertFalse(sources.allAgree())
    }

    @Test
    fun `allUnknown true when no source resolved`() {
        val sources = ManufacturerSources(null, "", "  ", OuiStatus.UNKNOWN)
        assertTrue(sources.allUnknown())
    }

    @Test
    fun `allUnknown false when any source has vendor`() {
        val sources = ManufacturerSources("Apple, Inc.", null, null, OuiStatus.UNKNOWN)
        assertFalse(sources.allUnknown())
    }

    @Test
    fun `resolvedCount counts non-blank vendors`() {
        val sources = ManufacturerSources(
            companyIdVendor = "Apple, Inc.",
            uuidVendor = "",
            ouiVendor = "Broadcom",
            ouiStatus = OuiStatus.RESOLVED
        )
        assertEquals(2, sources.resolvedCount())
    }

    @Test
    fun `fromYamlOnly sets OUI slots to null`() {
        val sources = ManufacturerSources.fromYamlOnly("Apple", "Google", OuiStatus.LOADING)
        assertEquals("Apple", sources.companyIdVendor)
        assertEquals("Google", sources.uuidVendor)
        assertNull(sources.ouiVendor)
        assertEquals(OuiStatus.LOADING, sources.ouiStatus)
    }

    @Test
    fun `from reflects RESOLVED OUI vendor`() {
        val sources = ManufacturerSources.from(
            companyIdVendor = "Apple",
            uuidVendor = null,
            oui = OuiResult.resolved("Broadcom")
        )
        assertEquals("Broadcom", sources.ouiVendor)
        assertEquals(OuiStatus.RESOLVED, sources.ouiStatus)
    }

    @Test
    fun `from does not set OUI vendor for non-resolved statuses`() {
        val laa = ManufacturerSources.from("Apple", null, OuiResult.LAA)
        assertNull(laa.ouiVendor)
        assertEquals(OuiStatus.LAA, laa.ouiStatus)

        val offline = ManufacturerSources.from("Apple", null, OuiResult.OFFLINE)
        assertNull(offline.ouiVendor)
        assertEquals(OuiStatus.OFFLINE, offline.ouiStatus)
    }

    @Test
    fun `from treats PRIVATE vendor as OUI vendor`() {
        val sources = ManufacturerSources.from(null, null, OuiResult.privateVendor("Private"))
        assertEquals("Private", sources.ouiVendor)
        assertEquals(OuiStatus.PRIVATE, sources.ouiStatus)
    }
}
