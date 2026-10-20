package com.craxiom.networksurvey.model

import com.craxiom.networksurvey.ui.map.CellRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [TowerKey] identifies a cell the way the new-tower check and the Map's card both need. */
class TowerKeyTest {

    @Test
    fun lte_usesTheTacAndEci() {
        assertEquals(TowerKey("310", "260", 12345, 25_600_103L, CellularProtocol.LTE), TowerKey.from(CellRecords.lte()))
    }

    @Test
    fun nr_usesTheTacAndNci() {
        assertEquals(TowerKey("310", "260", 22222, 1L shl 35, CellularProtocol.NR), TowerKey.from(CellRecords.nr()))
    }

    @Test
    fun gsm_usesTheLacAndCi() {
        assertEquals(TowerKey("234", "10", 210, 4321L, CellularProtocol.GSM), TowerKey.from(CellRecords.gsm()))
    }

    @Test
    fun umts_usesTheLacAndCid() {
        assertEquals(TowerKey("262", "2", 310, 98765L, CellularProtocol.UMTS), TowerKey.from(CellRecords.umts()))
    }

    @Test
    fun theRadio_isTheTechnologyName() {
        assertEquals("LTE", TowerKey.from(CellRecords.lte())?.radio)
        assertEquals("NR", TowerKey.from(CellRecords.nr())?.radio)
    }

    @Test
    fun aLeadingZeroMnc_isKept() {
        val key = TowerKey.from(CellRecords.lte(plmn = "234-01"))
        assertEquals("234", key?.mcc)
        assertEquals("01", key?.mnc)
    }

    @Test
    fun cdma_hasNoKey() {
        assertNull(TowerKey.from(CellRecords.cdma()))
    }

    @Test
    fun aCellThatCannotBeIdentified_hasNoKey() {
        assertNull(TowerKey.from(CellRecords.lte(eci = 0)))
        assertNull(TowerKey.from(CellRecords.lte(plmn = "0-0")))
        assertNull(TowerKey.from(null))
    }
}
