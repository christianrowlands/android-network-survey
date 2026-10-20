package com.craxiom.networksurvey.ui.map

import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.SignalBuckets

// The metric labels are 3GPP terms, the same in every language.
private const val LABEL_RSSI = "RSSI"
private const val LABEL_RSCP = "RSCP"
private const val LABEL_ECIO = "ECIO"
private const val LABEL_RSRP = "RSRP"
private const val LABEL_RSRQ = "RSRQ"
private const val LABEL_SS_RSRP = "SS-RSRP"
private const val LABEL_SS_RSRQ = "SS-RSRQ"
private const val LABEL_PLMN = "MCC-MNC"
private const val LABEL_LAC = "LAC"
private const val LABEL_TAC = "TAC"
private const val LABEL_CID = "CID"
private const val LABEL_ECI = "ECI"
private const val LABEL_NCI = "NCI"
private const val LABEL_ARFCN = "ARFCN"
private const val LABEL_UARFCN = "UARFCN"
private const val LABEL_EARFCN = "EARFCN"
private const val LABEL_NARFCN = "NARFCN"
private const val LABEL_TA = "TA"
private const val LABEL_SID_NID = "SID/NID"
private const val LABEL_BSID = "BSID"

/** The prefix of an NR band designator ("n78"), and the separator between candidate bands. */
private const val NR_BAND_PREFIX = "n"
private const val NR_BAND_SEPARATOR = "/"

/** One labelled value in the expanded serving-cell card ("RSRP", "-79"). */
data class CellMetric(val label: String, val value: String)

/**
 * What the Map's serving-cell card says about one SIM's serving cell, worked out from its record
 * so the card itself only lays it out.
 *
 * @property operator The operator name the phone reports, or null when it reports none.
 * @property plmn "MCC-MNC", with the MNC's leading zero, or null when unknown.
 * @property lteBand The LTE band number from the EARFCN, or null.
 * @property nrBands The NR band designators from the NARFCN ("n78", or "n77/n78" when ambiguous).
 * @property signalDbm The primary signal (RSRP, SS-RSRP, RSSI or RSCP) in dBm, or null when unknown.
 * @property signalBucket The [SignalBuckets] bucket of [signalDbm].
 * @property enbSector The LTE eNB ID and sector, or null for other technologies.
 * @property identity The cell's ID for other technologies ("NCI", "123"), or null for LTE.
 * @property distanceMeters How far away the serving tower is, when its location is known.
 * @property isNew True when the tower was found new during this Community upload.
 * @property metrics Everything the expanded card lists.
 */
data class ServingCellSummary(
    val subscriptionId: Int,
    val protocol: CellularProtocol,
    val operator: String?,
    val plmn: String?,
    val lteBand: Int?,
    val nrBands: String?,
    val signalDbm: Int?,
    val signalBucket: Int,
    val enbSector: Pair<Int, Int>?,
    val identity: CellMetric?,
    val distanceMeters: Double?,
    val isNew: Boolean,
    val metrics: List<CellMetric>,
) {
    companion object {
        /**
         * @param newTowers The towers found new in this Community upload.
         * @return The summary of [info]'s cell, or null when it has none.
         */
        fun from(info: ServingCellInfo?, distanceMeters: Double?, newTowers: Set<TowerKey>): ServingCellSummary? {
            val wrapper = info?.servingCell ?: return null
            val signal = CellularUtils.getSignalInfo(wrapper)
            val base = ServingCellSummary(
                subscriptionId = info.subscriptionId,
                protocol = wrapper.cellularProtocol,
                operator = null,
                plmn = null,
                lteBand = null,
                nrBands = null,
                signalDbm = null,
                signalBucket = SignalBuckets.UNKNOWN,
                enbSector = null,
                identity = null,
                distanceMeters = distanceMeters,
                isNew = TowerKey.from(wrapper)?.let { it in newTowers } ?: false,
                metrics = emptyList(),
            )
            val primary = signal?.signalOne?.takeIf { it < 0 }
            val secondary = signal?.signalTwo?.takeIf { it < 0 }
            return when (val record = wrapper.cellularRecord) {
                is LteRecord -> record.data.let { data ->
                    val eci = data.eci.value
                    base.copy(
                        operator = data.provider.ifBlank { null },
                        plmn = plmn(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                        lteBand = if (data.hasEarfcn()) CellularUtils.downlinkEarfcnToBand(data.earfcn.value).takeIf { it > 0 } else null,
                        signalDbm = primary,
                        signalBucket = bucket(SurveyedPointEntity.PROTOCOL_LTE, primary),
                        enbSector = if (eci > 0) {
                            CalculationUtils.getEnodebIdFromCellId(eci) to CalculationUtils.getSectorIdFromCellId(eci)
                        } else {
                            null
                        },
                        metrics = listOfNotNull(
                            metric(LABEL_RSRP, primary),
                            metric(LABEL_RSRQ, secondary),
                            plmnMetric(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                            CellMetric(LABEL_TAC, data.tac.value.toString()),
                            CellMetric(LABEL_ECI, eci.toString()),
                            if (data.hasEarfcn()) CellMetric(LABEL_EARFCN, data.earfcn.value.toString()) else null,
                            if (data.hasTa()) CellMetric(LABEL_TA, data.ta.value.toString()) else null,
                        ),
                    )
                }

                is NrRecord -> record.data.let { data ->
                    val bands = if (data.hasNarfcn()) CellularUtils.resolveNrBands(null, data.narfcn.value) else IntArray(0)
                    base.copy(
                        operator = data.provider.ifBlank { null },
                        plmn = plmn(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                        nrBands = bands.takeIf { it.isNotEmpty() }
                            ?.joinToString(NR_BAND_SEPARATOR) { NR_BAND_PREFIX + it },
                        signalDbm = primary,
                        signalBucket = bucket(SurveyedPointEntity.PROTOCOL_NR, primary),
                        identity = CellMetric(LABEL_NCI, data.nci.value.toString()),
                        metrics = listOfNotNull(
                            metric(LABEL_SS_RSRP, primary),
                            metric(LABEL_SS_RSRQ, secondary),
                            plmnMetric(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                            CellMetric(LABEL_TAC, data.tac.value.toString()),
                            CellMetric(LABEL_NCI, data.nci.value.toString()),
                            if (data.hasNarfcn()) CellMetric(LABEL_NARFCN, data.narfcn.value.toString()) else null,
                            if (data.hasTa()) CellMetric(LABEL_TA, data.ta.value.toString()) else null,
                        ),
                    )
                }

                is GsmRecord -> record.data.let { data ->
                    base.copy(
                        operator = data.provider.ifBlank { null },
                        plmn = plmn(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                        signalDbm = primary,
                        signalBucket = bucket(SurveyedPointEntity.PROTOCOL_GSM, primary),
                        identity = CellMetric(LABEL_CID, data.ci.value.toString()),
                        metrics = listOfNotNull(
                            metric(LABEL_RSSI, primary),
                            plmnMetric(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                            CellMetric(LABEL_LAC, data.lac.value.toString()),
                            CellMetric(LABEL_CID, data.ci.value.toString()),
                            if (data.hasArfcn()) CellMetric(LABEL_ARFCN, data.arfcn.value.toString()) else null,
                            if (data.hasTa()) CellMetric(LABEL_TA, data.ta.value.toString()) else null,
                        ),
                    )
                }

                is UmtsRecord -> record.data.let { data ->
                    base.copy(
                        operator = data.provider.ifBlank { null },
                        plmn = plmn(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                        signalDbm = secondary ?: primary,
                        signalBucket = bucket(SurveyedPointEntity.PROTOCOL_UMTS, secondary ?: primary),
                        identity = CellMetric(LABEL_CID, data.cid.value.toString()),
                        metrics = listOfNotNull(
                            metric(LABEL_RSSI, primary),
                            metric(LABEL_RSCP, secondary),
                            plmnMetric(data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0),
                            CellMetric(LABEL_LAC, data.lac.value.toString()),
                            CellMetric(LABEL_CID, data.cid.value.toString()),
                            if (data.hasUarfcn()) CellMetric(LABEL_UARFCN, data.uarfcn.value.toString()) else null,
                        ),
                    )
                }

                is CdmaRecord -> record.data.let { data ->
                    base.copy(
                        operator = data.provider.ifBlank { null },
                        identity = CellMetric(LABEL_BSID, data.bsid.value.toString()),
                        metrics = listOfNotNull(
                            metric(LABEL_ECIO, primary),
                            CellMetric(LABEL_SID_NID, "${data.sid.value}/${data.nid.value}"),
                            CellMetric(LABEL_BSID, data.bsid.value.toString()),
                        ),
                    )
                }

                else -> base
            }
        }

        private fun plmn(hasPlmn: Boolean, plmn: String?, mcc: Int, mnc: Int): String? = when {
            hasPlmn && !plmn.isNullOrBlank() -> plmn
            mcc > 0 -> "$mcc-$mnc"
            else -> null
        }

        private fun plmnMetric(hasPlmn: Boolean, plmn: String?, mcc: Int, mnc: Int): CellMetric? =
            plmn(hasPlmn, plmn, mcc, mnc)?.let { CellMetric(LABEL_PLMN, it) }

        private fun metric(label: String, value: Int?): CellMetric? = value?.let { CellMetric(label, it.toString()) }

        private fun bucket(protocol: Int, dBm: Int?): Int =
            dBm?.let { SignalBuckets.cellular(protocol, it) } ?: SignalBuckets.UNKNOWN
    }
}
