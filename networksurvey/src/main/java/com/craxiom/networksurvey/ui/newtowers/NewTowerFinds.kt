package com.craxiom.networksurvey.ui.newtowers

import androidx.annotation.StringRes
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.SurveyedPointDetails
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.map.CellMetric
import com.craxiom.networksurvey.ui.map.ServingCellSummary
import com.craxiom.networksurvey.util.SignalBuckets

/*
 * What the New towers page works out from a saved find: its session group, its tower key for a
 * re-check, and the parts of its identity the rows and the detail sheet show.
 */

/**
 * The towers one Community upload session found, under a heading with the session's start.
 *
 * @property startedAt Epoch milliseconds the session started ([NewTowerFindEntity.sessionStartedAt]).
 * @property finds The session's finds, newest first.
 */
data class NewTowerSession(val startedAt: Long, val finds: List<NewTowerFindEntity>)

/** Newest first find first; the id breaks a tie so the order never shuffles. */
private val NEWEST_FIRST = compareByDescending<NewTowerFindEntity> { it.firstSeenAt }.thenByDescending { it.id }

/**
 * Groups [finds] by the session that found them: the newest session first, and the newest find
 * first within each session.
 */
internal fun groupBySession(finds: List<NewTowerFindEntity>): List<NewTowerSession> =
    finds.groupBy { it.sessionStartedAt }
        .map { (startedAt, sessionFinds) -> NewTowerSession(startedAt, sessionFinds.sortedWith(NEWEST_FIRST)) }
        .sortedByDescending { it.startedAt }

/** The find's technology, or null when the saved radio name is not one the app knows. */
internal val NewTowerFindEntity.protocol: CellularProtocol?
    get() = CellularProtocol.entries.firstOrNull { it.name == radio }

/** The key the tower service is asked about, or null when the saved radio is unknown. */
internal fun NewTowerFindEntity.towerKey(): TowerKey? =
    protocol?.let { TowerKey(mcc, mnc, area, cellId, it) }

/** The [SignalBuckets] bucket of the signal at the first find, the same scale the Map card uses. */
internal val NewTowerFindEntity.signalBucket: Int
    get() {
        val dBm = signalDbm ?: return SignalBuckets.UNKNOWN
        val technology = protocol ?: return SignalBuckets.UNKNOWN
        return SignalBuckets.cellular(SurveyedPointDetails.protocolOf(technology), dBm)
    }

/** The survey point `PROTOCOL_` constant of the find, for the shared signal metric names. */
internal val NewTowerFindEntity.surveyProtocol: Int
    get() = protocol?.let { SurveyedPointDetails.protocolOf(it) } ?: 0

/** The LTE band number, or null for other technologies or an unknown band. */
internal val NewTowerFindEntity.lteBand: Int?
    get() = if (protocol == CellularProtocol.LTE) band?.toIntOrNull() else null

/** The NR band designators ("n78", "n77/n78"), or null for other technologies or an unknown band. */
internal val NewTowerFindEntity.nrBands: String?
    get() = if (protocol == CellularProtocol.NR) band?.takeIf { it.isNotBlank() } else null

/** The LTE eNB ID and sector, or null for other technologies. */
internal val NewTowerFindEntity.enbSector: Pair<Int, Int>?
    get() = if (protocol == CellularProtocol.LTE) ServingCellSummary.enbSectorOf(cellId) else null

/** The cell's ID as the Map card names it for non-LTE cells ("NCI 123"), or null for LTE. */
internal val NewTowerFindEntity.identity: CellMetric?
    get() = protocol?.let { ServingCellSummary.identityOf(it, cellId) }

/** True when the find has the phone's location at the first find. */
internal val NewTowerFindEntity.hasLocation: Boolean
    get() = latitude != null && longitude != null

/** "TAC %1$d" for LTE and NR, "LAC %1$d" for GSM and UMTS. */
@get:StringRes
internal val NewTowerFindEntity.areaFormat: Int
    get() = when (protocol) {
        CellularProtocol.LTE, CellularProtocol.NR -> R.string.survey_point_tac
        else -> R.string.survey_point_lac
    }

/** The channel's name: EARFCN, NARFCN, UARFCN or ARFCN. */
@get:StringRes
internal val NewTowerFindEntity.channelLabel: Int
    get() = when (protocol) {
        CellularProtocol.LTE -> R.string.earfcn_label
        CellularProtocol.NR -> R.string.narfcn_label
        CellularProtocol.UMTS -> R.string.uarfcn_label
        else -> R.string.arfcn_label
    }

/** The physical identity's name: PCI (LTE and NR), PSC (UMTS) or BSIC (GSM). */
@get:StringRes
internal val NewTowerFindEntity.physicalIdLabel: Int
    get() = when (protocol) {
        CellularProtocol.UMTS -> R.string.psc_label
        CellularProtocol.GSM -> R.string.bsic_label
        else -> R.string.pci_label
    }
