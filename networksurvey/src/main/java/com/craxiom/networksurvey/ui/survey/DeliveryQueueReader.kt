package com.craxiom.networksurvey.ui.survey

import android.content.Context
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.survey.status.CommunityQueue
import com.craxiom.networksurvey.survey.status.CommunityUploadFailure
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.survey.status.NetworkWait
import com.craxiom.networksurvey.survey.status.NsAnalyticsQueue
import com.craxiom.networksurvey.ui.community.CommunityUploadLauncher
import com.craxiom.networksurvey.ui.community.CommunityUploadRepository
import com.craxiom.networksurvey.ui.community.UploadProgressState
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import timber.log.Timber

/**
 * Reads what waits on the phone for NS Analytics and the community databases, and how their
 * uploads are going, for the destination statuses and the "Still to do" panel. Upload work changes
 * (and a failed community upload) show at once; the record counts are read again every
 * [pollIntervalMs] while collected, and at once after [refresh].
 */
class DeliveryQueueReader(
    context: Context,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
) {
    private val appContext = context.applicationContext

    /** The queues, while collected. */
    fun queues(): Flow<DeliveryQueues> {
        val community = CommunityUploadRepository.get(appContext)
        return combine(
            WorkManager.getInstance(appContext).getWorkInfosByTagFlow(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG),
            community.state,
            community.lastFailure,
            merge(pollTicks(pollIntervalMs), refreshes),
        ) { nsAnalyticsWork, communityUpload, communityFailure, _ -> read(nsAnalyticsWork, communityUpload, communityFailure) }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }


    private fun read(
        nsAnalyticsWork: List<WorkInfo>,
        communityUpload: UploadProgressState,
        communityFailure: CommunityUploadFailure?,
    ): DeliveryQueues = try {
        DeliveryQueues(nsAnalytics = nsAnalytics(nsAnalyticsWork), community = community(communityUpload, communityFailure))
    } catch (e: Exception) {
        Timber.e(e, "Could not read the upload queues")
        DeliveryQueues()
    }

    private fun nsAnalytics(work: List<WorkInfo>): NsAnalyticsQueue {
        val periodic = work.filter { NsAnalyticsConstants.NS_ANALYTICS_PERIODIC_WORKER_TAG in it.tags }
        val errorType = NsAnalyticsSecureStorage.getLastUploadErrorType(appContext)
        return NsAnalyticsQueue(
            pending = SurveyDatabase.getInstance(appContext).nsAnalyticsDao().pendingRecordCount,
            uploading = work.any { it.state == WorkInfo.State.RUNNING },
            uploadQueued = work.any { it.state == WorkInfo.State.ENQUEUED && it !in periodic },
            nextUploadMs = periodic.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                ?.nextScheduleTimeMillis?.takeIf { it != Long.MAX_VALUE },
            uploadIntervalMinutes = NsAnalyticsSecureStorage.getUploadFrequency(appContext),
            lastUploadMs = NsAnalyticsSecureStorage.getLastUploadTime(appContext).takeIf { it > 0 },
            lastUploadFailed = errorType != null && errorType != NsAnalyticsConstants.ERROR_CODE_QUOTA_EXCEEDED,
            quotaPaused = NsAnalyticsSecureStorage.isUploadPausedForQuota(appContext),
            online = NsUtils.isNetworkAvailable(appContext),
        )
    }

    /**
     * Only records a selected target still needs count: Cellular goes to either, Wi-Fi only to
     * BeaconDB, so Wi-Fi left over after BeaconDB was turned off isn't "waiting", and neither is a
     * record the selected targets already took.
     */
    private fun community(upload: UploadProgressState, failure: CommunityUploadFailure?): CommunityQueue {
        val openCelliD = PreferenceUtils.getBoolean(
            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
        )
        val beaconDb = PreferenceUtils.getBoolean(
            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
        )
        val pending = if (openCelliD || beaconDb) CommunityUploadLauncher.pending(appContext, openCelliD, beaconDb) else null
        val inProgress = upload as? UploadProgressState.InProgress
        val waiting = inProgress != null && !inProgress.running
        return CommunityQueue(
            pending = (pending?.cellular ?: 0) + (pending?.wifi ?: 0),
            uploading = inProgress?.running == true,
            uploadQueued = waiting && inProgress.retry == null,
            networkWait = if (waiting) communityNetworkWait() else NetworkWait.NONE,
            lastFailure = failure,
            retrying = inProgress?.retry,
        )
    }

    /**
     * @return The network the waiting community upload needs and the phone isn't on: automatic
     * uploads may be limited to Wi-Fi, and every upload needs a connection.
     */
    private fun communityNetworkWait(): NetworkWait {
        val waiting = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME).get()
            .firstOrNull { it.state == WorkInfo.State.ENQUEUED } ?: return NetworkWait.NONE
        return when (waiting.constraints.requiredNetworkType) {
            NetworkType.UNMETERED -> if (NsUtils.isOnUnmeteredNetwork(appContext)) NetworkWait.NONE else NetworkWait.WIFI
            NetworkType.NOT_REQUIRED -> NetworkWait.NONE
            else -> if (NsUtils.isNetworkAvailable(appContext)) NetworkWait.NONE else NetworkWait.ANY
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L

        private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /**
         * Reads the queues again now, for a change no upload work announces (the NS Analytics queue
         * cleared), so a page doesn't show the old count until the next poll.
         */
        fun refresh() {
            refreshes.tryEmit(Unit)
        }
    }
}

/** Emits at once and then every [intervalMs], while collected, so a poll reads again on its own. */
internal fun pollTicks(intervalMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(intervalMs)
    }
}
