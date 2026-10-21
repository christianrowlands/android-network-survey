package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellSighting
import com.craxiom.networksurvey.fragments.model.NrSecondaryCellViewState
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.NetworkTechnologyInfo
import com.craxiom.networksurvey.model.NrRecordWrapper
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.TelephonyStateUtils

/**
 * Everything the Cellular view knows about one SIM, rebuilt as scans arrive. Immutable: the view
 * model replaces it inside a state flow update, so the reducers here must stay pure.
 *
 * @param technology     The latest technology snapshot (hero, pills, carrier aggregation).
 * @param carrier        The last serving cell's operator name; kept through a scan with no cells,
 *                       as the old screen kept it above a No service hero.
 * @param serving        The serving cell card, or null when the last scan had no cells.
 * @param servingRecord  The record behind [serving], for the map's serving cell.
 * @param neighbors      The neighbor sections of the last scan.
 * @param nrCard         The NR secondary cell card, or null to hide it.
 * @param nrLastSeenAgeMs How long ago the idle NR cell was seen, or null when the card is not idle.
 * @param nrSighting     The last live NR secondary cell, kept for the idle state.
 * @param scanned        True once a scan for this SIM has arrived.
 * @param scanCount      How many scans for this SIM have arrived.
 */
data class CellularSimData(
    val technology: NetworkTechnologyInfo? = null,
    val carrier: String? = null,
    val serving: ServingCellDetails? = null,
    val servingRecord: CellularRecordWrapper? = null,
    val neighbors: List<NeighborSection> = emptyList(),
    val nrCard: NrSecondaryCellViewState? = null,
    val nrLastSeenAgeMs: Long? = null,
    val nrSighting: NrSecondaryCellSighting = NrSecondaryCellSighting.NONE,
    val scanned: Boolean = false,
    val scanCount: Int = 0,
) {

    /** @return This SIM with the technology snapshot sent just before the next scan's records. */
    fun withTechnology(info: NetworkTechnologyInfo): CellularSimData = copy(technology = info)

    /**
     * @return This SIM after one scan.
     *
     * An empty scan clears the cells (the phone sees none, or the scan failed), as the old screen
     * did. A scan with cells but no serving cell keeps the last serving cell, which avoids a
     * flicker when the modem leaves it out of one report.
     */
    fun withBatch(batch: List<CellularRecordWrapper>, nowMs: Long): CellularSimData {
        if (batch.isEmpty()) {
            return CellularSimData(technology = technology, carrier = carrier, scanned = true, scanCount = scanCount + 1)
        }

        // The last serving record wins, as it did when the old screen set each one in turn.
        val servingRecord = batch.lastOrNull { it.cellularProtocol != CellularProtocol.NONE && isServing(it) }
        val shownServing = batch.asReversed().firstNotNullOfOrNull { if (isServing(it)) ServingCellMapper.map(it) else null }
        val nonServingNr = batch.filter { it.cellularProtocol == CellularProtocol.NR && !isServing(it) }
            .filterIsInstance<NrRecordWrapper>()
        val secondary = CellularUtils.selectSecondaryServingNrCell(nonServingNr)
        val neighbors = batch.filter { !isServing(it) && it !== secondary }

        val nr = NrSecondaryCellResolver.resolve(
            secondary = secondary,
            nrMode = technology?.nrMode() ?: TelephonyStateUtils.NrMode.NONE,
            servingCellId = CellularUtils.getTowerId(servingRecord),
            lastSighting = nrSighting,
            nowMs = nowMs,
        )

        return copy(
            carrier = shownServing?.carrier ?: carrier,
            serving = shownServing ?: serving,
            servingRecord = servingRecord ?: this.servingRecord,
            neighbors = CellularNeighbors.sections(neighbors),
            nrCard = nr.state,
            nrLastSeenAgeMs = nr.lastSeenAgeMs,
            nrSighting = nr.sighting,
            scanned = true,
            scanCount = scanCount + 1,
        )
    }

    companion object {
        /** @return True when the record's serving cell flag is set. */
        fun isServing(record: CellularRecordWrapper): Boolean = when (record.cellularProtocol) {
            CellularProtocol.GSM -> (record.cellularRecord as GsmRecord).data.let { it.hasServingCell() && it.servingCell.value }
            CellularProtocol.UMTS -> (record.cellularRecord as UmtsRecord).data.let { it.hasServingCell() && it.servingCell.value }
            CellularProtocol.LTE -> (record.cellularRecord as LteRecord).data.let { it.hasServingCell() && it.servingCell.value }
            CellularProtocol.NR -> (record.cellularRecord as NrRecord).data.let { it.hasServingCell() && it.servingCell.value }
            CellularProtocol.CDMA -> CellularUtils.isServingCell(record.cellularRecord)
            else -> false
        }
    }
}
