package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The neighbor sections. */
class CellularNeighborsTest {

    private fun titles(section: NeighborSection) = section.rows.map { it.title.value }

    @Test
    fun sections_comeInTechnologyOrder() {
        val sections = CellularNeighbors.sections(
            listOf(
                DetailsRecords.gsm(serving = false),
                DetailsRecords.lte(serving = false),
                DetailsRecords.nr(serving = false, nci = null),
                DetailsRecords.umts(serving = false),
            )
        )

        assertEquals(
            listOf(CellularProtocol.NR, CellularProtocol.LTE, CellularProtocol.UMTS, CellularProtocol.GSM),
            sections.map { it.protocol },
        )
    }

    @Test
    fun rows_strongestFirst_noSignalLast() {
        val section = CellularNeighbors.sections(
            listOf(
                DetailsRecords.lte(serving = false, pci = 1, rsrp = -101f),
                DetailsRecords.lte(serving = false, pci = 2, rsrp = null),
                DetailsRecords.lte(serving = false, pci = 3, rsrp = -91f),
            )
        ).single()

        assertEquals(listOf("3", "1", "2"), titles(section))
        assertNull(section.rows.last().signal.value)
        assertNull(section.rows.last().signal.fraction)
    }

    @Test
    fun neighborsWithTheSameSignal_areAllKept_byChannelThenPci() {
        val section = CellularNeighbors.sections(
            listOf(
                DetailsRecords.lte(serving = false, pci = 302, earfcn = 66_811, rsrp = -96f),
                DetailsRecords.lte(serving = false, pci = 41, earfcn = 66_811, rsrp = -96f),
                DetailsRecords.lte(serving = false, pci = 118, earfcn = 2_050, rsrp = -96f),
            )
        ).single()

        assertEquals(listOf("118", "41", "302"), titles(section))
    }

    @Test
    fun lteRow_hasChannelBandQualityAndTa() {
        val row = CellularNeighbors.sections(listOf(DetailsRecords.lte(serving = false, ta = 3))).single().rows.single()

        assertEquals(CellFieldKind.PCI, row.title.kind)
        assertEquals("101", row.title.value)
        assertEquals(
            listOf(CellFieldKind.EARFCN, CellFieldKind.BAND, CellFieldKind.RSRQ, CellFieldKind.TA),
            row.details.map { it.kind },
        )
        assertEquals(listOf("66811", "66", "-8", "3"), row.details.map { it.value })
        assertEquals("-79", row.signal.value)
        assertEquals(SignalBuckets.STRONG, row.signal.bucket)
    }

    @Test
    fun lteRow_leavesOutAnUnknownBandAndMissingValues() {
        val row = CellularNeighbors.sections(
            listOf(DetailsRecords.lte(serving = false, earfcn = 999_999, rsrq = null, ta = null))
        ).single().rows.single()

        assertEquals(listOf(CellFieldKind.EARFCN), row.details.map { it.kind })
    }

    @Test
    fun gsmAndUmtsRows_useTheirCodes() {
        val sections = CellularNeighbors.sections(listOf(DetailsRecords.gsm(serving = false), DetailsRecords.umts(serving = false)))

        val umts = sections.single { it.protocol == CellularProtocol.UMTS }.rows.single()
        val gsm = sections.single { it.protocol == CellularProtocol.GSM }.rows.single()
        assertEquals(CellFieldKind.PSC, umts.title.kind)
        assertEquals("300", umts.title.value)
        assertEquals(CellFieldKind.RSCP, umts.signal.kind)
        assertEquals(CellFieldKind.BSIC, gsm.title.kind)
        assertEquals("1-2", gsm.title.value)
        assertEquals(listOf("128"), gsm.details.map { it.value })
    }

    @Test
    fun meterFraction_followsTheSignalThresholds() {
        // LTE RSRP: -97 sits 8 dB above the -105 threshold (0.4), a 10 dB step worth 0.2.
        val row = CellularNeighbors.sections(listOf(DetailsRecords.lte(serving = false, rsrp = -97.5f))).single().rows.single()

        assertEquals(0.56f, row.signal.fraction!!, 0.001f)
    }

    @Test
    fun nothing_meansNoSections() {
        assertEquals(emptyList<NeighborSection>(), CellularNeighbors.sections(emptyList()))
    }
}
