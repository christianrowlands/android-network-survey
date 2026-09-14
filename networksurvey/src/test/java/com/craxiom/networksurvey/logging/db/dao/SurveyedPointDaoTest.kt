package com.craxiom.networksurvey.logging.db.dao

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for the queries in [SurveyedPointDao]: bounded reads, coarse grouping, watermark
 * marking, and trimming.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyedPointDaoTest {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: SurveyedPointDao

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

    private fun point(
        lat: Double,
        lon: Double,
        time: Long,
        source: Int = SurveyedPointEntity.SOURCE_COMMUNITY,
        observed: Int = SurveyedPointEntity.OBSERVED_CELLULAR,
        uploaded: Int = 0,
    ): SurveyedPointEntity {
        val entity = SurveyedPointEntity()
        entity.latitude = lat
        entity.longitude = lon
        entity.time = time
        entity.source = source
        entity.observedMask = observed
        entity.uploadedMask = uploaded
        return entity
    }

    @Test
    fun inBounds_returnsOnlyPointsInsideTheBox() {
        dao.insert(point(38.0, -77.0, 1))
        dao.insert(point(39.0, -77.0, 2))
        dao.insert(point(38.0, -76.0, 3))

        val inside = dao.inBounds(south = 37.9, west = -77.1, north = 38.1, east = -76.9)

        assertEquals(listOf(1L), inside.map { it.time })
    }

    @Test
    fun coarse_groupsNearbyPointsAndKeepsPendingIfAnyRowIsPending() {
        dao.insert(point(38.0001, -77.0001, 1, uploaded = 3))
        dao.insert(point(38.0002, -77.0002, 2, uploaded = 0))
        dao.insert(point(38.0100, -77.0100, 3, uploaded = 3))

        val cells = dao.coarse(
            0.001,
            37.0,
            -78.0,
            39.0,
            -76.0,
            SurveyedPointEntity.OBSERVED_ANY,
            0,
            null,
            3
        )

        assertEquals(2, cells.size)
        val pendingCell = cells.first { it.count == 2 }
        assertEquals(0, pendingCell.minUploadedMask)
        val uploadedCell = cells.first { it.count == 1 }
        assertEquals(3, uploadedCell.minUploadedMask)
    }

    @Test
    fun coarse_doesNotStraddleZeroForNegativeCoordinates() {
        dao.insert(point(-0.0004, -0.0004, 1))
        dao.insert(point(0.0004, 0.0004, 2))

        val cells =
            dao.coarse(0.001, -1.0, -1.0, 1.0, 1.0, SurveyedPointEntity.OBSERVED_ANY, 0, null, 3)

        assertEquals(2, cells.size)
    }

    @Test
    fun markUploaded_onlyTouchesTheRequestedSourceAndTimeWindow() {
        dao.insert(point(38.0, -77.0, 100))
        dao.insert(point(38.0, -77.0, 200))
        dao.insert(point(38.0, -77.0, 100, source = SurveyedPointEntity.SOURCE_NS_ANALYTICS))

        dao.markUploaded(
            source = SurveyedPointEntity.SOURCE_COMMUNITY,
            beforeTime = 150,
            bits = SurveyedPointEntity.UPLOADED_BEACONDB,
            observedFilter = SurveyedPointEntity.OBSERVED_ANY,
        )

        val rows = dao.inBounds(37.0, -78.0, 39.0, -76.0).sortedBy { it.id }
        assertEquals(
            listOf(SurveyedPointEntity.UPLOADED_BEACONDB, 0, 0),
            rows.map { it.uploadedMask })
    }

    @Test
    fun markUploaded_respectsTheObservedFilter() {
        dao.insert(point(38.0, -77.0, 100, observed = SurveyedPointEntity.OBSERVED_CELLULAR))
        dao.insert(point(38.0, -77.0, 100, observed = SurveyedPointEntity.OBSERVED_WIFI))

        dao.markUploaded(
            source = SurveyedPointEntity.SOURCE_COMMUNITY,
            beforeTime = 100,
            bits = SurveyedPointEntity.UPLOADED_OCID,
            observedFilter = SurveyedPointEntity.OBSERVED_CELLULAR,
        )

        val rows = dao.inBounds(37.0, -78.0, 39.0, -76.0).sortedBy { it.id }
        assertEquals(listOf(SurveyedPointEntity.UPLOADED_OCID, 0), rows.map { it.uploadedMask })
    }

    @Test
    fun markUploaded_addsBitsWithoutClearingExistingOnes() {
        dao.insert(point(38.0, -77.0, 100, uploaded = SurveyedPointEntity.UPLOADED_OCID))

        val changed = dao.markUploaded(
            source = SurveyedPointEntity.SOURCE_COMMUNITY,
            beforeTime = 100,
            bits = SurveyedPointEntity.UPLOADED_BEACONDB,
            observedFilter = SurveyedPointEntity.OBSERVED_ANY,
        )
        val unchanged = dao.markUploaded(
            source = SurveyedPointEntity.SOURCE_COMMUNITY,
            beforeTime = 100,
            bits = SurveyedPointEntity.UPLOADED_BEACONDB,
            observedFilter = SurveyedPointEntity.OBSERVED_ANY,
        )

        assertEquals(1, changed)
        assertEquals(0, unchanged)
        assertEquals(
            SurveyedPointEntity.UPLOADED_OCID or SurveyedPointEntity.UPLOADED_BEACONDB,
            dao.inBounds(37.0, -78.0, 39.0, -76.0).single().uploadedMask
        )
    }

    @Test
    fun trim_removesUploadedRowsBeforePendingOnes_oldestFirst() {
        dao.insert(point(38.0, -77.0, 1, uploaded = 1))
        dao.insert(point(38.0, -77.0, 2, uploaded = 0))
        dao.insert(point(38.0, -77.0, 3, uploaded = 1))
        dao.insert(point(38.0, -77.0, 4, uploaded = 0))

        dao.trimUploadedOldest(1)
        assertEquals(listOf(2L, 3L, 4L), dao.inBounds(37.0, -78.0, 39.0, -76.0).map { it.time })

        dao.trimOldest(1)
        assertEquals(listOf(3L, 4L), dao.inBounds(37.0, -78.0, 39.0, -76.0).map { it.time })
    }

    @Test
    fun countAndClear() = runBlocking {
        dao.insert(point(38.0, -77.0, 1))
        dao.insert(point(38.0, -77.0, 2))

        assertEquals(2, dao.count())
        assertEquals(2, dao.observeCount().first())

        dao.clear()

        assertEquals(0, dao.count())
    }
}
