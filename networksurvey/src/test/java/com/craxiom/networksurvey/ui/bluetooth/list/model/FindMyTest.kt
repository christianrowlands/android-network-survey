package com.craxiom.networksurvey.ui.bluetooth.list.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading Find My advertisements: Apple's company ID, payload type 0x12 and the status byte's device type. */
class FindMyTest {

    @Test
    fun theStatusByteSaysWhatSentIt() {
        // Away from its owner: type 0x12, length 0x19, then the status byte.
        assertEquals(FindMyDevice.APPLE_DEVICE, FindMy.device("4c", "121900aabb"))
        assertEquals(FindMyDevice.AIRTAG, FindMy.device("4c", "121910aabb"))
        assertEquals(FindMyDevice.FIND_MY_ACCESSORY, FindMy.device("4c", "121920aabb"))
        assertEquals(FindMyDevice.AIRPODS, FindMy.device("4c", "121930aabb"))
    }

    @Test
    fun nearItsOwner_theShortFormIsRead() {
        // Type 0x12, length 0x02, a status byte with other bits set around device type 1.
        assertEquals(FindMyDevice.AIRTAG, FindMy.device("4c", "1202d4aa"))
    }

    @Test
    fun onlyAirTagsAndFindMyAccessoriesAreTrackers() {
        assertTrue(FindMyDevice.AIRTAG.isTracker)
        assertTrue(FindMyDevice.FIND_MY_ACCESSORY.isTracker)
        assertFalse(FindMyDevice.APPLE_DEVICE.isTracker)
        assertFalse(FindMyDevice.AIRPODS.isTracker)
    }

    @Test
    fun proximityPairing_isNotFindMy() {
        // Type 0x07 is sent by AirPods and other accessories while pairing.
        assertNull(FindMy.device("4c", "071910aabb"))
    }

    @Test
    fun theCompanyIdMayBePadded() {
        assertEquals(FindMyDevice.AIRTAG, FindMy.device("004c", "121910"))
    }

    @Test
    fun fromAndroid13_theDataStartsWithTheCompanyIdBytes() {
        // The raw advertisement carries Apple's ID little-endian ahead of the payload.
        assertEquals(FindMyDevice.AIRTAG, FindMy.device("4c", "4c00121910aabb"))
        assertEquals(FindMyDevice.AIRTAG, FindMy.device("4c", "4C00121910AABB"))
        assertEquals(FindMyDevice.APPLE_DEVICE, FindMy.device("4c", "4c00121900aabb"))
        assertNull(FindMy.device("4c", "4c00071910"))
        assertNull(FindMy.device("4c", "4c001005aabb"))
        assertNull(FindMy.device("4c", "4c00"))
    }

    @Test
    fun anotherApplePayload_isNotFindMy() {
        assertNull(FindMy.device("4c", "1005aabb"))
    }

    @Test
    fun anotherCompany_isNotFindMy() {
        assertNull(FindMy.device("75", "121910"))
    }

    @Test
    fun missingOrBrokenData_isNotFindMy() {
        assertNull(FindMy.device("", "121910"))
        assertNull(FindMy.device("4c", ""))
        assertNull(FindMy.device("4c", "1"))
        assertNull(FindMy.device("4c", "zz"))
        assertNull(FindMy.device("apple", "121910"))
        // Too short to carry a status byte, or a status byte that is not hex.
        assertNull(FindMy.device("4c", "1219"))
        assertNull(FindMy.device("4c", "12191"))
        assertNull(FindMy.device("4c", "1219zz"))
        assertNull(FindMy.device("4c", "1219-1"))
    }
}
