package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.fragments.model.NrSecondaryCellSighting
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.map.CellRecords
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** One SIM's state as scans arrive. */
class CellularSimDataTest {

    private val nsa = DetailsRecords.technology(nrMode = NrMode.NON_STANDALONE)

    @Test
    fun aScan_buildsTheServingCellAndNeighbors() {
        val data = CellularSimData().withBatch(
            listOf(DetailsRecords.lte(), DetailsRecords.lte(serving = false, pci = 41)), nowMs = 0,
        )

        assertTrue(data.scanned)
        assertEquals(CellularProtocol.LTE, data.serving!!.protocol)
        assertNotNull(data.servingRecord)
        assertEquals("41", data.neighbors.single().rows.single().title.value)
    }

    @Test
    fun theSecondaryServingNrCell_isOnItsCard_notInTheNeighbors() {
        val data = CellularSimData().withTechnology(nsa).withBatch(
            listOf(DetailsRecords.lte(), DetailsRecords.nrSecondary(pci = 271), DetailsRecords.nr(serving = false, pci = 500, nci = null)),
            nowMs = 0,
        )

        assertEquals("271 (1/90)", data.nrCard!!.pci())
        assertEquals(listOf("500"), data.neighbors.single { it.protocol == CellularProtocol.NR }.rows.map { it.title.value })
    }

    @Test
    fun theNrCard_goesIdleWhenTheLegDetaches_thenClearsOnAnEmptyScan() {
        val live = CellularSimData().withTechnology(nsa).withBatch(listOf(DetailsRecords.lte(), DetailsRecords.nrSecondary()), nowMs = 1_000)
        val idle = live.withTechnology(nsa).withBatch(listOf(DetailsRecords.lte()), nowMs = 31_000)

        assertTrue(idle.nrCard!!.idle())
        assertEquals(30_000L, idle.nrLastSeenAgeMs)

        val empty = idle.withBatch(emptyList(), nowMs = 32_000)
        assertNull(empty.nrCard)
        assertSame(NrSecondaryCellSighting.NONE, empty.nrSighting)
    }

    @Test
    fun anEmptyScan_clearsTheCells_butKeepsTheTechnology() {
        val data = CellularSimData().withTechnology(nsa)
            .withBatch(listOf(DetailsRecords.lte(), DetailsRecords.lte(serving = false)), nowMs = 0)
            .withBatch(emptyList(), nowMs = 1)

        assertNull(data.serving)
        assertNull(data.servingRecord)
        assertEquals(emptyList<NeighborSection>(), data.neighbors)
        assertSame(nsa, data.technology)
        assertTrue(data.scanned)
    }

    @Test
    fun theCarrierName_staysThroughAnEmptyScan() {
        val data = CellularSimData().withBatch(listOf(DetailsRecords.lte(provider = "Mint")), nowMs = 0)
            .withBatch(emptyList(), nowMs = 1)

        assertNull(data.serving)
        assertEquals("Mint", CellularSimView.from(data, "None").carrier)
    }

    @Test
    fun aScanWithoutAServingCell_keepsTheLastOne() {
        val first = CellularSimData().withBatch(listOf(DetailsRecords.lte(pci = 101)), nowMs = 0)
        val next = first.withBatch(listOf(DetailsRecords.lte(serving = false, pci = 41)), nowMs = 1)

        assertSame(first.serving, next.serving)
        assertEquals("41", next.neighbors.single().rows.single().title.value)
    }

    @Test
    fun theLastServingCellWins() {
        val data = CellularSimData().withBatch(listOf(DetailsRecords.lte(), DetailsRecords.nr()), nowMs = 0)

        assertEquals(CellularProtocol.NR, data.serving!!.protocol)
    }

    @Test
    fun aCdmaServingCell_isForTheMapOnly() {
        val data = CellularSimData().withBatch(listOf(CellRecords.cdma()), nowMs = 0)

        assertNull(data.serving)
        assertEquals(CellularProtocol.CDMA, data.servingRecord!!.cellularProtocol)
        assertFalse(data.neighbors.isNotEmpty())
    }

    @Test
    fun theView_hidesCarrierAggregationWithoutAServingCell() {
        val technology = DetailsRecords.technology(bandwidthsKhz = intArrayOf(20_000, 20_000))
        val withCell = CellularSimData().withTechnology(technology).withBatch(listOf(DetailsRecords.lte()), nowMs = 0)
        val noCell = withCell.withBatch(emptyList(), nowMs = 1)

        assertEquals(2, CellularSimView.from(withCell, "None").carrierAggregation!!.carriersMhz.size)
        assertNull(CellularSimView.from(noCell, "None").carrierAggregation)
        assertEquals("Mint", CellularSimView.from(withCell, "None").carrier)
    }
}
