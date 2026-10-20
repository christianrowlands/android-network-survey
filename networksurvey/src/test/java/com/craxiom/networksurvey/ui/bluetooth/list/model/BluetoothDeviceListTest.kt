package com.craxiom.networksurvey.ui.bluetooth.list.model

import com.craxiom.messaging.bluetooth.SupportedTechnologies
import com.craxiom.messaging.bluetooth.Technology
import com.craxiom.networksurvey.services.controller.BluetoothController
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothRecords.classic
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothRecords.le
import com.craxiom.networksurvey.ui.bluetooth.list.model.BluetoothRecords.row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folding records into devices, dropping devices no longer heard, and the sort orders. */
class BluetoothDeviceListTest {

    private val apple = "4c"
    private val airTagData = "121910bbcc"

    @Test
    fun merge_keysByAddressAndSkipsBlankAddresses() {
        val devices = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA"), le("BB"), le("")), now = 1)

        assertEquals(setOf("AA", "BB"), devices.keys)
    }

    @Test
    fun merge_aClassicRecordKeepsWhatTheLeScannerLearned() {
        val first = BluetoothDeviceList.merge(
            emptyMap(),
            listOf(le("AA", rssi = -50f, name = "Tag", companyId = apple, mfgData = airTagData, supported = SupportedTechnologies.DUAL)),
            now = 1,
        )

        val device = BluetoothDeviceList.merge(first, listOf(classic("AA", rssi = -80f)), now = 2).getValue("AA")

        assertEquals(-80, device.rssi)
        assertEquals(2L, device.lastSeenMillis)
        assertEquals("Tag", device.name)
        assertEquals(apple, device.companyId)
        assertEquals(FindMyDevice.AIRTAG, device.findMy)
        assertEquals(BluetoothTech.DUAL, device.tech)
        assertEquals("the details open with the LE scanner's record", Technology.LE, device.detailsRecord.technology)
    }

    @Test
    fun merge_leAndClassicSupportWidenToDual() {
        val le = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA", supported = SupportedTechnologies.LE)), now = 1)

        val widened = BluetoothDeviceList.merge(le, listOf(classic("AA", supported = SupportedTechnologies.BR_EDR)), now = 2)

        assertEquals(BluetoothTech.DUAL, widened.getValue("AA").tech)
    }

    @Test
    fun merge_dualNeverNarrowsBackToClassic() {
        val dual = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA", supported = SupportedTechnologies.DUAL)), now = 1)

        val after = BluetoothDeviceList.merge(dual, listOf(classic("AA", supported = SupportedTechnologies.BR_EDR)), now = 2)

        assertEquals(BluetoothTech.DUAL, after.getValue("AA").tech)
    }

    @Test
    fun merge_aRecordWithoutSignalKeepsTheLastSignal() {
        val first = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA", rssi = -55f)), now = 1)

        assertEquals(-55, BluetoothDeviceList.merge(first, listOf(le("AA", rssi = null)), now = 2).getValue("AA").rssi)
    }

    @Test
    fun merge_newManufacturerDataCanClearTheFindMyKind() {
        val tagged = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA", companyId = apple, mfgData = airTagData)), now = 1)

        val device = BluetoothDeviceList.merge(tagged, listOf(le("AA", companyId = apple, mfgData = "1005aa")), now = 2).getValue("AA")

        assertNull(device.findMy)
    }

    @Test
    fun merge_aClassicOnlyDeviceOpensWithItsLatestRecord() {
        val first = BluetoothDeviceList.merge(emptyMap(), listOf(classic("AA", rssi = -70f)), now = 1)

        val device = BluetoothDeviceList.merge(first, listOf(classic("AA", rssi = -60f)), now = 2).getValue("AA")

        assertEquals(-60f, device.detailsRecord.signalStrength.value)
    }

    @Test
    fun pruneStale_dropsOnlyDevicesNotHeardWithinTheWindow() {
        val devices = BluetoothDeviceList.merge(emptyMap(), listOf(le("OLD")), now = 1_000) +
            BluetoothDeviceList.merge(emptyMap(), listOf(le("NEW")), now = 9_000)

        val pruned = BluetoothDeviceList.pruneStale(devices, now = 10_000, retentionMs = 5_000)

        assertEquals(setOf("NEW"), pruned.keys)
    }

    @Test
    fun pruneStale_returnsTheSameMapWhenNothingIsStale() {
        val devices = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA")), now = 1_000)

        assertSame(devices, BluetoothDeviceList.pruneStale(devices, now = 2_000, retentionMs = 5_000))
    }

    @Test
    fun aSteadyDeviceReportedEveryMinute_staysAtTheDefaultScanRate() {
        // The controller re-reports an unchanged device only after 30 s, at the next scan cycle.
        val retention = BluetoothController.displayRetentionMs(30_000)
        var devices = BluetoothDeviceList.merge(emptyMap(), listOf(le("AA")), now = 0)

        for (now in 5_000L..60_000L step 5_000L) {
            devices = BluetoothDeviceList.pruneStale(devices, now, retention)
        }

        assertTrue(devices.containsKey("AA"))
    }

    @Test
    fun displayRetention_coversAShortScanRateToo() {
        // A scan cycle never takes less than the LE and Classic phases, so a 5 s rate still waits
        // for a 23 s cycle.
        assertTrue(BluetoothController.displayRetentionMs(5_000) >= 60_000)
    }

    @Test
    fun sortBySignal_strongestFirstThenNoSignalLast() {
        val rows = listOf(row("A", rssi = null), row("B", rssi = -80), row("C", rssi = -40))

        assertEquals(listOf("C", "B", "A"), BluetoothDeviceList.sorted(rows, BluetoothSort.SIGNAL).map { it.address })
    }

    @Test
    fun sortTies_fallBackToTheAddress() {
        val rows = listOf(row("C", rssi = -60), row("A", rssi = -60), row("B", rssi = -60))

        assertEquals(listOf("A", "B", "C"), BluetoothDeviceList.sorted(rows, BluetoothSort.SIGNAL).map { it.address })
    }

    @Test
    fun sortByAddress() {
        val rows = listOf(row("B2"), row("A1"), row("C3"))

        assertEquals(listOf("A1", "B2", "C3"), BluetoothDeviceList.sorted(rows, BluetoothSort.ADDRESS).map { it.address })
    }

    @Test
    fun sortByName_aToZIgnoringCaseWithUnnamedLast() {
        val rows = listOf(row("1", name = ""), row("2", name = "beacon"), row("3", name = "Alpha"))

        assertEquals(listOf("3", "2", "1"), BluetoothDeviceList.sorted(rows, BluetoothSort.NAME).map { it.address })
    }

    @Test
    fun sortByTechnology_unknownLast() {
        val rows = listOf(
            row("1", tech = BluetoothTech.UNKNOWN),
            row("2", tech = BluetoothTech.DUAL),
            row("3", tech = BluetoothTech.LE),
            row("4", tech = BluetoothTech.CLASSIC),
        )

        assertEquals(listOf("3", "4", "2", "1"), BluetoothDeviceList.sorted(rows, BluetoothSort.TECHNOLOGY).map { it.address })
    }

    @Test
    fun techFrom_mapsEveryReportedValue() {
        assertEquals(BluetoothTech.LE, BluetoothTech.from(SupportedTechnologies.LE))
        assertEquals(BluetoothTech.CLASSIC, BluetoothTech.from(SupportedTechnologies.BR_EDR))
        assertEquals(BluetoothTech.DUAL, BluetoothTech.from(SupportedTechnologies.DUAL))
        assertEquals(BluetoothTech.UNKNOWN, BluetoothTech.from(SupportedTechnologies.UNKNOWN))
    }
}
