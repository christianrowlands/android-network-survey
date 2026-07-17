package com.craxiom.networksurvey.logging.db.dao

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsQueueEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for the retry-cap queries in [NsAnalyticsDao] (poison-pill guard).
 *
 * The invariant under test: upload eligibility (retryCount <= max) and purge
 * (retryCount > max) are exact mirror images, so every row is always either
 * uploadable or purgeable and no row can sit invisible in the queue forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NsAnalyticsDaoTest {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: NsAnalyticsDao

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.nsAnalyticsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun queueRecord(timestamp: Long, retryCount: Int, uploaded: Boolean = false): NsAnalyticsQueueEntity {
        val entity = NsAnalyticsQueueEntity()
        entity.recordType = "lte"
        entity.protobufJson = "{}"
        entity.timestamp = timestamp
        entity.batchId = "batch-1"
        entity.uploaded = uploaded
        entity.retryCount = retryCount
        entity.lastUploadAttempt = 0
        entity.payloadSize = 2
        return entity
    }

    @Test
    fun uploadableRecords_excludeOverCapRows_oldestFirst() {
        // Rows with retryCount 0..5; cap of 3 means 0-3 are eligible, 4-5 are not.
        for (retry in 0..5) {
            dao.insertRecord(queueRecord(timestamp = 1000L + retry, retryCount = retry))
        }

        val uploadable = dao.getUploadableRecords(10, 3)

        assertEquals(4, uploadable.size)
        assertEquals(listOf(0, 1, 2, 3), uploadable.map { it.retryCount })
        assertEquals(listOf(1000L, 1001L, 1002L, 1003L), uploadable.map { it.timestamp })
    }

    @Test
    fun uploadableCount_matchesUploadableRecords() {
        for (retry in 0..5) {
            dao.insertRecord(queueRecord(timestamp = 1000L + retry, retryCount = retry))
        }
        // Uploaded rows are never counted regardless of retryCount.
        dao.insertRecord(queueRecord(timestamp = 2000L, retryCount = 0, uploaded = true))

        assertEquals(4, dao.getUploadableRecordCount(3))
        assertEquals(dao.getUploadableRecords(10, 3).size, dao.getUploadableRecordCount(3))
    }

    @Test
    fun deleteFailedRecords_deletesOnlyOverCap_andReturnsCount() {
        for (retry in 0..5) {
            dao.insertRecord(queueRecord(timestamp = 1000L + retry, retryCount = retry))
        }

        val purged = dao.deleteFailedRecords(3)

        assertEquals(2, purged)
        // Every remaining pending row is uploadable: no invisible zombies.
        assertEquals(4, dao.getPendingRecordCount())
        assertEquals(4, dao.getUploadableRecordCount(3))
    }

    @Test
    fun incrementRetryCount_onlyAffectsGivenIds() {
        dao.insertRecord(queueRecord(timestamp = 1000L, retryCount = 0))
        dao.insertRecord(queueRecord(timestamp = 1001L, retryCount = 0))
        val rows = dao.getUploadableRecords(10, 3)
        val firstId = rows[0].id

        dao.incrementRetryCount(listOf(firstId), 5000L)

        val after = dao.getUploadableRecords(10, 3).associateBy { it.id }
        assertEquals(1, after[firstId]?.retryCount)
        assertEquals(5000L, after[firstId]?.lastUploadAttempt)
        val other = after.values.first { it.id != firstId }
        assertEquals(0, other.retryCount)
        assertEquals(0L, other.lastUploadAttempt)
    }
}
