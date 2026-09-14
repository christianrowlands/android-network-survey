package com.craxiom.networksurvey.logging.db

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.dao.SurveyedPointDao
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests [SurveyedPointStore] against a real in-memory database: thinned writes, per-pipeline
 * upload marking, and cap-based trimming.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyedPointStoreTest {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: SurveyedPointDao

    private val lat = 38.0
    private val lon = -77.0

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.surveyedPointDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun allRows() = dao.inBounds(-90.0, -180.0, 90.0, 180.0).sortedBy { it.id }

    @Test
    fun observe_writesOneThinnedRowPerKind() {
        val store = SurveyedPointStore(dao, SurveyedPointEntity.SOURCE_COMMUNITY)

        store.observe(SurveyedPointEntity.OBSERVED_CELLULAR, lat, lon, 10, 1f, 1000)
        store.observe(SurveyedPointEntity.OBSERVED_CELLULAR, lat, lon, 10, 1f, 1001)
        store.observe(SurveyedPointEntity.OBSERVED_WIFI, lat, lon, 10, 1f, 1002)

        val rows = allRows()
        assertEquals(2, rows.size)
        assertEquals(SurveyedPointEntity.SOURCE_COMMUNITY, rows[0].source)
        assertEquals(SurveyedPointEntity.OBSERVED_CELLULAR, rows[0].observedMask)
        assertEquals(1000L, rows[0].time)
        assertEquals(0, rows[0].uploadedMask)
        assertEquals(SurveyedPointEntity.OBSERVED_WIFI, rows[1].observedMask)
    }

    @Test
    fun markCommunityUploaded_setsOcidOnlyForCellular() {
        val store = SurveyedPointStore(dao, SurveyedPointEntity.SOURCE_COMMUNITY)
        store.observe(SurveyedPointEntity.OBSERVED_CELLULAR, lat, lon, 10, 1f, 100)
        store.observe(SurveyedPointEntity.OBSERVED_WIFI, lat, lon, 10, 1f, 100)

        SurveyedPointStore.markCommunityUploaded(
            dao,
            beforeTime = 100,
            ocid = true,
            beaconDb = true
        )

        assertEquals(
            listOf(
                SurveyedPointEntity.UPLOADED_OCID or SurveyedPointEntity.UPLOADED_BEACONDB,
                SurveyedPointEntity.UPLOADED_BEACONDB
            ),
            allRows().map { it.uploadedMask }
        )
    }

    @Test
    fun markCommunityUploaded_skipsDisabledTargets() {
        val store = SurveyedPointStore(dao, SurveyedPointEntity.SOURCE_COMMUNITY)
        store.observe(SurveyedPointEntity.OBSERVED_CELLULAR, lat, lon, 10, 1f, 100)
        store.observe(SurveyedPointEntity.OBSERVED_WIFI, lat, lon, 10, 1f, 100)

        SurveyedPointStore.markCommunityUploaded(
            dao,
            beforeTime = 100,
            ocid = true,
            beaconDb = false
        )

        assertEquals(
            listOf(SurveyedPointEntity.UPLOADED_OCID, 0),
            allRows().map { it.uploadedMask })
    }

    @Test
    fun markNsAnalyticsUploaded_exclusiveWatermarkLeavesTheBoundaryPending() {
        val store = SurveyedPointStore(dao, SurveyedPointEntity.SOURCE_NS_ANALYTICS)
        store.observe(SurveyedPointEntity.OBSERVED_CELLULAR, lat, lon, 10, 1f, 100)
        store.observe(SurveyedPointEntity.OBSERVED_WIFI, lat, lon, 10, 1f, 200)

        SurveyedPointStore.markNsAnalyticsUploaded(dao, maxTimestamp = 200, inclusive = false)
        assertEquals(
            listOf(SurveyedPointEntity.UPLOADED_NS_ANALYTICS, 0),
            allRows().map { it.uploadedMask })

        SurveyedPointStore.markNsAnalyticsUploaded(dao, maxTimestamp = 200, inclusive = true)
        assertEquals(
            listOf(
                SurveyedPointEntity.UPLOADED_NS_ANALYTICS,
                SurveyedPointEntity.UPLOADED_NS_ANALYTICS
            ),
            allRows().map { it.uploadedMask }
        )
    }

    @Test
    fun trimIfNeeded_dropsUploadedRowsFirstUntilUnderTheCap() {
        val store = SurveyedPointStore(dao, SurveyedPointEntity.SOURCE_COMMUNITY)
        // Distinct locations so the gate accepts every observation.
        for (i in 0 until 6) {
            store.observe(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                lat + i * 0.01,
                lon,
                10,
                1f,
                i.toLong()
            )
        }
        SurveyedPointStore.markCommunityUploaded(dao, beforeTime = 2, ocid = true, beaconDb = true)

        SurveyedPointStore.trimIfNeeded(dao, maxRows = 4)

        assertEquals(listOf(2L, 3L, 4L, 5L), allRows().map { it.time })
        assertEquals(4, dao.count())
    }
}
