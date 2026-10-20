package com.craxiom.networksurvey.ui.community

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.logging.db.uploader.UploadResult
import com.craxiom.networksurvey.survey.status.CommunityUploadFailure
import com.craxiom.networksurvey.ui.survey.model.CommunityUploadUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The one place that watches the community (OpenCelliD and BeaconDB) upload work. The Community
 * databases screen and the Survey screen's progress row both read [state], so an upload's result
 * is never lost to whichever screen happened to see the work finish first; this is also the only
 * caller of [WorkManager.pruneWork] for upload work.
 *
 * A failed upload is also kept as [lastFailure] until it is acknowledged (the Community databases
 * page is opened) or the next upload starts, so the Survey screen can say it failed instead of
 * letting it vanish with the upload notification.
 */
class CommunityUploadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow<UploadProgressState>(UploadProgressState.Hidden)
    private val mutableFailure = MutableStateFlow<CommunityUploadFailure?>(null)

    /** The finished work already reported, so a repeated WorkManager emission can't bring back an acknowledged failure. */
    @Volatile
    private var reportedWorkId: UUID? = null

    /** The running or most recently finished upload. */
    val state: StateFlow<UploadProgressState> = mutableState.asStateFlow()

    /**
     * The last upload's failure (a failure or a partial failure, never a cancel), until
     * [acknowledge] is called or the next upload starts; null otherwise.
     */
    val lastFailure: StateFlow<CommunityUploadFailure?> = mutableFailure.asStateFlow()

    /** The compact form the Survey screen shows while an upload runs; null otherwise. */
    val progress: Flow<CommunityUploadUi?> = state.map { upload ->
        (upload as? UploadProgressState.InProgress)?.let {
            CommunityUploadUi(
                percent = it.progress * PERCENT / it.maxProgress.coerceAtLeast(1),
                message = it.statusMessage,
                running = it.running,
            )
        }
    }

    init {
        scope.launch {
            workManager.getWorkInfosByTagFlow(NsUploaderWorker.WORKER_TAG).collect(::onWorkInfos)
        }
    }

    /** Cancels a running upload. */
    fun cancel() {
        workManager.cancelAllWorkByTag(NsUploaderWorker.WORKER_TAG)
    }

    /** The user has seen the failure (the Community databases page is open), so it stops showing elsewhere. */
    fun acknowledge() {
        mutableFailure.value = null
    }

    private fun onWorkInfos(workInfos: List<WorkInfo>) {
        val active = workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        if (active != null) {
            // A new upload replaces the old result, failed or not
            mutableFailure.value = null
            mutableState.value = inProgress(active)
            return
        }
        val finished = workInfos.firstOrNull { it.state.isFinished } ?: return
        mutableState.value = finished(finished)
        if (finished.id != reportedWorkId) {
            reportedWorkId = finished.id
            mutableFailure.value = failure(finished)
        }
        workManager.pruneWork()
    }

    /**
     * @return The failure of [work], or null when every target succeeded, had nothing to upload or
     * was off, and for a cancel (the user stopped it, so there is nothing to tell them).
     */
    private fun failure(work: WorkInfo): CommunityUploadFailure? {
        if (work.state == WorkInfo.State.CANCELLED) return null
        val output = work.outputData
        val results = listOf(
            resultOf(output.getString(NsUploaderWorker.OCID_RESULT_ENUM)) to output.getString(NsUploaderWorker.OCID_RESULT),
            resultOf(output.getString(NsUploaderWorker.BEACONDB_RESULT_ENUM)) to output.getString(NsUploaderWorker.BEACONDB_RESULT),
        )
        if (results.any { (result, _) -> result == UploadResult.Cancelled }) return null
        val worst = results
            .filter { (result, _) -> result != null && result !in NOT_FAILURES }
            .minByOrNull { (result, _) -> UploadResult.SEVERITY_ORDER.indexOf(result) }
        return when {
            worst != null -> CommunityUploadFailure(worst.second ?: appContext.getString(R.string.uploader_failure))
            // A failed run that reported no result for either target
            work.state == WorkInfo.State.FAILED && results.all { it.first == null } ->
                CommunityUploadFailure(appContext.getString(R.string.uploader_failure))

            else -> null
        }
    }

    private fun resultOf(name: String?): UploadResult? = enumValues<UploadResult>().firstOrNull { it.name == name }

    private fun inProgress(work: WorkInfo): UploadProgressState.InProgress {
        val max = work.progress.getInt(NsUploaderWorker.PROGRESS_MAX, NsUploaderWorker.PROGRESS_MAX_VALUE)
        val current = work.progress.getInt(NsUploaderWorker.PROGRESS, NsUploaderWorker.PROGRESS_MIN_VALUE)
        val waiting = work.state == WorkInfo.State.ENQUEUED
        val message = work.progress.getString(NsUploaderWorker.PROGRESS_STATUS_MESSAGE) ?: when {
            // A retry after a failed attempt waits in ENQUEUED too; only that one failed.
            waiting && work.runAttemptCount > 0 -> appContext.getString(R.string.uploader_enqueued)
            waiting -> appContext.getString(R.string.uploader_waiting_to_start)
            else -> ""
        }
        return UploadProgressState.InProgress(current.coerceAtMost(max), max, message, running = !waiting)
    }

    private fun finished(work: WorkInfo): UploadProgressState.Finished {
        val output = work.outputData
        return UploadProgressState.Finished(
            cancelled = work.state == WorkInfo.State.CANCELLED,
            ocidResult = output.getString(NsUploaderWorker.OCID_RESULT).orEmpty(),
            beaconDbResult = output.getString(NsUploaderWorker.BEACONDB_RESULT).orEmpty(),
            ocidResultMessage = output.getString(NsUploaderWorker.OCID_RESULT_MESSAGE).orEmpty(),
            beaconDbResultMessage = output.getString(NsUploaderWorker.BEACONDB_RESULT_MESSAGE).orEmpty(),
        )
    }

    companion object {
        private const val PERCENT = 100

        /** The results that are not a failure: done, nothing to upload, or the target is off. */
        private val NOT_FAILURES = setOf(
            UploadResult.Success,
            UploadResult.NoData,
            UploadResult.UploadDisabledForTarget,
            UploadResult.NotStarted,
        )

        @Volatile
        private var instance: CommunityUploadRepository? = null

        /**
         * Stops the app-wide instance's WorkManager observer and drops it, so each test starts with
         * a fresh observer and no collector from an earlier test is left running.
         */
        @VisibleForTesting
        fun resetForTest() {
            synchronized(this) {
                instance?.scope?.cancel()
                instance = null
            }
        }

        /** @return The app-wide repository, created on first use. */
        @JvmStatic
        fun get(context: Context): CommunityUploadRepository =
            instance ?: synchronized(this) { instance ?: CommunityUploadRepository(context).also { instance = it } }
    }
}
