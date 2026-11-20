package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.logging.db.SurveyedPointLocation
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.map.ServingCellSummary

/**
 * Builds the saved row for a tower found not in the tower database from its serving-cell record.
 * The operator, band and signal come from [ServingCellSummary], so the New towers page shows the
 * same values the Map's serving-cell card showed.
 */
object NewTowerFindFactory {

    /**
     * @param key The tower's identity.
     * @param servingCell The serving-cell record the tower was found in.
     * @param sessionStartedAt When the upload session that found it started.
     * @param now The time of the find.
     */
    fun create(key: TowerKey, servingCell: CellularRecordWrapper, sessionStartedAt: Long, now: Long): NewTowerFindEntity {
        val summary = ServingCellSummary.from(ServingCellInfo(servingCell, 0), null, emptySet())
        val location = SurveyedPointLocation.fromCellular(listOf(servingCell))
        val (channel, pci) = channelAndPci(servingCell)
        return NewTowerFindEntity().apply {
            mcc = key.mcc
            mnc = key.mnc
            area = key.area
            cellId = key.cellId
            radio = key.radio
            firstSeenAt = now
            lastSeenAt = now
            this.sessionStartedAt = sessionStartedAt
            latitude = location?.latitude
            longitude = location?.longitude
            accuracy = location?.accuracy?.takeIf { it > 0 }?.toFloat()
            operatorName = summary?.operator
            band = summary?.lteBand?.toString() ?: summary?.nrBands
            signalDbm = summary?.signalDbm
            this.channel = channel
            this.pci = pci
            inDatabase = false
            lastCheckedAt = now
        }
    }

    /** @return The record's channel number and physical cell identity, each null when unknown. */
    private fun channelAndPci(wrapper: CellularRecordWrapper): Pair<Int?, Int?> =
        when (val record = wrapper.cellularRecord) {
            is LteRecord -> record.data.let { data ->
                (if (data.hasEarfcn()) data.earfcn.value else null) to (if (data.hasPci()) data.pci.value else null)
            }

            is NrRecord -> record.data.let { data ->
                (if (data.hasNarfcn()) data.narfcn.value else null) to (if (data.hasPci()) data.pci.value else null)
            }

            is UmtsRecord -> record.data.let { data ->
                (if (data.hasUarfcn()) data.uarfcn.value else null) to (if (data.hasPsc()) data.psc.value else null)
            }

            is GsmRecord -> record.data.let { data ->
                (if (data.hasArfcn()) data.arfcn.value else null) to (if (data.hasBsic()) data.bsic.value else null)
            }

            else -> null to null
        }
}
