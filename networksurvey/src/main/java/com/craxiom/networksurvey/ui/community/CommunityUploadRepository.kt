package com.craxiom.networksurvey.ui.community

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.uploader.CommunityRetryStore
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.logging.db.uploader.UploadResult
import com.craxiom.networksurvey.logging.db.uploader.UploadTarget
import com.craxiom.networksurvey.survey.status.CommunityRetry
import com.craxiom.networksurvey.survey.status.CommunityUploadFailure
import com.craxiom.networksurvey.ui.survey.SurveyEventMessages
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
 * letting it vanish with the upload notification. An upload waiting to try again after a failed
 * attempt carries why it failed and when it tries again ([UploadProgressState.InProgress.retry]).
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

    /**
     * The compact form the Survey screen shows while an upload runs or waits for its first
     * attempt; null otherwise. An upload waiting to retry shows as the destination's Will retry
     * status instead, which says why and when.
     */
    val progress: Flow<CommunityUploadUi?> = state.map { upload ->
        (upload as? UploadProgressState.InProgress)?.takeIf { it.retry == null }?.let {
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
            // An upload that replaced a waiting retry leaves the cancelled one behind
            if (workInfos.any { it.state.isFinished }) workManager.pruneWork()
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
        val openCelliD = resultOf(output.getString(NsUploaderWorker.OCID_RESULT_ENUM))
        val beaconDb = resultOf(output.getString(NsUploaderWorker.BEACONDB_RESULT_ENUM))
        if (openCelliD == UploadResult.Cancelled || beaconDb == UploadResult.Cancelled) return null
        val worst = worstMessage(openCelliD, beaconDb)
        return when {
            worst != null -> CommunityUploadFailure(worst)
            // A failed run that reported no result for either target
            work.state == WorkInfo.State.FAILED && openCelliD == null && beaconDb == null ->
                CommunityUploadFailure(appContext.getString(R.string.uploader_failure))

            else -> null
        }
    }

    /**
     * @return The worst failing result with the targets that had it ("OpenCelliD: Server error",
     * "OpenCelliD and BeaconDB: Connection error"), or null when neither target failed.
     */
    private fun worstMessage(openCelliD: UploadResult?, beaconDb: UploadResult?): String? {
        val failed = listOf(UploadTarget.OpenCelliD to openCelliD, UploadTarget.BeaconDB to beaconDb)
            .mapNotNull { (target, result) -> result?.takeIf { it !in NOT_FAILURES }?.let { target to it } }
        val worst = failed.minByOrNull { (_, result) -> UploadResult.SEVERITY_ORDER.indexOf(result) }?.second ?: return null
        val targets = failed.filter { it.second == worst }.map { (target, _) -> appContext.getString(targetName(target)) }
        return appContext.getString(
            R.string.community_target_result,
            SurveyEventMessages.join(appContext.resources, targets),
            appContext.getString(UploadResult.getMessage(worst)),
        )
    }

    private fun targetName(target: UploadTarget): Int = when (target) {
        UploadTarget.OpenCelliD -> R.string.survey_dest_community_opencellid
        UploadTarget.BeaconDB -> R.string.survey_dest_community_beacondb
    }

    /**
     * @return Why an earlier attempt of [work] failed and when the next one runs, or null if none
     * failed (the system stopped it, for example when Wi-Fi went away, which also counts as an attempt).
     */
    private fun retry(work: WorkInfo): CommunityRetry? {
        val results = CommunityRetryStore.read(appContext, work.id) ?: return null
        return CommunityRetry(
            message = worstMessage(results.openCelliD, results.beaconDb) ?: appContext.getString(R.string.uploader_failure),
            nextTryMs = work.nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE },
        )
    }

    private fun resultOf(name: String?): UploadResult? = enumValues<UploadResult>().firstOrNull { it.name == name }

    private fun inProgress(work: WorkInfo): UploadProgressState.InProgress {
        val max = work.progress.getInt(NsUploaderWorker.PROGRESS_MAX, NsUploaderWorker.PROGRESS_MAX_VALUE)
        val current = work.progress.getInt(NsUploaderWorker.PROGRESS, NsUploaderWorker.PROGRESS_MIN_VALUE)
        val waiting = work.state == WorkInfo.State.ENQUEUED
        // A retry after a failed attempt waits in ENQUEUED too; only that one failed
        val retry = if (waiting && work.runAttemptCount > 0) retry(work) else null
        val message = work.progress.getString(NsUploaderWorker.PROGRESS_STATUS_MESSAGE) ?: when {
            retry != null -> appContext.getString(R.string.uploader_enqueued)
            waiting -> appContext.getString(R.string.uploader_waiting_to_start)
            else -> ""
        }
        return UploadProgressState.InProgress(current.coerceAtMost(max), max, message, running = !waiting, retry = retry)
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
