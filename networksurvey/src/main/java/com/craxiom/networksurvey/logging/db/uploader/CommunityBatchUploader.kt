package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao
import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity
import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity
import com.craxiom.networksurvey.logging.db.uploader.beacondb.BeaconDbUploadClient
import com.craxiom.networksurvey.logging.db.uploader.ocid.OpenCelliDCsvFormatter
import com.craxiom.networksurvey.logging.db.uploader.ocid.OpenCelliDUploadClient
import com.craxiom.networksurvey.util.PreferenceUtils
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

/**
 * Sends the records saved for the community databases to OpenCelliD and BeaconDB in batches, each
 * target on its own: a record is marked for a target as soon as that target takes it, and a target
 * that fails sits out the rest of the run while the other one keeps going. So an OpenCelliD outage
 * doesn't hold back BeaconDB, and a target that already has a record never gets it again.
 *
 * @param openCelliD True to upload to OpenCelliD.
 * @param anonymousOpenCelliD True to upload to OpenCelliD with the app's shared key.
 * @param beaconDb True to upload to BeaconDB.
 * @param batchSize The most records read and sent at once.
 */
class CommunityBatchUploader @JvmOverloads constructor(
    private val context: Context,
    private val database: SurveyDatabase,
    private val openCelliD: Boolean,
    private val anonymousOpenCelliD: Boolean,
    private val beaconDb: Boolean,
    private val batchSize: Int = BATCH_SIZE,
) {
    private val dao: SurveyRecordDao get() = database.surveyRecordDao()

    /** The targets still uploading in this run; one that fails leaves it. */
    private val active = mutableSetOf<UploadTarget>()

    /**
     * Uploads every waiting record, or as many as the targets take before they fail.
     *
     * @param isStopped True once the work is cancelled.
     * @param onProgress Called with the share of the waiting records handled so far, 0 to 100.
     * @return Each target's result: [UploadResult.Success] once it took everything waiting for it,
     * [UploadResult.NoData] if nothing waited for it, its failure otherwise, and
     * [UploadResult.Cancelled] for both if the work was stopped.
     */
    fun run(isStopped: () -> Boolean, onProgress: (Int) -> Unit): UploadResultBundle {
        val results = UploadResultBundle()
        UploadTarget.entries.forEach { target ->
            val inPlay = target == UploadTarget.OpenCelliD && openCelliD || target == UploadTarget.BeaconDB && beaconDb
            results.setResult(target, if (inPlay) UploadResult.NoData else UploadResult.UploadDisabledForTarget)
            if (inPlay) active += target
        }

        val total = waitingCount().coerceAtLeast(1)
        var handled = 0
        while (active.isNotEmpty()) {
            if (isStopped()) {
                Timber.d("Upload cancelled, stopping the batch loop")
                results.markAllCancelled()
                return results
            }
            val batch = readBatch()
            if (batch.isEmpty()) break
            batch.forEach { upload(it, results, isStopped) }
            handled += batch.sumOf { it.records.size }
            onProgress((handled * PERCENT / total).coerceAtMost(PERCENT))
        }
        // A send interrupted by a cancel reads as a failure; it was a cancel
        if (isStopped()) results.markAllCancelled()
        return results
    }

    /** @return The records waiting for a target still in this run. */
    private fun waitingCount(): Int =
        NsUploaderWorker.getTotalRecordsForUpload(dao, UploadTarget.OpenCelliD in active, UploadTarget.BeaconDB in active)

    /** @return The next records waiting for a target still in this run, at most [batchSize]. */
    private fun readBatch(): List<Batch<*>> {
        val ocid = UploadTarget.OpenCelliD in active
        val beacon = UploadTarget.BeaconDB in active
        val batches = mutableListOf<Batch<*>>()
        var room = batchSize
        for (table in tables()) {
            if (room <= 0) break
            val batch = table.readBatch(ocid, beacon, room) ?: continue
            batches += batch
            room -= batch.records.size
        }
        return batches
    }

    /** Sends [batch] to each target still in the run that doesn't have its records yet. */
    private fun <T> upload(batch: Batch<T>, results: UploadResultBundle, isStopped: () -> Boolean) {
        for (target in active.toList()) {
            if (isStopped()) return
            val waiting = batch.records.filterNot { batch.table.uploaded(it, target) }
            if (waiting.isEmpty()) continue
            if (!batch.table.takes(target)) {
                // Nothing in these records goes to this target, so there is nothing to send
                batch.table.mark(target, waiting)
                continue
            }
            val result = send(target, waiting)
            Timber.i("%s took %d records: %s", target, waiting.size, result)
            if (result == UploadResult.Success) {
                batch.table.mark(target, waiting)
                results.setResult(target, UploadResult.Success)
            } else {
                results.setResult(target, result)
                active -= target
            }
        }
    }

    private fun send(target: UploadTarget, records: List<*>): UploadResult = try {
        val wrapper = UploadRecordsWrapper.createRecordsWrapper(records)
        when (target) {
            UploadTarget.OpenCelliD -> sendToOpenCelliD(wrapper)
            UploadTarget.BeaconDB -> sendToBeaconDb(wrapper)
        }
    } catch (e: IOException) {
        // No route, no DNS, a timeout: the server was never reached
        Timber.w(e, "Could not reach %s", target)
        UploadResult.ConnectionError
    } catch (e: Exception) {
        Timber.e(e, "The upload to %s failed", target)
        UploadResult.Failure
    }

    private fun sendToOpenCelliD(wrapper: UploadRecordsWrapper): UploadResult {
        val apiKey = PreferenceUtils.getOpenCelliDApiKey(context, anonymousOpenCelliD).toRequestBody(MultipartBody.FORM)
        val appId = NsUploaderWorker.OCID_APP_ID.toRequestBody(MultipartBody.FORM)
        val csv = OpenCelliDCsvFormatter.formatRecords(wrapper).toRequestBody(NsUploaderWorker.MEDIA_TYPE_CSV)
        val file = MultipartBody.Part.createFormData(
            OPENCELLID_FILE_FIELD, "NetworkSurvey_measurements_${System.currentTimeMillis()}.csv", csv,
        )
        val response = OpenCelliDUploadClient.getInstance().uploadToOcid(apiKey, appId, file).execute()
        return response.body().use { body ->
            OpenCelliDUploadClient.mapRequestResultToUploadResult(OpenCelliDUploadClient.handleOcidResponse(response.code(), body))
        }
    }

    private fun sendToBeaconDb(wrapper: UploadRecordsWrapper): UploadResult {
        val response = BeaconDbUploadClient.getInstance().uploadToBeaconDB(wrapper).execute()
        return response.body().use { body ->
            BeaconDbUploadClient.mapRequestResultToUploadResult(BeaconDbUploadClient.handleBeaconDbResponse(response.code(), body))
        }
    }

    /** Some records of one table, read for upload. */
    private class Batch<T>(val table: Table<T>, val records: List<T>)

    /**
     * How the uploader reads and marks one record table.
     *
     * @property takes Whether a target takes anything in these records.
     */
    private inner class Table<T>(
        private val read: (ocid: Boolean, beaconDb: Boolean, limit: Int) -> List<T>,
        private val id: (T) -> Long,
        private val ocidUploaded: (T) -> Boolean,
        private val beaconDbUploaded: (T) -> Boolean,
        private val markOcid: (List<Long>) -> Unit,
        private val markBeaconDb: (List<Long>) -> Unit,
        val takes: (UploadTarget) -> Boolean,
    ) {
        /** @return Up to [limit] records waiting for a target in play, or null if none wait. */
        fun readBatch(ocid: Boolean, beaconDb: Boolean, limit: Int): Batch<T>? =
            read(ocid, beaconDb, limit).takeIf { it.isNotEmpty() }?.let { Batch(this, it) }

        fun uploaded(record: T, target: UploadTarget): Boolean = when (target) {
            UploadTarget.OpenCelliD -> ocidUploaded(record)
            UploadTarget.BeaconDB -> beaconDbUploaded(record)
        }

        fun mark(target: UploadTarget, records: List<T>) {
            val ids = records.map(id)
            database.runInTransaction {
                when (target) {
                    UploadTarget.OpenCelliD -> markOcid(ids)
                    UploadTarget.BeaconDB -> markBeaconDb(ids)
                }
            }
        }
    }

    /** The record tables, in the order they upload. */
    private fun tables(): List<Table<*>> = listOf(
        Table<GsmRecordEntity>(
            dao::getGsmRecordsForUpload, { it.id }, { it.ocidUploaded }, { it.beaconDbUploaded },
            dao::markGsmRecordsAsUploadedToOcid, dao::markGsmRecordsAsUploadedToBeaconDb, takes = { true },
        ),
        // Neither target takes CDMA, so its records are only marked so they can be deleted
        Table<CdmaRecordEntity>(
            dao::getCdmaRecordsForUpload, { it.id }, { it.ocidUploaded }, { it.beaconDbUploaded },
            dao::markCdmaRecordsAsUploadedToOcid, dao::markCdmaRecordsAsUploadedToBeaconDb, takes = { false },
        ),
        Table<UmtsRecordEntity>(
            dao::getUmtsRecordsForUpload, { it.id }, { it.ocidUploaded }, { it.beaconDbUploaded },
            dao::markUmtsRecordsAsUploadedToOcid, dao::markUmtsRecordsAsUploadedToBeaconDb, takes = { true },
        ),
        Table<LteRecordEntity>(
            dao::getLteRecordsForUpload, { it.id }, { it.ocidUploaded }, { it.beaconDbUploaded },
            dao::markLteRecordsAsUploadedToOcid, dao::markLteRecordsAsUploadedToBeaconDb, takes = { true },
        ),
        Table<NrRecordEntity>(
            dao::getNrRecordsForUpload, { it.id }, { it.ocidUploaded }, { it.beaconDbUploaded },
            dao::markNrRecordsAsUploadedToOcid, dao::markNrRecordsAsUploadedToBeaconDb, takes = { true },
        ),
        // OpenCelliD takes no Wi-Fi, so Wi-Fi records count as already there for it
        Table<WifiBeaconRecordEntity>(
            { _, beacon, limit -> dao.getWifiRecordsForUpload(beacon, limit) }, { it.id },
            { true }, { it.beaconDbUploaded }, {}, dao::markWifiRecordsAsUploadedToBeaconDb,
            takes = { it == UploadTarget.BeaconDB },
        ),
    )

    private companion object {
        const val BATCH_SIZE = 100
        const val PERCENT = 100
        const val OPENCELLID_FILE_FIELD = "datafile"
    }
}
