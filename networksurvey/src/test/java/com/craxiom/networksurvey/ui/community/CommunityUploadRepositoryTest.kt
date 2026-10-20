package com.craxiom.networksurvey.ui.community

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * The one observer of community upload work: it reports a finished upload's results to every
 * screen and is the only place that prunes the finished work.
 */
@RunWith(RobolectricTestRunner::class)
class CommunityUploadRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        CommunityUploadRepository.resetForTest()
    }

    @After
    fun tearDown() {
        CommunityUploadRepository.resetForTest()
    }

    @Test
    fun `a finished upload keeps its results after the work is pruned`() = runBlocking {
        val repository = CommunityUploadRepository.get(context)
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<FinishedUploadWorker>().addTag(NsUploaderWorker.WORKER_TAG).build()
        )

        val finished = awaitState(repository) { it is UploadProgressState.Finished } as UploadProgressState.Finished
        assertEquals(OCID_RESULT, finished.ocidResult)
        assertEquals(BEACONDB_RESULT, finished.beaconDbResult)

        // Pruned, so the next screen to observe WorkManager directly would see nothing; the
        // repository is what keeps the result.
        assertTrue(WorkManager.getInstance(context).getWorkInfosByTag(NsUploaderWorker.WORKER_TAG).get().isEmpty())
        assertTrue(repository.state.value is UploadProgressState.Finished)
    }

    @Test
    fun `nothing uploading shows nothing on the Survey screen`() = runBlocking {
        val repository = CommunityUploadRepository.get(context)
        assertEquals(null, repository.progress.first())
    }

    private suspend fun awaitState(
        repository: CommunityUploadRepository,
        predicate: (UploadProgressState) -> Boolean,
    ): UploadProgressState = withTimeout(TIMEOUT_MS) {
        while (!predicate(repository.state.value)) {
            ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(POLL_MS)
        }
        repository.state.value
    }

    /** Stands in for [NsUploaderWorker], finishing at once with both targets' results. */
    class FinishedUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success(
            workDataOf(
                NsUploaderWorker.OCID_RESULT to OCID_RESULT,
                NsUploaderWorker.BEACONDB_RESULT to BEACONDB_RESULT,
            )
        )
    }

    private companion object {
        const val OCID_RESULT = "Success"
        const val BEACONDB_RESULT = "No data"
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 20L
    }
}
