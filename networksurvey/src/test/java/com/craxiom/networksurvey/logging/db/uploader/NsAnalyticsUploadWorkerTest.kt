package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [NsAnalyticsUploadWorker].
 *
 * Two areas are covered:
 *  - [NsAnalyticsUploadWorker.parseRetryAfterSeconds], the pure helper that turns the server's
 *    Retry-After header into a sane delay for the quota re-check.
 *  - The MDM-block branch of [NsAnalyticsUploadWorker.doWork], which must return success (do not
 *    retry) and tear down BOTH the periodic schedule and any pending quota re-check when an admin
 *    disables NS Analytics after work has already been enqueued.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NsAnalyticsUploadWorkerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        // Synchronous executor so enqueue/cancel operations complete before assertions run.
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun setNsAnalyticsAllowed(allowed: Boolean) {
        val restrictionsManager =
            context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val restrictions = Bundle().apply {
            putBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS, allowed)
        }
        shadowOf(restrictionsManager).setApplicationRestrictions(restrictions)
    }

    private fun uploadWorkInfos(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosByTag(NsAnalyticsConstants.NS_ANALYTICS_UPLOAD_WORKER_TAG)
            .get()

    @Test
    fun doWork_whenMdmBlocks_returnsSuccessAndCancelsScheduledWork() {
        // Arrange: NS Analytics allowed, schedule both the periodic upload and a quota re-check.
        // schedulePeriodicUpload and schedulePausedRetryCheck both self-gate on MDM, so they must
        // be scheduled while still allowed.
        setNsAnalyticsAllowed(true)
        NsAnalyticsUploadWorker.schedulePeriodicUpload(context, 15)
        NsAnalyticsUploadWorker.schedulePausedRetryCheck(context, 300)

        val scheduled = uploadWorkInfos()
        assertTrue(
            "Expected periodic + quota re-check work to be scheduled before the run",
            scheduled.size >= 2 && scheduled.none { it.state == WorkInfo.State.CANCELLED }
        )

        // Act: an admin disables NS Analytics, then the already-enqueued worker runs.
        setNsAnalyticsAllowed(false)
        val worker = TestListenableWorkerBuilder<NsAnalyticsUploadWorker>(context).build()
        val result = runBlocking { worker.doWork() }

        // Assert: the run does nothing and is not retried, and every upload-tagged work (periodic
        // schedule AND the pending quota re-check) has been cancelled.
        assertEquals(ListenableWorker.Result.success(), result)
        val after = uploadWorkInfos()
        assertFalse("Expected upload work to exist after the run", after.isEmpty())
        assertTrue(
            "Expected all NS Analytics upload work to be cancelled by the MDM block",
            after.all { it.state == WorkInfo.State.CANCELLED }
        )
    }

    @Test
    fun parseRetryAfterSeconds_validSecondsValue_isUsedAsIs() {
        assertEquals(86400L, NsAnalyticsUploadWorker.parseRetryAfterSeconds("86400"))
        assertEquals(300L, NsAnalyticsUploadWorker.parseRetryAfterSeconds("300"))
    }

    @Test
    fun parseRetryAfterSeconds_nullHeader_fallsBackToDefault() {
        assertEquals(
            NsAnalyticsConstants.DEFAULT_QUOTA_RETRY_AFTER_SECONDS,
            NsAnalyticsUploadWorker.parseRetryAfterSeconds(null)
        )
    }

    @Test
    fun parseRetryAfterSeconds_blankOrNonNumeric_fallsBackToDefault() {
        val default = NsAnalyticsConstants.DEFAULT_QUOTA_RETRY_AFTER_SECONDS
        assertEquals(default, NsAnalyticsUploadWorker.parseRetryAfterSeconds(""))
        assertEquals(default, NsAnalyticsUploadWorker.parseRetryAfterSeconds("   "))
        assertEquals(default, NsAnalyticsUploadWorker.parseRetryAfterSeconds("soon"))
        // HTTP-date form is intentionally not parsed; it falls back to the default.
        assertEquals(
            default,
            NsAnalyticsUploadWorker.parseRetryAfterSeconds("Wed, 21 Oct 2025 07:28:00 GMT")
        )
    }

    @Test
    fun parseRetryAfterSeconds_surroundingWhitespace_isTrimmed() {
        assertEquals(120L, NsAnalyticsUploadWorker.parseRetryAfterSeconds(" 120 "))
    }

    @Test
    fun parseRetryAfterSeconds_zeroOrNegative_isClampedToMinimum() {
        assertEquals(1L, NsAnalyticsUploadWorker.parseRetryAfterSeconds("0"))
        assertEquals(1L, NsAnalyticsUploadWorker.parseRetryAfterSeconds("-42"))
    }

    @Test
    fun parseRetryAfterSeconds_absurdlyLargeValue_isClampedToMax() {
        val max = NsAnalyticsConstants.MAX_QUOTA_RETRY_AFTER_SECONDS
        assertEquals(max, NsAnalyticsUploadWorker.parseRetryAfterSeconds("999999999"))
        // The exact max is preserved.
        assertEquals(max, NsAnalyticsUploadWorker.parseRetryAfterSeconds(max.toString()))
    }

    // isDefinitiveRejection gates whether a failed upload attempt counts against the
    // retry cap. Only definitive server verdicts may advance retryCount: if transient
    // conditions counted, a backend outage plus the cap purge would delete records
    // that never had a real chance to upload.

    @Test
    fun isDefinitiveRejection_207PartialFailure_isDefinitive() {
        assertTrue(NsAnalyticsUploadWorker.isDefinitiveRejection(207))
    }

    @Test
    fun isDefinitiveRejection_definitive4xx_isDefinitive() {
        assertTrue(NsAnalyticsUploadWorker.isDefinitiveRejection(400))
        assertTrue(NsAnalyticsUploadWorker.isDefinitiveRejection(404))
        assertTrue(NsAnalyticsUploadWorker.isDefinitiveRejection(413))
        assertTrue(NsAnalyticsUploadWorker.isDefinitiveRejection(422))
    }

    @Test
    fun isDefinitiveRejection_recoverable4xx_isTransient() {
        // 401/403: authorization state that can be restored (e.g. 403 WORKSPACE_INACTIVE
        // during a deletion grace period must never burn retries toward the purge).
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(401))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(403))
        // 402 quota, 408 timeout, 425 too-early, 429 throttling.
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(402))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(408))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(425))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(429))
    }

    @Test
    fun isDefinitiveRejection_5xxAndSuccessCodes_areNotDefinitive() {
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(200))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(500))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(502))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(503))
        assertFalse(NsAnalyticsUploadWorker.isDefinitiveRejection(504))
    }
}
