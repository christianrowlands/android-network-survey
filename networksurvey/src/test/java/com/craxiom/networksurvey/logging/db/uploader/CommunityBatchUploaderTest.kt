package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The community upload with one target down, the way OpenCelliD answered 521 for a whole day while
 * BeaconDB took everything: BeaconDB still gets every record exactly once, OpenCelliD keeps its
 * records for the retry, and nothing is deleted before every target has it.
 */
@RunWith(RobolectricTestRunner::class)
class CommunityBatchUploaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database get() = SurveyDatabase.getInstance(context)
    private val server = MockWebServer()
    private val ocidBodies = CopyOnWriteArrayList<String>()
    private val beaconDbBodies = CopyOnWriteArrayList<String>()

    @Volatile
    private var openCelliDCode = OUTAGE_CODE
    private lateinit var lastWorkId: UUID

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.endsWith(OCID_PATH) -> {
                        ocidBodies.add(request.body.readUtf8())
                        if (openCelliDCode == OK_CODE) MockResponse().setBody(OCID_OK_BODY) else MockResponse().setResponseCode(openCelliDCode)
                    }

                    path.endsWith(BEACONDB_PATH) -> {
                        beaconDbBodies.add(request.body.readUtf8())
                        MockResponse().setResponseCode(OK_CODE)
                    }

                    else -> MockResponse().setResponseCode(NOT_FOUND_CODE)
                }
            }
        }
        server.start()
        UploadEndpoints.overrideForTest(server.url("/").toString())
        background { database.clearAllTables() }
    }

    @After
    fun tearDown() {
        UploadEndpoints.overrideForTest(null)
        server.shutdown()
        background { database.clearAllTables() }
    }

    @Test
    fun `an OpenCelliD outage still sends BeaconDB every record once and keeps the Cellular ones`() {
        insert(nr = NR_COUNT, wifi = WIFI_COUNT)

        val result = runWorker(retry = true)

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals("OpenCelliD gave up after its first batch", 1, ocidBodies.size)
        assertEquals(NR_COUNT, occurrences(beaconDbBodies, "\"cellTowers\""))
        assertEquals(WIFI_COUNT, occurrences(beaconDbBodies, "\"wifiAccessPoints\""))
        // Wi-Fi is done (BeaconDB is its only target); Cellular waits for OpenCelliD only
        assertEquals(0, background { database.surveyRecordDao().getWifiRecordCountForUpload(true) })
        assertEquals(NR_COUNT, background { database.surveyRecordDao().getNrRecordCountForUpload(true, false) })
        assertEquals(0, background { database.surveyRecordDao().getNrRecordCountForUpload(false, true) })
        assertEquals(
            CommunityRetryStore.Results(UploadResult.ServerError, UploadResult.Success),
            CommunityRetryStore.read(context, lastWorkId),
        )
    }

    @Test
    fun `the retry after the outage sends OpenCelliD its records and BeaconDB nothing again`() {
        insert(nr = NR_COUNT, wifi = WIFI_COUNT)
        runWorker(retry = true)
        val beaconDbRequests = beaconDbBodies.size
        ocidBodies.clear()
        openCelliDCode = OK_CODE

        val result = runWorker(retry = true)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(beaconDbRequests, beaconDbBodies.size)
        assertEquals(NR_COUNT, ocidBodies.sumOf { body -> body.lines().count { it.contains(NR_LABEL) } })
        assertEquals(0, background { database.surveyRecordDao().getNrRecordCountForUpload(true, true) })
    }

    @Test
    fun `records every target in play already has are deleted even when nothing else waits`() {
        insert(nr = NR_COUNT, wifi = 0)
        runWorker(retry = true)
        assertEquals(NR_COUNT, background { database.surveyRecordDao().getNrRecordCountForUpload(true, false) })

        // OpenCelliD turned off: BeaconDB already has them all, so they can go
        val input = Data.Builder()
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, false)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, true)
            .build()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val worker = TestWorkerBuilder<NsUploaderWorker>(context, executor, input).build()
            assertTrue(background { worker.doWork() } is ListenableWorker.Result.Success)
        } finally {
            executor.shutdown()
        }
        assertEquals(0, background { database.surveyRecordDao().getNrRecordCountForUpload(true, true) })
    }

    @Test
    fun `a failure saved for one upload isn't read for another`() {
        insert(nr = NR_COUNT, wifi = 0)
        runWorker(retry = true)

        assertEquals(null, CommunityRetryStore.read(context, UUID.randomUUID()))
    }

    @Test
    fun `servers the phone can't reach are a connection error, not an unknown failure`() {
        insert(nr = 1, wifi = 1)
        server.shutdown()

        assertTrue(runWorker(retry = true) is ListenableWorker.Result.Retry)
        assertEquals(
            CommunityRetryStore.Results(UploadResult.ConnectionError, UploadResult.ConnectionError),
            CommunityRetryStore.read(context, lastWorkId),
        )
    }

    @Test
    fun `OpenCelliD never gets Wi-Fi`() {
        openCelliDCode = OK_CODE
        insert(nr = 0, wifi = WIFI_COUNT)

        val result = runWorker(retry = true)

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(ocidBodies.isEmpty())
    }

    @Test
    fun `with retry off the outage fails and says which target failed`() {
        insert(nr = NR_COUNT, wifi = 0)

        val result = runWorker(retry = false)

        assertTrue(result is ListenableWorker.Result.Failure)
        val output = (result as ListenableWorker.Result.Failure).outputData
        assertEquals(UploadResult.ServerError.name, output.getString(NsUploaderWorker.OCID_RESULT_ENUM))
        assertEquals(UploadResult.Success.name, output.getString(NsUploaderWorker.BEACONDB_RESULT_ENUM))
    }

    @Test
    fun `a retryable failure on either target retries`() {
        val beaconDbDown = UploadResultBundle().apply {
            setResult(UploadTarget.OpenCelliD, UploadResult.Success)
            setResult(UploadTarget.BeaconDB, UploadResult.ConnectionError)
        }
        val ocidRefusedData = UploadResultBundle().apply {
            setResult(UploadTarget.OpenCelliD, UploadResult.InvalidData)
            setResult(UploadTarget.BeaconDB, UploadResult.Success)
        }

        assertTrue(beaconDbDown.isRetryableError)
        assertFalse(ocidRefusedData.isRetryableError)
    }

    private fun runWorker(retry: Boolean): ListenableWorker.Result {
        val input = Data.Builder()
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, true)
            .putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, true)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, true)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
            .build()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val worker = TestWorkerBuilder<NsUploaderWorker>(context, executor, input).build()
            lastWorkId = worker.id
            return background { worker.doWork() }
        } finally {
            executor.shutdown()
        }
    }

    private fun insert(nr: Int, wifi: Int) = background {
        database.nrRecordDao().insertRecords(List(nr) { nrRecord(it) })
        database.wifiRecordDao().insertRecords(List(wifi) { wifiRecord(it) })
    }

    private fun nrRecord(index: Int) = NrRecordEntity().apply {
        base(index)
        mcc = MCC
        mnc = MNC
        tac = TAC
        nci = NCI_BASE + index
        narfcn = NARFCN
        pci = PCI
        ssRsrp = RSRP
        ta = 0
        servingCell = true
    }

    private fun wifiRecord(index: Int) = WifiBeaconRecordEntity().apply {
        base(index)
        bssid = "00:11:22:33:%02x:%02x".format(index / 256, index % 256)
        ssid = "test"
    }

    private fun com.craxiom.networksurvey.logging.db.model.BaseRecordEntity.base(index: Int) {
        deviceSerialNumber = "test"
        deviceName = "test"
        deviceTime = "2026-10-21T10:41:38.171305-04:00"
        latitude = LATITUDE + index * LATITUDE_STEP
        longitude = LONGITUDE
        accuracy = ACCURACY
        speed = 0f
    }

    private fun occurrences(bodies: List<String>, text: String): Int = bodies.sumOf { it.windowed(text.length).count { w -> w == text } }

    /** Room refuses the main thread, which Robolectric runs tests on. */
    private fun <T> background(block: () -> T): T = Executors.newSingleThreadExecutor().let { executor ->
        try {
            executor.submit(Callable { block() }).get()
        } finally {
            executor.shutdown()
        }
    }

    private companion object {
        const val OCID_PATH = "/measure/uploadCsv"
        const val BEACONDB_PATH = "/v2/geosubmit"
        const val OCID_OK_BODY = "0,OK"
        const val OK_CODE = 200
        const val NOT_FOUND_CODE = 404

        /** Cloudflare's "web server is down", what OpenCelliD answered on 21 Oct 2026. */
        const val OUTAGE_CODE = 521
        const val NR_LABEL = "NR"

        // More than one batch of each, so the run has to keep going after OpenCelliD fails
        const val NR_COUNT = 150
        const val WIFI_COUNT = 230

        const val MCC = 310
        const val MNC = 260
        const val TAC = 8114176
        const val NCI_BASE = 5522767873L
        const val NARFCN = 124590
        const val PCI = 480
        const val RSRP = -98f
        const val LATITUDE = 35.4176
        const val LATITUDE_STEP = 0.0001
        const val LONGITUDE = -80.8836966
        const val ACCURACY = 12
    }
}
