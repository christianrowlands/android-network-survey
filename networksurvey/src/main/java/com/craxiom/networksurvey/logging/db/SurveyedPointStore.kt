package com.craxiom.networksurvey.logging.db

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.networksurvey.logging.db.SurveyedPointStore.Companion.clearAll
import com.craxiom.networksurvey.logging.db.dao.SurveyedPointDao
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.logging.db.uploader.UploadResult
import com.craxiom.networksurvey.logging.db.uploader.UploadResultBundle
import com.craxiom.networksurvey.logging.db.uploader.UploadTarget
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper
import timber.log.Timber

/**
 * Writes survey points for one upload pipeline. Each pipeline's data store owns an instance and
 * calls [observe] from its own executor thread for every batch it receives; the gate decides
 * which batches become rows.
 *
 * The upload workers use the static helpers to mark rows as uploaded by time watermark and to
 * keep the table under its cap. Neither the store nor the helpers own a thread.
 */
class SurveyedPointStore @JvmOverloads constructor(
    private val dao: SurveyedPointDao,
    private val source: Int,
    private val gate: SurveyedPointGate = SurveyedPointGate(),
) {
    private var seenClearGeneration = clearGeneration

    /**
     * Records a survey point for [kind] when the device has moved far enough from the last one.
     *
     * @param timeMs the observation time; for NS Analytics this must be no later than the queue
     * timestamps of the records in the same batch, or the watermark marking will miss the point.
     */
    fun observe(
        kind: Int,
        latitude: Double,
        longitude: Double,
        accuracy: Int,
        speedMps: Float,
        timeMs: Long,
        details: () -> SurveyedPointDetails = { SurveyedPointDetails() },
    ) {
        if (seenClearGeneration != clearGeneration) {
            // The user cleared the table since this store last wrote; start a fresh trail
            gate.reset()
            seenClearGeneration = clearGeneration
        }
        if (!gate.shouldRecord(kind, latitude, longitude, accuracy, speedMps)) return

        // Extracted only for accepted steps, so the batch walk costs nothing on the rest.
        val d = details()
        val entity = SurveyedPointEntity()
        entity.latitude = latitude
        entity.longitude = longitude
        entity.time = timeMs
        entity.source = source
        entity.observedMask = kind
        entity.uploadedMask = 0
        entity.protocol = d.protocol
        entity.plmn = d.plmn
        entity.provider = d.provider
        entity.area = d.area
        entity.cid = d.cid
        entity.cellId = d.cellId
        entity.signal = d.signal
        entity.signal2 = d.signal2
        entity.signalBucket = d.signalBucket
        entity.nrScg = d.nrScg
        entity.deviceCount = d.deviceCount
        entity.label = d.label
        entity.missionId = d.missionId
        try {
            dao.insert(entity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to write a surveyed point")
        }
    }

    /** Records the batch's shared fix as a cellular survey point, subject to the gate. */
    fun observeCellular(batch: List<CellularRecordWrapper>, timeMs: Long) {
        SurveyedPointLocation.fromCellular(batch)?.let {
            observe(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                it,
                timeMs
            ) { SurveyedPointDetails.fromCellular(batch) }
        }
    }

    /** Records the batch's shared fix as a Wi-Fi survey point, subject to the gate. */
    fun observeWifi(batch: List<WifiRecordWrapper>, timeMs: Long) {
        SurveyedPointLocation.fromWifi(batch)?.let {
            observe(SurveyedPointEntity.OBSERVED_WIFI, it, timeMs) {
                SurveyedPointDetails.fromWifi(
                    batch
                )
            }
        }
    }

    /** Records the batch's shared fix as a Bluetooth survey point, subject to the gate. */
    fun observeBluetooth(batch: List<BluetoothRecord>, timeMs: Long) {
        SurveyedPointLocation.fromBluetooth(batch)?.let {
            observe(
                SurveyedPointEntity.OBSERVED_BLUETOOTH,
                it,
                timeMs
            ) { SurveyedPointDetails.fromBluetooth(batch) }
        }
    }

    private fun observe(
        kind: Int,
        location: SurveyedPointLocation,
        timeMs: Long,
        details: () -> SurveyedPointDetails
    ) =
        observe(
            kind,
            location.latitude,
            location.longitude,
            location.accuracy,
            location.speedMps,
            timeMs,
            details
        )

    /** Forgets the movement anchors so the next batch of each kind is recorded. */
    fun reset() = gate.reset()

    companion object {
        /** Hard cap on rows; roughly 350 driving hours per pipeline before trimming starts. */
        const val MAX_ROWS = 500_000

        private const val TRIM_CHUNK = 5_000

        /** Bumped by [clearAll] so every live store forgets its movement anchors. */
        @Volatile
        private var clearGeneration = 0

        /** Deletes every survey point and makes running surveys start a fresh trail. */
        @JvmStatic
        fun clearAll(dao: SurveyedPointDao) {
            dao.clear()
            clearGeneration++
        }

        /**
         * Marks every community point observed at or before [beforeTime] as delivered to the
         * targets that accepted the run. OpenCelliD never receives Wi-Fi or Bluetooth, so its bit
         * is only set on cellular points.
         */
        @JvmStatic
        fun markCommunityUploaded(
            dao: SurveyedPointDao,
            beforeTime: Long,
            ocid: Boolean,
            beaconDb: Boolean
        ) {
            if (ocid) {
                dao.markUploaded(
                    SurveyedPointEntity.SOURCE_COMMUNITY, beforeTime,
                    SurveyedPointEntity.UPLOADED_OCID, SurveyedPointEntity.OBSERVED_CELLULAR
                )
            }
            if (beaconDb) {
                dao.markUploaded(
                    SurveyedPointEntity.SOURCE_COMMUNITY, beforeTime,
                    SurveyedPointEntity.UPLOADED_BEACONDB, SurveyedPointEntity.OBSERVED_ANY
                )
            }
        }

        /**
         * Called by the community upload worker once every pending record has been accepted, just
         * before it deletes those records. Marks the points each target actually received and
         * keeps the table under its cap.
         *
         * @param uploadStartTime the watermark; points observed after the run started may not
         * have been part of it and stay pending until the next run.
         */
        @JvmStatic
        fun onCommunityUploadCompleted(
            dao: SurveyedPointDao,
            uploadStartTime: Long,
            results: UploadResultBundle
        ) {
            markCommunityUploaded(
                dao, uploadStartTime,
                ocid = results.getResult(UploadTarget.OpenCelliD).isAccepted(),
                beaconDb = results.getResult(UploadTarget.BeaconDB).isAccepted()
            )
            trimIfNeeded(dao)
        }

        private fun UploadResult.isAccepted() =
            this == UploadResult.Success || this == UploadResult.PartiallySucceeded

        /**
         * Marks NS Analytics points up to the newest queue timestamp in an accepted batch.
         *
         * @param inclusive false when the batch was cut by its size limit, because rows sharing
         * that final millisecond may still be waiting in the next batch.
         */
        @JvmStatic
        fun markNsAnalyticsUploaded(dao: SurveyedPointDao, maxTimestamp: Long, inclusive: Boolean) {
            val beforeTime = if (inclusive) maxTimestamp else maxTimestamp - 1
            dao.markUploaded(
                SurveyedPointEntity.SOURCE_NS_ANALYTICS, beforeTime,
                SurveyedPointEntity.UPLOADED_NS_ANALYTICS, SurveyedPointEntity.OBSERVED_ANY
            )
        }

        /**
         * Trims the table back under [maxRows], removing uploaded rows oldest first and only then
         * pending rows. Meant to run from the upload workers, never on the write path.
         */
        @JvmStatic
        @JvmOverloads
        fun trimIfNeeded(dao: SurveyedPointDao, maxRows: Int = MAX_ROWS) {
            var excess = dao.count() - maxRows
            while (excess > 0) {
                val chunk = minOf(excess, TRIM_CHUNK)
                var removed = dao.trimUploadedOldest(chunk)
                if (removed < chunk) removed += dao.trimOldest(chunk - removed)
                if (removed == 0) return
                excess -= removed
            }
        }
    }
}
