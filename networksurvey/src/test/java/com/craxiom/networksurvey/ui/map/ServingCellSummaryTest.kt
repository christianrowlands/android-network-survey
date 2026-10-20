package com.craxiom.networksurvey.ui.map

import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Map's serving-cell card says, worked out from the serving cell's record. */
class ServingCellSummaryTest {

    @Test
    fun lte_givesTheOperatorBandSignalAndEnb() {
        val summary = ServingCellSummary.from(ServingCellInfo(CellRecords.lte(), 1), 410.0, emptySet())!!

        assertEquals(CellularProtocol.LTE, summary.protocol)
        assertEquals("Mint", summary.operator)
        assertEquals("310-260", summary.plmn)
        assertEquals(66, summary.lteBand)
        assertEquals(-79, summary.signalDbm)
        assertEquals(SignalBuckets.STRONG, summary.signalBucket)
        assertEquals(
            CalculationUtils.getEnodebIdFromCellId(25_600_103) to CalculationUtils.getSectorIdFromCellId(25_600_103),
            summary.enbSector,
        )
        assertEquals(410.0, summary.distanceMeters!!, 0.0)
        assertTrue(summary.metrics.any { it.label == "EARFCN" && it.value == "66536" })
    }

    @Test
    fun nr_givesItsBandDesignatorAndNci() {
        val summary = ServingCellSummary.from(ServingCellInfo(CellRecords.nr(), 2), null, emptySet())!!

        assertEquals("n71", summary.nrBands)
        assertNull(summary.lteBand)
        assertNull(summary.enbSector)
        assertEquals(CellMetric("NCI", (1L shl 35).toString()), summary.identity)
        assertEquals(-98, summary.signalDbm)
    }

    @Test
    fun withoutAnEarfcnOrDistance_thoseAreLeftOut() {
        val summary = ServingCellSummary.from(ServingCellInfo(CellRecords.lte(earfcn = null), 1), null, emptySet())!!

        assertNull(summary.lteBand)
        assertNull(summary.distanceMeters)
        assertFalse(summary.metrics.any { it.label == "EARFCN" })
    }

    @Test
    fun aBlankOperator_isLeftForTheCardToReplace() {
        val summary = ServingCellSummary.from(ServingCellInfo(CellRecords.lte(provider = " "), 1), null, emptySet())!!

        assertNull(summary.operator)
        assertEquals("310-260", summary.plmn)
    }

    @Test
    fun theCell_isNewWhenItsTowerWasFoundNew() {
        val record = CellRecords.lte()
        val newTowers = setOf(TowerKey.from(record)!!)

        assertTrue(ServingCellSummary.from(ServingCellInfo(record, 1), null, newTowers)!!.isNew)
        assertFalse(ServingCellSummary.from(ServingCellInfo(CellRecords.lte(eci = 99), 1), null, newTowers)!!.isNew)
    }

    @Test
    fun noServingCell_meansNoCard() {
        assertNull(ServingCellSummary.from(null, null, emptySet()))
        assertNull(ServingCellSummary.from(ServingCellInfo(null, 1), null, emptySet()))
    }
}
