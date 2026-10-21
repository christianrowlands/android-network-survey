package com.craxiom.networksurvey.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.WifiBeaconRecordData
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.destinations.LOOPBACK_HOST
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.logging.db.uploader.UploadEndpoints
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.util.NsUtils
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.BoolValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End to end tests of the Community databases destination: records captured by the survey into the
 * upload database and sent by [NsUploaderWorker] to a MockWebServer standing in for both OpenCelliD
 * and BeaconDB.
 *
 * The organization policy block (`allow_external_data_upload`) needs a device policy controller to
 * set on a real phone, so it stays covered by the JVM `SurveyPolicyTest` instead.
 */
@RunWith(AndroidJUnit4::class)
class CommunityUploadTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private val server = MockWebServer()
    private val ocidBodies = CopyOnWriteArrayList<String>()
    private val beaconDbBodies = CopyOnWriteArrayList<String>()

    @Volatile
    private var answerWithServerError = false

    private lateinit var service: NetworkSurveyService
    private var nextLatitude = START_LATITUDE

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (answerWithServerError) return MockResponse().setResponseCode(503)
                val path = request.path.orEmpty()
                return when {
                    path.endsWith(OCID_PATH) -> {
                        ocidBodies.add(request.body.readUtf8())
                        MockResponse().setResponseCode(200).setBody(OCID_SUCCESS_BODY)
                    }

                    path.endsWith(BEACONDB_PATH) -> {
                        beaconDbBodies.add(request.body.readUtf8())
                        MockResponse().setResponseCode(200)
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start(InetAddress.getByName(LOOPBACK_HOST), 0)
        UploadEndpoints.overrideForTest(server.url("/").toString())

        SurveyDatabase.getInstance(DestinationTestSupport.context()).clearAllTables()
        DestinationTestSupport.resetPreferences {
            it.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, false)
        }
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        WorkManager.getInstance(DestinationTestSupport.context()).cancelAllWork()
        UploadEndpoints.overrideForTest(null)
        server.shutdown()
    }

    @Test
    fun surveyRecords_reachOpenCelliDAndBeaconDb() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR, RecordType.WIFI), setOf(Destination.COMMUNITY))

        injectUploadableLte()
        injectUploadableWifi()
        awaitStoredRecords(cellular = 1, wifi = 1)

        assertThat(runUploadWorker(retry = false)).isInstanceOf(ListenableWorker.Result.Success::class.java)

        // OpenCelliD takes cellular only, as a CSV; BeaconDB takes cellular and Wi-Fi as JSON.
        assertThat(ocidBodies.joinToString()).contains(ECI.toString())
        assertThat(ocidBodies.joinToString()).doesNotContain(WIFI_BSSID)
        assertThat(beaconDbBodies.joinToString()).contains(WIFI_BSSID)
        // The Mission ID is never sent to the public databases.
        val missionId = service.rolledMissionId.orEmpty()
        if (missionId.isNotEmpty()) {
            assertThat(ocidBodies.joinToString() + beaconDbBodies.joinToString()).doesNotContain(missionId)
        }
    }

    @Test
    fun serverError_withRetryEnabled_asksWorkManagerToRetry() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.COMMUNITY))
        injectUploadableLte()
        awaitStoredRecords(cellular = 1, wifi = 0)

        answerWithServerError = true
        assertThat(runUploadWorker(retry = true)).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(runUploadWorker(retry = false)).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    @Test
    fun autoUpload_at500Records_enqueuesWifiOnlyWork() {
        DestinationTestSupport.resetPreferences {
            it.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_WIFI_ONLY, true)
        }
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.COMMUNITY))

        repeat(NetworkSurveyConstants.AUTO_UPLOAD_RECORD_THRESHOLD) { injectUploadableLte() }

        val workManager = WorkManager.getInstance(DestinationTestSupport.context())
        assertThat(awaitCondition {
            workManager.getWorkInfosForUniqueWork(NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME)
                .get().isNotEmpty()
        }).isTrue()
        val work = workManager.getWorkInfosForUniqueWork(NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME).get()
        assertThat(work.first().constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }

    /**
     * Sends a complete serving-cell LTE record. Each one is placed about a kilometer from the last,
     * because the upload store skips records that have not moved.
     */
    private fun injectUploadableLte() {
        val record = LteRecord.newBuilder().setData(
            LteRecordData.newBuilder()
                .setDeviceSerialNumber(DestinationTestSupport.MARKER_SERIAL)
                .setDeviceTime(NsUtils.getRfc3339String(ZonedDateTime.now()))
                .setLatitude(nextLatitude())
                .setLongitude(LONGITUDE)
                .setAccuracy(ACCURACY_METERS)
                .setMcc(Int32Value.of(MCC))
                .setMnc(Int32Value.of(MNC))
                .setTac(Int32Value.of(TAC))
                .setEci(Int32Value.of(ECI))
                .setPci(Int32Value.of(PCI))
                .setEarfcn(Int32Value.of(EARFCN))
                .setRsrp(FloatValue.of(RSRP))
                .setServingCell(BoolValue.of(true))
        ).build()
        service.surveyRecordProcessorForTest
            .notifyCellularListeners(listOf(CellularRecordWrapper(CellularProtocol.LTE, record)), 0)
    }

    private fun injectUploadableWifi() {
        val record = WifiBeaconRecord.newBuilder().setData(
            WifiBeaconRecordData.newBuilder()
                .setDeviceSerialNumber(DestinationTestSupport.MARKER_SERIAL)
                .setDeviceTime(NsUtils.getRfc3339String(ZonedDateTime.now()))
                .setLatitude(nextLatitude())
                .setLongitude(LONGITUDE)
                .setAccuracy(ACCURACY_METERS)
                .setSourceAddress(WIFI_BSSID)
                .setBssid(WIFI_BSSID)
                .setSsid("ns-community-test")
                .setSignalStrength(FloatValue.of(WIFI_SIGNAL))
                .setChannel(Int32Value.of(WIFI_CHANNEL))
        ).build()
        service.surveyRecordProcessorForTest.notifyWifiBeaconRecordListeners(listOf(WifiRecordWrapper(record, "")))
    }

    private fun nextLatitude(): Double = nextLatitude.also { nextLatitude += LATITUDE_STEP }

    private fun awaitStoredRecords(cellular: Int, wifi: Int) {
        val dao = SurveyDatabase.getInstance(DestinationTestSupport.context()).surveyRecordDao()
        assertThat(awaitCondition {
            dao.getLteRecordCountForUpload(true, true) >= cellular && dao.getWifiRecordCountForUpload(true) >= wifi
        }).isTrue()
    }

    private fun runUploadWorker(retry: Boolean): ListenableWorker.Result =
        TestListenableWorkerBuilder<NsUploaderWorker>(DestinationTestSupport.context())
            .setInputData(
                Data.Builder()
                    .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, true)
                    .putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, true)
                    .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, true)
                    .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
                    .build()
            )
            .build()
            .doWork()

    private companion object {
        const val OCID_PATH = "measure/uploadCsv"
        const val BEACONDB_PATH = "v2/geosubmit"
        const val OCID_SUCCESS_BODY = "0,OK"

        const val START_LATITUDE = 40.0
        const val LATITUDE_STEP = 0.01
        const val LONGITUDE = -75.0
        const val ACCURACY_METERS = 5
        const val MCC = 310
        const val MNC = 260
        const val TAC = 12345
        const val ECI = 25_600_003
        const val PCI = 101
        const val EARFCN = 66811
        const val RSRP = -95f
        const val WIFI_BSSID = "02:00:00:00:0a:01"
        const val WIFI_SIGNAL = -60f
        const val WIFI_CHANNEL = 6
    }
}
