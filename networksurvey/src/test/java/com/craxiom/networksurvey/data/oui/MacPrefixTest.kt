package com.craxiom.networksurvey.data.oui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacPrefixTest {

    @Test
    fun `parse accepts colon-grouped MAC`() {
        assertEquals(0xAABBCCDDEEFFL, MacPrefix.parse("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `parse accepts dash-grouped MAC`() {
        assertEquals(0xAABBCCDDEEFFL, MacPrefix.parse("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `parse accepts dot-grouped MAC`() {
        assertEquals(0xAABBCCDDEEFFL, MacPrefix.parse("AABB.CCDD.EEFF"))
    }

    @Test
    fun `parse accepts bare hex`() {
        assertEquals(0xAABBCCDDEEFFL, MacPrefix.parse("AABBCCDDEEFF"))
    }

    @Test
    fun `parse accepts lowercase`() {
        assertEquals(0xAABBCCDDEEFFL, MacPrefix.parse("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `parse rejects short MAC`() {
        assertNull(MacPrefix.parse("AA:BB:CC:DD:EE"))
    }

    @Test
    fun `parse rejects non-hex characters`() {
        assertNull(MacPrefix.parse("AA:BB:CC:DD:EE:ZZ"))
    }

    @Test
    fun `parse rejects empty string`() {
        assertNull(MacPrefix.parse(""))
    }

    @Test
    fun `normalize produces uppercase colon form`() {
        assertEquals("AA:BB:CC:DD:EE:FF", MacPrefix.normalize("aa-bb-cc-dd-ee-ff"))
        assertEquals("00:01:02:03:04:05", MacPrefix.normalize("000102030405"))
    }

    @Test
    fun `maskToPrefix 24 bits keeps top three octets`() {
        val full = 0xAABBCCDDEEFFL
        assertEquals(0xAABBCC000000L, MacPrefix.maskToPrefix(full, MacPrefix.MA_L))
    }

    @Test
    fun `maskToPrefix 28 bits keeps top 28 bits`() {
        val full = 0xAABBCCDDEEFFL
        assertEquals(0xAABBCCD00000L, MacPrefix.maskToPrefix(full, MacPrefix.MA_M))
    }

    @Test
    fun `maskToPrefix 36 bits keeps top 36 bits`() {
        val full = 0xAABBCCDDEEFFL
        assertEquals(0xAABBCCDDE000L, MacPrefix.maskToPrefix(full, MacPrefix.MA_S))
    }

    @Test
    fun `maskToPrefix 48 is identity`() {
        val full = 0xAABBCCDDEEFFL
        assertEquals(full, MacPrefix.maskToPrefix(full, 48))
    }

    @Test
    fun `prefix24String formats colon-grouped uppercase`() {
        assertEquals("AA:BB:CC", MacPrefix.prefix24String(0xAABBCCDDEEFFL))
        assertEquals("00:01:02", MacPrefix.prefix24String(0x000102030405L))
    }

    @Test
    fun `isLocallyAdministered true when second-least-significant bit set`() {
        // First byte 0x02 -> binary xxxx_xx10 -> LAA set, multicast not set
        assertTrue(MacPrefix.isLocallyAdministered(0x02AABBCCDDEEL))
        assertTrue(MacPrefix.isLocallyAdministered(0x06AABBCCDDEEL))
    }

    @Test
    fun `isLocallyAdministered false for globally-administered MACs`() {
        // Apple, Intel, etc. First byte has LAA bit clear
        assertFalse(MacPrefix.isLocallyAdministered(0xA87EEA112233L)) // Intel-ish
        assertFalse(MacPrefix.isLocallyAdministered(0xB84C87112233L)) // generic
    }

    @Test
    fun `isMulticast true when least-significant bit of first byte set`() {
        assertTrue(MacPrefix.isMulticast(0x01005E112233L)) // IPv4 multicast
        assertTrue(MacPrefix.isMulticast(0xFFFFFFFFFFFFL)) // broadcast
    }

    @Test
    fun `isMulticast false for unicast MACs`() {
        assertFalse(MacPrefix.isMulticast(0xA87EEA112233L))
    }

    @Test
    fun `broadcast MAC reports both LAA and multicast`() {
        val broadcast = 0xFFFFFFFFFFFFL
        assertTrue(MacPrefix.isLocallyAdministered(broadcast))
        assertTrue(MacPrefix.isMulticast(broadcast))
    }
}
