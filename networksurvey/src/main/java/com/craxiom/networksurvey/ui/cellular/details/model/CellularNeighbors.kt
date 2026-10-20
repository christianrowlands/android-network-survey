package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.ParserUtils

/**
 * Builds the neighbor sections from a scan's non-serving cells: one section per technology (NR,
 * LTE, UMTS, GSM), strongest first, cells without a signal last, ties by channel then code.
 *
 * Every neighbor is kept. The old tables were sorted sets compared on signal alone, so two
 * neighbors with the same signal showed as one row.
 */
object CellularNeighbors {

    private val SECTION_ORDER = listOf(CellularProtocol.NR, CellularProtocol.LTE, CellularProtocol.UMTS, CellularProtocol.GSM)

    /**
     * @param neighbors The scan's non-serving records, without the secondary-serving NR cell (it is
     *                  shown on its own card).
     */
    fun sections(neighbors: List<CellularRecordWrapper>): List<NeighborSection> {
        val byProtocol = neighbors.groupBy { it.cellularProtocol }
        return SECTION_ORDER.mapNotNull { protocol ->
            val rows = byProtocol[protocol].orEmpty().mapNotNull { sortableRow(it) }
            if (rows.isEmpty()) {
                null
            } else {
                NeighborSection(protocol, rows.sortedWith(ROW_ORDER).map { it.row })
            }
        }
    }

    /** A row with the numbers it sorts on. */
    private class SortableRow(val row: NeighborRow, val signal: Int?, val channel: Int?, val code: Int?)

    private val ROW_ORDER = compareBy<SortableRow>(
        { it.signal == null },
        { -(it.signal ?: 0) },
        { it.channel == null },
        { it.channel ?: 0 },
        { it.code == null },
        { it.code ?: 0 },
    )

    private fun sortableRow(record: CellularRecordWrapper): SortableRow? = when (record.cellularProtocol) {
        CellularProtocol.NR -> nr(record)
        CellularProtocol.LTE -> lte(record)
        CellularProtocol.UMTS -> umts(record)
        CellularProtocol.GSM -> gsm(record)
        else -> null
    }

    private fun nr(record: CellularRecordWrapper): SortableRow {
        val data = (record.cellularRecord as NrRecord).data
        val protocol = CellularProtocol.NR
        val narfcn = if (data.hasNarfcn()) data.narfcn.value else null
        val pci = if (data.hasPci()) data.pci.value else null
        val ssRsrp = if (data.hasSsRsrp()) data.ssRsrp.value.toInt() else null
        val ssRsrq = if (data.hasSsRsrq()) data.ssRsrq.value.toInt() else null
        val details = listOfNotNull(
            narfcn?.let { CellField(CellFieldKind.NARFCN, it.toString()) },
            ssRsrq?.let { ServingCellMapper.signal(protocol, CellFieldKind.SS_RSRQ, it, ValueUnit.DB) },
        )
        return row(protocol, CellFieldKind.PCI, pci?.toString(), details, CellFieldKind.SS_RSRP, ssRsrp, narfcn, pci)
    }

    private fun lte(record: CellularRecordWrapper): SortableRow {
        val data = (record.cellularRecord as LteRecord).data
        val protocol = CellularProtocol.LTE
        val earfcn = if (data.hasEarfcn()) data.earfcn.value else null
        val band = earfcn?.let { CellularUtils.downlinkEarfcnToBand(it) }?.takeIf { it != -1 }
        val pci = if (data.hasPci()) data.pci.value else null
        val rsrp = if (data.hasRsrp()) data.rsrp.value.toInt() else null
        val rsrq = if (data.hasRsrq()) data.rsrq.value.toInt() else null
        val details = listOfNotNull(
            earfcn?.let { CellField(CellFieldKind.EARFCN, it.toString()) },
            band?.let { CellField(CellFieldKind.BAND, it.toString()) },
            rsrq?.let { ServingCellMapper.signal(protocol, CellFieldKind.RSRQ, it, ValueUnit.DB) },
            if (data.hasTa()) CellField(CellFieldKind.TA, data.ta.value.toString()) else null,
        )
        return row(protocol, CellFieldKind.PCI, pci?.toString(), details, CellFieldKind.RSRP, rsrp, earfcn, pci)
    }

    private fun umts(record: CellularRecordWrapper): SortableRow {
        val data = (record.cellularRecord as UmtsRecord).data
        val uarfcn = if (data.hasUarfcn()) data.uarfcn.value else null
        val psc = if (data.hasPsc()) data.psc.value else null
        val rscp = if (data.hasRscp()) data.rscp.value.toInt() else null
        val details = listOfNotNull(uarfcn?.let { CellField(CellFieldKind.UARFCN, it.toString()) })
        return row(CellularProtocol.UMTS, CellFieldKind.PSC, psc?.toString(), details, CellFieldKind.RSCP, rscp, uarfcn, psc)
    }

    private fun gsm(record: CellularRecordWrapper): SortableRow {
        val data = (record.cellularRecord as GsmRecord).data
        val arfcn = if (data.hasArfcn()) data.arfcn.value else null
        val bsic = if (data.hasBsic()) data.bsic.value else null
        val rssi = if (data.hasSignalStrength()) data.signalStrength.value.toInt() else null
        val details = listOfNotNull(arfcn?.let { CellField(CellFieldKind.ARFCN, it.toString()) })
        val bsicText = bsic?.let { ParserUtils.bsicToString(it) }?.ifEmpty { null }
        return row(CellularProtocol.GSM, CellFieldKind.BSIC, bsicText, details, CellFieldKind.RSSI, rssi, arfcn, bsic)
    }

    private fun row(
        protocol: CellularProtocol,
        titleKind: CellFieldKind,
        title: String?,
        details: List<CellField>,
        signalKind: CellFieldKind,
        signal: Int?,
        channel: Int?,
        code: Int?,
    ) = SortableRow(
        row = NeighborRow(
            title = CellField(titleKind, title),
            details = details,
            signal = ServingCellMapper.signal(protocol, signalKind, signal, ValueUnit.DBM),
        ),
        signal = signal,
        channel = channel,
        code = code,
    )
}
