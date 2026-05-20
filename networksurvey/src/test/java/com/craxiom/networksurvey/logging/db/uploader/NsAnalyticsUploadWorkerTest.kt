package com.craxiom.networksurvey.logging.db.uploader

import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure helpers in [NsAnalyticsUploadWorker].
 *
 * Focus is on [NsAnalyticsUploadWorker.parseRetryAfterSeconds], which is the safety net that turns
 * the server's Retry-After header into a sane delay for the quota re-check.
 */
class NsAnalyticsUploadWorkerTest {

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
}
