package com.craxiom.networksurvey.ui.community

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.ui.survey.model.CommunityUploadUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one place that watches the community (OpenCelliD and BeaconDB) upload work. The Community
 * databases screen and the Survey screen's progress row both read [state], so an upload's result
 * is never lost to whichever screen happened to see the work finish first; this is also the only
 * caller of [WorkManager.pruneWork] for upload work.
 */
class CommunityUploadRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow<UploadProgressState>(UploadProgressState.Hidden)

    /** The running or most recently finished upload. */
    val state: StateFlow<UploadProgressState> = mutableState.asStateFlow()

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

    private fun onWorkInfos(workInfos: List<WorkInfo>) {
        val active = workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        if (active != null) {
            mutableState.value = inProgress(active)
            return
        }
        val finished = workInfos.firstOrNull { it.state.isFinished } ?: return
        mutableState.value = finished(finished)
        workManager.pruneWork()
    }

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

        @Volatile
        private var instance: CommunityUploadRepository? = null

        /** Drops the app-wide instance so each test starts with a fresh WorkManager observer. */
        @VisibleForTesting
        fun resetForTest() {
            instance = null
        }

        /** @return The app-wide repository, created on first use. */
        @JvmStatic
        fun get(context: Context): CommunityUploadRepository =
            instance ?: synchronized(this) { instance ?: CommunityUploadRepository(context).also { instance = it } }
    }
}
