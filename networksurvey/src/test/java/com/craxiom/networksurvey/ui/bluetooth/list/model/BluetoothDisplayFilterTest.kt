package com.craxiom.networksurvey.ui.bluetooth.list.model

import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothRecords.row
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Bluetooth list's display filter. */
class BluetoothDisplayFilterTest {

    private val tag = row("02:00:F4:12:00:9C", name = "Keys", company = "Apple, Inc.", tracker = FindMyDevice.AIRTAG)
    private val findMyTag = row("C2:10:4A:7B:33:01", company = "Apple, Inc.", tracker = FindMyDevice.FIND_MY_ACCESSORY)
    private val unnamed = row("02-00-5C-8D-31-AA", company = "Zebra Technologies")

    @Test
    fun anEmptyFilter_isInactiveAndMatchesEverything() {
        val filter = BluetoothDisplayFilter(query = "  ")

        assertFalse(filter.isActive)
        assertTrue(filter.matches(tag))
        assertTrue(filter.matches(unnamed))
    }

    @Test
    fun query_matchesTheNameIgnoringCase() {
        assertTrue(BluetoothDisplayFilter(query = "keys").matches(tag))
        assertFalse(BluetoothDisplayFilter(query = "keys").matches(unnamed))
    }

    @Test
    fun query_matchesTheCompany() {
        assertTrue(BluetoothDisplayFilter(query = "zebra").matches(unnamed))
    }

    @Test
    fun query_matchesTheAddressWithOrWithoutSeparators() {
        assertTrue(BluetoothDisplayFilter(query = "12:00:9c").matches(tag))
        assertTrue(BluetoothDisplayFilter(query = "f412009c").matches(tag))
        assertTrue(BluetoothDisplayFilter(query = "8d:31").matches(unnamed))
    }

    @Test
    fun query_isTrimmed() {
        assertTrue(BluetoothDisplayFilter(query = " Keys ").matches(tag))
    }

    @Test
    fun namedOnly_hidesUnnamedDevices() {
        val filter = BluetoothDisplayFilter(namedOnly = true)

        assertTrue(filter.isActive)
        assertTrue(filter.matches(tag))
        assertFalse(filter.matches(unnamed))
    }

    @Test
    fun trackersOnly_showsAirTagsAndOtherFindMyTrackers() {
        val filter = BluetoothDisplayFilter(trackersOnly = true)

        assertTrue(filter.isActive)
        assertTrue(filter.matches(tag))
        assertTrue(filter.matches(findMyTag))
        assertFalse(filter.matches(unnamed))
    }

    @Test
    fun everyConstraint_mustPass() {
        assertFalse(BluetoothDisplayFilter(query = "zebra", trackersOnly = true).matches(unnamed))
    }
}
