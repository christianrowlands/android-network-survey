package com.craxiom.networksurvey.model

import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.util.NsUtils

/**
 * Identifies one cell the way the new-tower check asks the tower service about it: the MCC and MNC
 * as strings (so an MNC of "01" keeps its leading zero), the area (TAC for LTE and NR, LAC for GSM
 * and UMTS), the cell ID (ECI, NCI, CI or CID) and the radio technology.
 *
 * The new-tower detector and the Map's serving-cell card both build it, so a tower the detector
 * reports as new is the same key the card looks up.
 */
data class TowerKey(
    val mcc: String,
    val mnc: String,
    val area: Int,
    val cellId: Long,
    val protocol: CellularProtocol,
) {
    /** The radio technology name the tower service expects ("LTE", "NR", "GSM" or "UMTS"). */
    val radio: String get() = protocol.name

    override fun toString(): String = "$mcc-$mnc-$area-$cellId ($radio)"

    companion object {
        /**
         * @return The key of [wrapper]'s cell, or null when it cannot be identified: CDMA (the
         * tower service has no CDMA towers), a missing or zero MCC, or a missing cell ID.
         */
        @JvmStatic
        fun from(wrapper: CellularRecordWrapper?): TowerKey? {
            val record = wrapper?.cellularRecord ?: return null
            val key = when (record) {
                is LteRecord -> record.data.let { data ->
                    key(
                        data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                        if (data.hasTac()) data.tac.value else 0,
                        if (data.hasEci()) data.eci.value.toLong() else 0L,
                        CellularProtocol.LTE,
                    )
                }

                is NrRecord -> record.data.let { data ->
                    key(
                        data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                        if (data.hasTac()) data.tac.value else 0,
                        if (data.hasNci()) data.nci.value else 0L,
                        CellularProtocol.NR,
                    )
                }

                is GsmRecord -> record.data.let { data ->
                    key(
                        data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                        if (data.hasLac()) data.lac.value else 0,
                        if (data.hasCi()) data.ci.value.toLong() else 0L,
                        CellularProtocol.GSM,
                    )
                }

                is UmtsRecord -> record.data.let { data ->
                    key(
                        data.hasPlmn(), data.plmn?.value, data.mcc?.value ?: 0, data.mnc?.value ?: 0,
                        if (data.hasLac()) data.lac.value else 0,
                        if (data.hasCid()) data.cid.value.toLong() else 0L,
                        CellularProtocol.UMTS,
                    )
                }

                else -> null
            }
            return key?.takeIf { it.mcc.isNotEmpty() && it.mcc != "0" && it.cellId > 0 }
        }

        private fun key(
            hasPlmn: Boolean,
            plmn: String?,
            mcc: Int,
            mnc: Int,
            area: Int,
            cellId: Long,
            protocol: CellularProtocol,
        ): TowerKey {
            val (mccString, mncString) = NsUtils.extractMccMncStrings(hasPlmn, plmn, mcc, mnc)
            return TowerKey(mccString, mncString, area, cellId, protocol)
        }
    }
}
