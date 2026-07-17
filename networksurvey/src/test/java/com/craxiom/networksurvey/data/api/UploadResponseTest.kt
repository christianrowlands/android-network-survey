package com.craxiom.networksurvey.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UploadResponse] deserialization and the isSuccess contract.
 *
 * The server's poison-pill fix (R1) reports permanently rejected records in a new
 * "skipped" field while keeping "failed" for transient, retryable errors. A batch
 * whose records were all skipped arrives as status=completed / failed=0 and MUST
 * be treated as a success so the client marks it uploaded and stops retrying it.
 */
class UploadResponseTest {

    private val gson = Gson()

    @Test
    fun skipped_parsedWhenPresent() {
        val json = """
            {"batch_id":"b1","status":"completed","message":"Upload processed",
             "processed":5,"skipped":3,"failed":0,"total_records":8}
        """.trimIndent()

        val response = gson.fromJson(json, UploadResponse::class.java)

        assertEquals(3, response.skipped)
        assertEquals(5, response.processed)
        assertEquals(0, response.failed)
    }

    @Test
    fun skipped_defaultsToZeroWhenAbsent() {
        // Old-server response shape: no "skipped" field.
        val json = """
            {"batch_id":"b1","status":"completed","message":"Upload processed",
             "processed":5,"failed":0,"total_records":5}
        """.trimIndent()

        val response = gson.fromJson(json, UploadResponse::class.java)

        assertEquals(0, response.skipped)
        assertTrue(response.isSuccess)
    }

    @Test
    fun isSuccess_trueForCompletedWithSkips() {
        // The client half of the unwedge contract: skip-only or partially skipped
        // batches are successes; the skipped records must never be retried.
        val json = """
            {"batch_id":"b1","status":"completed","message":"Upload processed",
             "processed":0,"skipped":5,"failed":0,"total_records":5}
        """.trimIndent()

        val response = gson.fromJson(json, UploadResponse::class.java)

        assertTrue(response.isSuccess)
    }

    @Test
    fun isSuccess_falseForCompletedWithErrors() {
        val json = """
            {"batch_id":"b1","status":"completed_with_errors","message":"Upload processed",
             "processed":3,"skipped":0,"failed":2,"total_records":5}
        """.trimIndent()

        val response = gson.fromJson(json, UploadResponse::class.java)

        assertFalse(response.isSuccess)
    }

    @Test
    fun isSuccess_falseForFailedStatus() {
        val json = """
            {"batch_id":"b1","status":"failed","message":"Upload processed",
             "processed":0,"skipped":0,"failed":4,"total_records":4}
        """.trimIndent()

        val response = gson.fromJson(json, UploadResponse::class.java)

        assertFalse(response.isSuccess)
    }
}
