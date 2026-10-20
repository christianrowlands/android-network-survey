package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.map.CellRecords
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The serving cell card: every value the old details screen showed, per technology. */
class ServingCellMapperTest {

    private fun ServingCellDetails.field(kind: CellFieldKind): CellField =
        (signals + identity).single { it.kind == kind }

    @Test
    fun lte_hasEveryValue() {
        val card = ServingCellMapper.map(DetailsRecords.lte())!!

        assertEquals(CellularProtocol.LTE, card.protocol)
        assertEquals("Mint", card.carrier)
        assertEquals(
            listOf(CellFieldKind.RSRP, CellFieldKind.RSRQ, CellFieldKind.SNR),
            card.signals.map { it.kind },
        )
        assertEquals("-79", card.field(CellFieldKind.RSRP).value)
        assertEquals(ValueUnit.DBM, card.field(CellFieldKind.RSRP).unit)
        assertEquals(SignalBuckets.STRONG, card.field(CellFieldKind.RSRP).bucket)
        // -79 dBm is 6 dB above the -85 threshold (0.8), a 10 dB step worth 0.2.
        assertEquals(0.92f, card.field(CellFieldKind.RSRP).fraction!!, 0.001f)
        assertEquals("-8", card.field(CellFieldKind.RSRQ).value)
        assertEquals(ValueUnit.DB, card.field(CellFieldKind.RSRQ).unit)
        assertEquals("13", card.field(CellFieldKind.CQI).value)
        assertNull(card.field(CellFieldKind.CQI).bucket)
        assertEquals("310-260", card.field(CellFieldKind.PLMN).value)
        assertEquals("12345", card.field(CellFieldKind.TAC).value)
        assertEquals("25600003", card.field(CellFieldKind.CID).value)
        assertEquals("100000", card.field(CellFieldKind.ENB).value)
        assertEquals("3", card.field(CellFieldKind.SECTOR).value)
        assertEquals("101 (2/33)", card.field(CellFieldKind.PCI).value)
        assertEquals("66811", card.field(CellFieldKind.EARFCN).value)
        assertEquals("15", card.field(CellFieldKind.BANDWIDTH).value)
        assertEquals(ValueUnit.MHZ, card.field(CellFieldKind.BANDWIDTH).unit)
        assertEquals("0", card.field(CellFieldKind.TA).value)
        assertEquals("66", card.band)
    }

    @Test
    fun lte_bandIsNamedAndTappable() {
        val band = ServingCellMapper.map(DetailsRecords.lte())!!.identity.single { it.kind == CellFieldKind.BAND }

        assertEquals("66 (AWS)", band.value)
        assertEquals(BandTapTarget.of(BandTechnology.LTE, 66), band.bandTarget)
    }

    @Test
    fun lte_missingValuesAreNull() {
        val card = ServingCellMapper.map(DetailsRecords.lte(pci = null, earfcn = null, rsrp = null, eci = null, ta = null))!!

        assertNull(card.field(CellFieldKind.PCI).value)
        assertNull(card.field(CellFieldKind.EARFCN).value)
        assertNull(card.field(CellFieldKind.CID).value)
        assertNull(card.field(CellFieldKind.ENB).value)
        assertNull(card.field(CellFieldKind.BAND).value)
        assertNull(card.field(CellFieldKind.BAND).bandTarget)
        assertNull(card.field(CellFieldKind.RSRP).value)
        assertEquals(SignalBuckets.UNKNOWN, card.field(CellFieldKind.RSRP).bucket)
        assertNull(card.band)
        assertNull(card.chart.value)
    }

    @Test
    fun plmn_keepsTheMncLeadingZeros() {
        val card = ServingCellMapper.map(DetailsRecords.lte(plmn = "310-026"))!!

        assertEquals("310-026", card.field(CellFieldKind.PLMN).value)
    }

    @Test
    fun plmn_fallsBackToTheNumbers() {
        val card = ServingCellMapper.map(DetailsRecords.lte(plmn = null))!!

        assertEquals("310-260", card.field(CellFieldKind.PLMN).value)
    }

    @Test
    fun nr_hasBandsFrequencyAndNci() {
        val card = ServingCellMapper.map(DetailsRecords.nr())!!

        assertEquals(
            listOf(CellFieldKind.SS_RSRP, CellFieldKind.SS_RSRQ, CellFieldKind.SS_SINR),
            card.signals.map { it.kind },
        )
        assertEquals("-84", card.field(CellFieldKind.SS_RSRP).value)
        assertEquals("68719476000", card.field(CellFieldKind.CID).value)
        assertEquals("271 (1/90)", card.field(CellFieldKind.PCI).value)
        assertEquals("520110", card.field(CellFieldKind.NARFCN).value)
        assertEquals("2600.550", card.field(CellFieldKind.FREQUENCY).value)
        assertEquals("n41 (TD 2600+)", card.field(CellFieldKind.BAND).value)
        assertEquals(BandTapTarget.of(BandTechnology.NR, 41), card.field(CellFieldKind.BAND).bandTarget)
        assertEquals("n41", card.band)
        assertEquals(CellFieldKind.SS_RSRP, card.chart.metric)
        assertEquals(-125, card.chart.minDbm)
        assertEquals(-73, card.chart.maxDbm)
    }

    @Test
    fun nr_withoutReportedBands_derivesTheBandsFromTheNarfcn() {
        // NARFCN 520110 lies in both n38 and n41, so the header names both.
        val card = ServingCellMapper.map(DetailsRecords.nr(bands = null))!!

        assertEquals("n38/n41", card.band)
    }

    @Test
    fun gsm_showsRssiAndBsic() {
        val card = ServingCellMapper.map(DetailsRecords.gsm())!!

        assertEquals(listOf(CellFieldKind.RSSI), card.signals.map { it.kind })
        assertEquals(
            listOf(CellFieldKind.PLMN, CellFieldKind.LAC, CellFieldKind.CID, CellFieldKind.ARFCN, CellFieldKind.BSIC),
            card.identity.map { it.kind },
        )
        assertEquals("1-2", card.field(CellFieldKind.BSIC).value)
        assertEquals("234-10", card.field(CellFieldKind.PLMN).value)
        assertEquals(SignalBuckets.GOOD, card.field(CellFieldKind.RSSI).bucket)
        assertEquals(-110, card.chart.minDbm)
        assertEquals(-46, card.chart.maxDbm)
    }

    @Test
    fun umts_splitsTheCidAndIgnoresTheBogusRscp() {
        val card = ServingCellMapper.map(DetailsRecords.umts())!!
        assertEquals("2748", card.field(CellFieldKind.RNC).value)
        assertEquals("4660", card.field(CellFieldKind.SHORT_CID).value)
        assertEquals("-90", card.field(CellFieldKind.RSCP).value)
        assertEquals("300", card.field(CellFieldKind.PSC).value)

        assertNull(ServingCellMapper.map(DetailsRecords.umts(rscp = -120f))!!.field(CellFieldKind.RSCP).value)
        assertNull(ServingCellMapper.map(DetailsRecords.umts(rscp = -24f))!!.field(CellFieldKind.RSCP).value)
    }

    @Test
    fun cdma_isNotShown() {
        assertNull(ServingCellMapper.map(CellRecords.cdma()))
    }

    @Test
    fun chart_carriesTheServingCellId() {
        assertEquals(25_600_003, ServingCellMapper.map(DetailsRecords.lte())!!.chart.cellId)
        assertEquals(-79, ServingCellMapper.map(DetailsRecords.lte())!!.chart.value)
    }
}
