package com.craxiom.networksurvey.ui.cellular

import android.content.Context
import androidx.room.Room
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSourceFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointTap
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests that the controller actually hands the user's filter choices to the DAO.
 *
 * The DAO tests pin the enum-to-SQL contract and [SurveyedPointFilterSelectionTest] pins when the
 * chips appear, but neither notices if the controller stops passing a filter through: the query
 * would quietly return everything while the UI still claimed to be filtering. Driving it through
 * the public [SurveyedPointsController.resolve] keeps the assertion on behavior rather than on a
 * private method, since that is the path a tap on a zoomed-out cell takes.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyedPointsControllerFilterTest {

    private lateinit var database: SurveyDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var controller: SurveyedPointsController

    /** The lattice cell the tap resolves to, and a point comfortably inside it. */
    private val step = 0.001
    private val latKey = 128_000L
    private val lonKey = 103_000L
    private val insideLat = 38.0005
    private val insideLon = -76.9995

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, SurveyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.Unconfined)
        controller = SurveyedPointsController(scope)
        controller.attach(database.surveyedPointDao())
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun uploadFilterReachesTheQuery() {
        insert(uploaded = 0)
        insert(uploaded = SurveyedPointEntity.UPLOADED_OCID)

        assertEquals(2, resolveCell().size)

        controller.setUploadFilter(SurveyedPointUploadFilter.NOT_SENT)
        assertEquals(listOf(0), resolveCell().map { it.uploadedMask })

        controller.setUploadFilter(SurveyedPointUploadFilter.SENT)
        assertEquals(
            listOf(SurveyedPointEntity.UPLOADED_OCID),
            resolveCell().map { it.uploadedMask }
        )

        controller.setUploadFilter(SurveyedPointUploadFilter.ANY)
        assertEquals(2, resolveCell().size)
    }

    @Test
    fun sourceAndKindFiltersStillReachTheQuery() {
        insert(source = SurveyedPointEntity.SOURCE_COMMUNITY)
        insert(source = SurveyedPointEntity.SOURCE_NS_ANALYTICS)
        insert(
            source = SurveyedPointEntity.SOURCE_NS_ANALYTICS,
            observed = SurveyedPointEntity.OBSERVED_WIFI
        )

        // Cellular is the controller's default kind, so the Wi-Fi row is already excluded
        assertEquals(2, resolveCell().size)

        controller.setSourceFilter(SurveyedPointSourceFilter.NS_ANALYTICS)
        assertEquals(
            listOf(SurveyedPointEntity.SOURCE_NS_ANALYTICS),
            resolveCell().map { it.source }
        )

        controller.setKind(SurveyedPointKind.WIFI)
        assertEquals(
            listOf(SurveyedPointEntity.OBSERVED_WIFI),
            resolveCell().map { it.observedMask }
        )
    }

    @Test
    fun filtersCombineRatherThanOverrideEachOther() {
        insert(source = SurveyedPointEntity.SOURCE_NS_ANALYTICS, uploaded = 0)
        insert(
            source = SurveyedPointEntity.SOURCE_NS_ANALYTICS,
            uploaded = SurveyedPointEntity.UPLOADED_NS_ANALYTICS
        )
        insert(source = SurveyedPointEntity.SOURCE_COMMUNITY, uploaded = 0)

        controller.setSourceFilter(SurveyedPointSourceFilter.NS_ANALYTICS)
        controller.setUploadFilter(SurveyedPointUploadFilter.NOT_SENT)

        val points = resolveCell()
        assertEquals(1, points.size)
        assertEquals(SurveyedPointEntity.SOURCE_NS_ANALYTICS, points.single().source)
        assertEquals(0, points.single().uploadedMask)
    }

    private fun resolveCell(): List<SurveyedPointEntity> = runBlocking {
        controller.resolve(SurveyedPointTap.Cell(latKey, lonKey, step, count = 0))?.points
            ?: emptyList()
    }

    private fun insert(
        source: Int = SurveyedPointEntity.SOURCE_COMMUNITY,
        observed: Int = SurveyedPointEntity.OBSERVED_CELLULAR,
        uploaded: Int = 0,
    ) {
        val entity = SurveyedPointEntity()
        entity.latitude = insideLat
        entity.longitude = insideLon
        entity.time = 1
        entity.source = source
        entity.observedMask = observed
        entity.uploadedMask = uploaded
        database.surveyedPointDao().insert(entity)
    }
}
