package com.craxiom.networksurvey.logging.db.dao

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.util.SignalBuckets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests the v2 queries on [SurveyedPointDao]: the descriptive columns, read filters, per-kind
 * and per-source counts, the "This survey" mission lookup, and the coarse aggregates that back
 * each color mode.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyedPointDaoV2Test {

    private lateinit var database: SurveyDatabase
    private lateinit var dao: SurveyedPointDao

    private val all = SurveyedPointFilter()
    private val world = doubleArrayOf(-90.0, -180.0, 90.0, 180.0)

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
        lat: Double = 38.0,
        lon: Double = -77.0,
        time: Long = 1,
        source: Int = SurveyedPointEntity.SOURCE_COMMUNITY,
        observed: Int = SurveyedPointEntity.OBSERVED_CELLULAR,
        uploaded: Int = 0,
        protocol: Int = SurveyedPointEntity.PROTOCOL_NONE,
        plmn: String? = null,
        area: Int = 0,
        cellId: String? = null,
        bucket: Int = SignalBuckets.UNKNOWN,
        nrScg: Int = 0,
        missionId: String? = null,
    ): SurveyedPointEntity {
        val entity = SurveyedPointEntity()
        entity.latitude = lat
        entity.longitude = lon
        entity.time = time
        entity.source = source
        entity.observedMask = observed
        entity.uploadedMask = uploaded
        entity.protocol = protocol
        entity.plmn = plmn
        entity.area = area
        entity.cellId = cellId
        entity.signalBucket = bucket
        entity.nrScg = nrScg
        entity.missionId = missionId
        return entity
    }

    private fun inWorld(filter: SurveyedPointFilter = all) =
        dao.inBoundsWhere(
            world[0],
            world[1],
            world[2],
            world[3],
            filter.kinds,
            filter.since,
            filter.missionId,
            filter.sources
        )

    @Test
    fun newColumnsDefaultToUnknownOnAV1StyleInsert() {
        val id = dao.insert(point())

        val row = dao.byIds(listOf(id)).single()
        assertEquals(SurveyedPointEntity.PROTOCOL_NONE, row.protocol)
        assertNull(row.plmn)
        assertNull(row.provider)
        assertEquals(0L, row.cid)
        assertEquals(0, row.signal)
        assertEquals(SignalBuckets.UNKNOWN, row.signalBucket)
        assertNull(row.cellId)
        assertNull(row.label)
        assertNull(row.missionId)
    }

    @Test
    fun byIdsReturnsOnlyTheRequestedRows() {
        val a = dao.insert(point(time = 1))
        dao.insert(point(time = 2))
        val c = dao.insert(point(time = 3))

        assertEquals(listOf(1L, 3L), dao.byIds(listOf(a, c)).map { it.time }.sorted())
    }

    @Test
    fun readFiltersByKindTimeMissionAndSource() {
        dao.insert(
            point(
                time = 10,
                observed = SurveyedPointEntity.OBSERVED_CELLULAR,
                missionId = "m1"
            )
        )
        dao.insert(point(time = 20, observed = SurveyedPointEntity.OBSERVED_WIFI, missionId = "m1"))
        dao.insert(
            point(
                time = 30,
                observed = SurveyedPointEntity.OBSERVED_CELLULAR,
                source = SurveyedPointEntity.SOURCE_NS_ANALYTICS,
                missionId = "m2"
            )
        )

        assertEquals(
            listOf(10L, 30L),
            inWorld(SurveyedPointFilter(kinds = SurveyedPointEntity.OBSERVED_CELLULAR)).map { it.time }
                .sorted()
        )
        assertEquals(
            listOf(20L, 30L),
            inWorld(SurveyedPointFilter(since = 20)).map { it.time }.sorted()
        )
        assertEquals(
            listOf(10L, 20L),
            inWorld(SurveyedPointFilter(missionId = "m1")).map { it.time }.sorted()
        )
        assertEquals(
            listOf(30L),
            inWorld(SurveyedPointFilter(sources = SurveyedPointEntity.SOURCE_NS_ANALYTICS)).map { it.time })
        assertEquals(3, inWorld().size)
    }

    @Test
    fun countsPerKindAndDistinctSourcesAreObservable() = runBlocking {
        dao.insert(point(observed = SurveyedPointEntity.OBSERVED_CELLULAR))
        dao.insert(point(observed = SurveyedPointEntity.OBSERVED_CELLULAR))
        dao.insert(
            point(
                observed = SurveyedPointEntity.OBSERVED_WIFI,
                source = SurveyedPointEntity.SOURCE_NS_ANALYTICS
            )
        )

        val counts = dao.observeCountByKind().first().associate { it.kind to it.count }
        assertEquals(2, counts[SurveyedPointEntity.OBSERVED_CELLULAR])
        assertEquals(1, counts[SurveyedPointEntity.OBSERVED_WIFI])
        assertNull(counts[SurveyedPointEntity.OBSERVED_BLUETOOTH])

        assertEquals(
            listOf(SurveyedPointEntity.SOURCE_COMMUNITY, SurveyedPointEntity.SOURCE_NS_ANALYTICS),
            dao.observeSources().first().sorted()
        )
        assertEquals(
            listOf(
                SurveyedPointEntity.OBSERVED_CELLULAR,
                SurveyedPointEntity.OBSERVED_WIFI
            ), dao.kindsSince(0).sorted()
        )
    }

    @Test
    fun latestMissionComesOnlyFromNsAnalyticsRowsWithAnId() = runBlocking {
        assertNull(dao.observeLatestNsAnalyticsMissionId().first())

        dao.insert(
            point(
                time = 1,
                source = SurveyedPointEntity.SOURCE_NS_ANALYTICS,
                missionId = "old"
            )
        )
        dao.insert(
            point(
                time = 2,
                source = SurveyedPointEntity.SOURCE_COMMUNITY,
                missionId = "stale-community"
            )
        )
        dao.insert(
            point(
                time = 3,
                source = SurveyedPointEntity.SOURCE_NS_ANALYTICS,
                missionId = null
            )
        )

        assertEquals("old", dao.observeLatestNsAnalyticsMissionId().first())
    }

    @Test
    fun coarseReportsTheBestBucketAndNewestTime() {
        dao.insert(point(lat = 38.0001, time = 5, bucket = SignalBuckets.WEAK, uploaded = 3))
        dao.insert(point(lat = 38.0002, time = 9, bucket = SignalBuckets.GOOD, uploaded = 0))
        dao.insert(point(lat = 38.0003, time = 7, bucket = SignalBuckets.UNKNOWN, uploaded = 3))

        val cell = dao.coarse(
            0.001,
            world[0],
            world[1],
            world[2],
            world[3],
            all.kinds,
            all.since,
            all.missionId,
            all.sources
        ).single()

        assertEquals(3, cell.count)
        assertEquals(SignalBuckets.GOOD, cell.bestBucket)
        assertEquals(9L, cell.lastTime)
        assertEquals(0, cell.minUploadedMask)
    }

    private fun dominant(category: DominantCategory, step: Double = 0.001) = dao.coarseDominant(
        SurveyedPointQueries.dominant(category, step, world[0], world[1], world[2], world[3], all)
    )

    @Test
    fun dominantTechnologyPicksTheMostCommonAndBreaksTiesTowardTheNewer() {
        dao.insert(point(lat = 38.0001, protocol = SurveyedPointEntity.PROTOCOL_LTE))
        dao.insert(point(lat = 38.0002, protocol = SurveyedPointEntity.PROTOCOL_LTE))
        dao.insert(point(lat = 38.0003, protocol = SurveyedPointEntity.PROTOCOL_UMTS))
        dao.insert(point(lat = 38.0004, protocol = SurveyedPointEntity.PROTOCOL_UMTS))

        val cell = dominant(DominantCategory.TECHNOLOGY).single()

        // Two LTE vs two UMTS: the tie goes to the newer generation.
        assertEquals(SurveyedPointEntity.PROTOCOL_LTE.toString(), cell.category)
        assertEquals(2, cell.count)
        assertEquals(4, cell.cellCount)
    }

    @Test
    fun dominantTechnologyCountsNsaAsNr() {
        dao.insert(point(lat = 38.0001, protocol = SurveyedPointEntity.PROTOCOL_LTE, nrScg = 1))
        dao.insert(point(lat = 38.0002, protocol = SurveyedPointEntity.PROTOCOL_LTE, nrScg = 1))
        dao.insert(point(lat = 38.0003, protocol = SurveyedPointEntity.PROTOCOL_LTE))

        assertEquals(
            SurveyedPointEntity.PROTOCOL_NR.toString(),
            dominant(DominantCategory.TECHNOLOGY).single().category
        )
    }

    @Test
    fun dominantProviderCellAndAreaGroupByTheirIdentity() {
        dao.insert(point(lat = 38.0001, plmn = "310-260", area = 1, cellId = "310-260-1-100"))
        dao.insert(point(lat = 38.0002, plmn = "310-260", area = 1, cellId = "310-260-1-100"))
        dao.insert(point(lat = 38.0003, plmn = "311-480", area = 7, cellId = "311-480-7-200"))
        dao.insert(point(lat = 38.0004))

        assertEquals("310-260", dominant(DominantCategory.PROVIDER).single().category)
        assertEquals("310-260-1-100", dominant(DominantCategory.CELL).single().category)
        assertEquals("310-260-1", dominant(DominantCategory.AREA).single().category)
    }

    @Test
    fun dominantQueriesStayBoundedByTheLatticeNotByCategoryCount() {
        for (i in 0 until 10) {
            dao.insert(point(lat = 38.0 + i * 0.00001, cellId = "cell-$i"))
        }

        assertEquals(1, dominant(DominantCategory.CELL).size)
    }
}
