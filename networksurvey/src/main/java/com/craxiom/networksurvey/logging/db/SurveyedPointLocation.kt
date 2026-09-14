package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper

/**
 * The location fields a survey batch contributes to the surveyed places gate. Every record in a
 * batch shares one fix, so the first record that carries a non-zero position stands in for the
 * batch.
 */
data class SurveyedPointLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Int,
    val speedMps: Float,
) {
    companion object {
        @JvmStatic
        fun fromCellular(batch: List<CellularRecordWrapper>): SurveyedPointLocation? {
            for (wrapper in batch) {
                val location = when (wrapper.cellularProtocol) {
                    CellularProtocol.GSM -> (wrapper.cellularRecord as GsmRecord).data
                        .let { of(it.latitude, it.longitude, it.accuracy, it.speed) }

                    CellularProtocol.CDMA -> (wrapper.cellularRecord as CdmaRecord).data
                        .let { of(it.latitude, it.longitude, it.accuracy, it.speed) }

                    CellularProtocol.UMTS -> (wrapper.cellularRecord as UmtsRecord).data
                        .let { of(it.latitude, it.longitude, it.accuracy, it.speed) }

                    CellularProtocol.LTE -> (wrapper.cellularRecord as LteRecord).data
                        .let { of(it.latitude, it.longitude, it.accuracy, it.speed) }

                    CellularProtocol.NR -> (wrapper.cellularRecord as NrRecord).data
                        .let { of(it.latitude, it.longitude, it.accuracy, it.speed) }

                    else -> null
                }
                if (location != null) return location
            }
            return null
        }

        @JvmStatic
        fun fromWifi(batch: List<WifiRecordWrapper>): SurveyedPointLocation? {
            for (wrapper in batch) {
                val data = wrapper.wifiBeaconRecord?.data ?: continue
                of(data.latitude, data.longitude, data.accuracy, data.speed)?.let { return it }
            }
            return null
        }

        @JvmStatic
        fun fromBluetooth(batch: List<BluetoothRecord>): SurveyedPointLocation? {
            for (record in batch) {
                val data = record.data
                of(data.latitude, data.longitude, data.accuracy, data.speed)?.let { return it }
            }
            return null
        }

        private fun of(
            latitude: Double,
            longitude: Double,
            accuracy: Int,
            speed: Float
        ): SurveyedPointLocation? =
            if (latitude == 0.0 && longitude == 0.0) null else SurveyedPointLocation(
                latitude,
                longitude,
                accuracy,
                speed
            )
    }
}
