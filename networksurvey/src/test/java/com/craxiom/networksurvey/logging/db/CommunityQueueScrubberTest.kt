package com.craxiom.networksurvey.logging.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craxiom.networksurvey.logging.db.model.BaseRecordEntity
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.north
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * The community queue scrub: rows recorded inside a zone (allowing for their accuracy) are deleted
 * from every table whether they were sent or not, rows outside stay, and the editor's count only
 * counts rows still waiting for a selected target.
 */
@RunWith(RobolectricTestRunner::class)
class CommunityQueueScrubberTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database get() = SurveyDatabase.getInstance(context)
    private val scrubber get() = CommunityQueueScrubber(database)

    @Before
    fun setUp() = background { database.clearAllTables() }

    @After
    fun tearDown() = background { database.clearAllTables() }

    @Test
    fun `deletes rows inside a zone from every table and keeps the rest`() {
        background {
            database.gsmRecordDao().insertRecords(listOf(gsm(meters = 0.0), gsm(meters = 1_000.0)))
            database.lteRecordDao().insertRecords(listOf(lte(meters = 200.0)))
            database.wifiRecordDao().insertRecords(listOf(wifi(meters = 100.0), wifi(meters = 400.0)))
        }

        val deleted = background { scrubber.scrub(listOf(HOME)) }

        assertEquals(3, deleted)
        assertEquals(1, background { count("gsm_survey_records") })
        assertEquals(0, background { count("lte_survey_records") })
        assertEquals(1, background { count("wifi_survey_records") })
    }

    @Test
    fun `a row just outside is deleted when its accuracy reaches the zone`() {
        background {
            database.gsmRecordDao().insertRecords(listOf(gsm(meters = 300.0, accuracy = 60), gsm(meters = 300.0, accuracy = 40)))
        }

        assertEquals(1, background { scrubber.scrub(listOf(HOME)) })
    }

    @Test
    fun `a row coarser than the box allows is still checked exactly`() {
        background { database.gsmRecordDao().insertRecords(listOf(gsm(meters = 2_000.0, accuracy = 1_900))) }

        assertEquals(1, background { scrubber.scrub(listOf(HOME)) })
    }

    @Test
    fun `rows already sent are deleted too`() {
        background {
            database.wifiRecordDao().insertRecords(listOf(wifi(meters = 0.0).apply { beaconDbUploaded = true }))
        }

        assertEquals(1, background { scrubber.scrub(listOf(HOME)) })
    }

    @Test
    fun `unsent community survey points inside are deleted so they never read as uploaded`() {
        background {
            val points = database.surveyedPointDao()
            points.insert(point(meters = 0.0))
            points.insert(point(meters = 200.0))
            points.insert(point(meters = 1_000.0))
            points.insert(point(meters = 0.0, uploadedMask = SurveyedPointEntity.UPLOADED_OCID))
            points.insert(point(meters = 0.0, source = SurveyedPointEntity.SOURCE_NS_ANALYTICS))
        }

        background { scrubber.scrub(listOf(HOME)) }

        val left = background { database.surveyedPointDao().inBounds(-90.0, -180.0, 90.0, 180.0) }
        // Outside, already sent, and NS Analytics points stay
        assertEquals(3, left.size)
        assertTrue(left.none { it.source == SurveyedPointEntity.SOURCE_COMMUNITY && it.uploadedMask == 0 && it.latitude < 40.005 })

        // The next successful upload no longer marks a point from inside the zone
        background {
            SurveyedPointStore.markCommunityUploaded(database.surveyedPointDao(), Long.MAX_VALUE, ocid = true, beaconDb = true)
        }
        val marked = background { database.surveyedPointDao().inBounds(-90.0, -180.0, 90.0, 180.0) }
        assertEquals(1, marked.count { it.source == SurveyedPointEntity.SOURCE_COMMUNITY && it.uploadedMask != 0 && it.latitude > 40.005 })
    }

    @Test
    fun `no zones deletes nothing`() {
        background { database.gsmRecordDao().insertRecords(listOf(gsm(meters = 0.0))) }

        assertEquals(0, background { scrubber.scrub(emptyList()) })
        assertEquals(1, background { count("gsm_survey_records") })
    }

    @Test
    fun `the editor count only counts rows still waiting for a selected target`() {
        background {
            database.gsmRecordDao().insertRecords(
                listOf(gsm(meters = 0.0), gsm(meters = 0.0).apply { ocidUploaded = true }, gsm(meters = 1_000.0))
            )
            database.wifiRecordDao().insertRecords(listOf(wifi(meters = 0.0)))
        }

        assertEquals(3, background { scrubber.countWaitingInside(HOME, openCelliD = true, beaconDb = true) })
        // OpenCelliD alone: the Cellular row OpenCelliD took and the Wi-Fi row aren't waiting
        assertEquals(1, background { scrubber.countWaitingInside(HOME, openCelliD = true, beaconDb = false) })
        assertEquals(0, background { scrubber.countWaitingInside(HOME, openCelliD = false, beaconDb = false) })
        // Counting deletes nothing
        assertEquals(3, background { count("gsm_survey_records") })
    }

    @Test
    fun `the box opens its longitude near a pole and across the antimeridian`() {
        val polar = CommunityQueueScrubber.Box.around(PrivacyZone("p", "", 89.5, 10.0, 2_000), 100)
        assertEquals(-180.0, polar.minLon, 0.0)
        assertEquals(180.0, polar.maxLon, 0.0)

        val dateLine = CommunityQueueScrubber.Box.around(PrivacyZone("d", "", 0.0, 179.999, 2_000), 100)
        assertEquals(-180.0, dateLine.minLon, 0.0)

        val home = CommunityQueueScrubber.Box.around(HOME, 100)
        val (edgeLat, _) = north(HOME, (HOME.radiusMeters + 100).toDouble())
        assertTrue(home.maxLat > edgeLat)
        assertTrue(home.maxLon > HOME.longitude && home.minLon < HOME.longitude)
    }

    private fun count(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun gsm(meters: Double, accuracy: Int = ACCURACY) = GsmRecordEntity().apply { place(meters, accuracy) }

    private fun point(
        meters: Double,
        source: Int = SurveyedPointEntity.SOURCE_COMMUNITY,
        uploadedMask: Int = 0,
    ) = SurveyedPointEntity().apply {
        val (lat, lon) = north(HOME, meters)
        latitude = lat
        longitude = lon
        time = POINT_TIME_MS
        this.source = source
        observedMask = SurveyedPointEntity.OBSERVED_CELLULAR
        this.uploadedMask = uploadedMask
    }

    private fun lte(meters: Double) = LteRecordEntity().apply { place(meters, ACCURACY) }

    private fun wifi(meters: Double) = WifiBeaconRecordEntity().apply {
        place(meters, ACCURACY)
        bssid = "00:11:22:33:44:55"
        ssid = "test"
    }

    private fun BaseRecordEntity.place(meters: Double, accuracy: Int) {
        val (lat, lon) = north(HOME, meters)
        deviceSerialNumber = "test"
        deviceName = "test"
        deviceTime = "2026-11-20T10:00:00Z"
        latitude = lat
        longitude = lon
        this.accuracy = accuracy
    }

    /** Room refuses the main thread, which Robolectric runs tests on. */
    private fun <T> background(block: () -> T): T = Executors.newSingleThreadExecutor().let { executor ->
        try {
            executor.submit(Callable { block() }).get()
        } finally {
            executor.shutdown()
        }
    }

    private companion object {
        val HOME = PrivacyZone("home", "Home", 40.0, -105.0, 250)
        const val ACCURACY = 10
        const val POINT_TIME_MS = 1_000L
    }
}
