package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.ConnectionStatus
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.ui.cellular.model.TowerIdentity
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.SignalBuckets

/**
 * The descriptive columns of a surveyed point: what was heard at that step, so the map can color
 * by it and the tap sheet can explain it. Extracted once per accepted step from the full batch.
 * Every field defaults to "unknown".
 */
data class SurveyedPointDetails(
    val protocol: Int = SurveyedPointEntity.PROTOCOL_NONE,
    val plmn: String? = null,
    val provider: String? = null,
    val area: Int = 0,
    val cid: Long = 0,
    val cellId: String? = null,
    val signal: Int = 0,
    val signal2: Int = 0,
    val signalBucket: Int = SignalBuckets.UNKNOWN,
    val nrScg: Int = 0,
    val deviceCount: Int = 0,
    val label: String? = null,
    val missionId: String? = null,
) {
    /** The identity fields of one cellular record, independent of its protocol. */
    private class CellIdentity(
        val mcc: String,
        val mnc: String,
        val area: Int,
        val cid: Long,
        val provider: String,
        val missionId: String
    )

    companion object {
        /**
         * Describes the batch by its serving cell (the LTE anchor under 5G NSA, the NR cell under
         * SA). A batch without a serving record still yields a point, with an unknown technology,
         * so coverage is never lost.
         */
        @JvmStatic
        fun fromCellular(batch: List<CellularRecordWrapper>): SurveyedPointDetails {
            val nrScg = if (batch.any { isNrSecondaryServing(it) }) 1 else 0
            val missionId =
                batch.firstNotNullOfOrNull { identityOf(it)?.missionId?.ifBlank { null } }
            val serving = batch.firstOrNull { CellularUtils.isServingCell(it.cellularRecord) }
                ?: return SurveyedPointDetails(nrScg = nrScg, missionId = missionId)

            val protocol = protocolOf(serving.cellularProtocol)
            val identity = identityOf(serving)
            val signalInfo = CellularUtils.getSignalInfo(serving)
            // UMTS reports RSSI as signal one and RSCP as signal two; RSCP is the better ramp input.
            val primary = when {
                serving.cellularProtocol == CellularProtocol.UMTS && signalInfo != null && signalInfo.signalTwo != 0 -> signalInfo.signalTwo
                else -> signalInfo?.signalOne ?: 0
            }
            val secondary = when {
                serving.cellularProtocol == CellularProtocol.UMTS -> signalInfo?.signalOne ?: 0
                else -> signalInfo?.signalTwo ?: 0
            }

            return SurveyedPointDetails(
                protocol = protocol,
                plmn = identity?.let { "${it.mcc}-${it.mnc}" },
                provider = identity?.provider?.ifBlank { null },
                area = identity?.area ?: 0,
                cid = identity?.cid ?: 0,
                cellId = identity?.let { TowerIdentity.towerId(it.mcc, it.mnc, it.area, it.cid) },
                signal = primary,
                signal2 = secondary,
                signalBucket = SignalBuckets.cellular(protocol, primary),
                nrScg = nrScg,
                missionId = missionId,
            )
        }

        /** Describes the batch by its strongest access point. */
        @JvmStatic
        fun fromWifi(batch: List<WifiRecordWrapper>): SurveyedPointDetails {
            val records = batch.mapNotNull { it.wifiBeaconRecord?.data }
            val strongest =
                records.maxByOrNull { it.signalStrength.value } ?: return SurveyedPointDetails()
            val signal = strongest.signalStrength.value.toInt()
            return SurveyedPointDetails(
                signal = signal,
                signalBucket = SignalBuckets.rssi(signal),
                deviceCount = records.size,
                label = strongest.ssid.ifBlank { strongest.bssid }.ifBlank { null },
                missionId = records.firstNotNullOfOrNull { it.missionId.ifBlank { null } },
            )
        }

        /** Describes the batch by its strongest device. */
        @JvmStatic
        fun fromBluetooth(batch: List<BluetoothRecord>): SurveyedPointDetails {
            val records = batch.map { it.data }
            val strongest =
                records.maxByOrNull { it.signalStrength.value } ?: return SurveyedPointDetails()
            val signal = strongest.signalStrength.value.toInt()
            return SurveyedPointDetails(
                signal = signal,
                signalBucket = SignalBuckets.rssi(signal),
                deviceCount = records.size,
                label = strongest.otaDeviceName.ifBlank { strongest.sourceAddress }
                    .ifBlank { null },
                missionId = records.firstNotNullOfOrNull { it.missionId.ifBlank { null } },
            )
        }

        private fun protocolOf(protocol: CellularProtocol): Int = when (protocol) {
            CellularProtocol.GSM -> SurveyedPointEntity.PROTOCOL_GSM
            CellularProtocol.CDMA -> SurveyedPointEntity.PROTOCOL_CDMA
            CellularProtocol.UMTS -> SurveyedPointEntity.PROTOCOL_UMTS
            CellularProtocol.LTE -> SurveyedPointEntity.PROTOCOL_LTE
            CellularProtocol.NR -> SurveyedPointEntity.PROTOCOL_NR
            else -> SurveyedPointEntity.PROTOCOL_NONE
        }

        private fun isNrSecondaryServing(wrapper: CellularRecordWrapper): Boolean =
            wrapper.cellularProtocol == CellularProtocol.NR &&
                    (wrapper.cellularRecord as NrRecord).data.connectionStatus == ConnectionStatus.SECONDARY_SERVING

        private fun identityOf(wrapper: CellularRecordWrapper): CellIdentity? =
            when (wrapper.cellularProtocol) {
                CellularProtocol.GSM -> (wrapper.cellularRecord as GsmRecord).data.let { d ->
                    val mccMnc = NsUtils.extractMccMncStrings(
                        d.hasPlmn(),
                        if (d.hasPlmn()) d.plmn.value else null,
                        d.mcc.value,
                        d.mnc.value
                    )
                    CellIdentity(
                        mccMnc[0],
                        mccMnc[1],
                        d.lac.value,
                        d.ci.value.toLong(),
                        d.provider,
                        d.missionId
                    )
                }

                CellularProtocol.UMTS -> (wrapper.cellularRecord as UmtsRecord).data.let { d ->
                    val mccMnc = NsUtils.extractMccMncStrings(
                        d.hasPlmn(),
                        if (d.hasPlmn()) d.plmn.value else null,
                        d.mcc.value,
                        d.mnc.value
                    )
                    CellIdentity(
                        mccMnc[0],
                        mccMnc[1],
                        d.lac.value,
                        d.cid.value.toLong(),
                        d.provider,
                        d.missionId
                    )
                }

                CellularProtocol.LTE -> (wrapper.cellularRecord as LteRecord).data.let { d ->
                    val mccMnc = NsUtils.extractMccMncStrings(
                        d.hasPlmn(),
                        if (d.hasPlmn()) d.plmn.value else null,
                        d.mcc.value,
                        d.mnc.value
                    )
                    CellIdentity(
                        mccMnc[0],
                        mccMnc[1],
                        d.tac.value,
                        d.eci.value.toLong(),
                        d.provider,
                        d.missionId
                    )
                }

                CellularProtocol.NR -> (wrapper.cellularRecord as NrRecord).data.let { d ->
                    val mccMnc = NsUtils.extractMccMncStrings(
                        d.hasPlmn(),
                        if (d.hasPlmn()) d.plmn.value else null,
                        d.mcc.value,
                        d.mnc.value
                    )
                    CellIdentity(
                        mccMnc[0],
                        mccMnc[1],
                        d.tac.value,
                        d.nci.value,
                        d.provider,
                        d.missionId
                    )
                }

                else -> null
            }
    }
}
