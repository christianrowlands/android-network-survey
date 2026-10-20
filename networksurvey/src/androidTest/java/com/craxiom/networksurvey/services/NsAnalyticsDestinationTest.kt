package com.craxiom.networksurvey.services

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.destinations.LOOPBACK_HOST
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsAnalyticsUploadWorker
import com.craxiom.networksurvey.services.DestinationTestSupport.MARKER_SERIAL
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsRegistration
import com.craxiom.networksurvey.ui.nsanalytics.RegistrationError
import com.craxiom.networksurvey.ui.nsanalytics.RegistrationResult
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * End to end tests of the NS Analytics destination: records captured by the real service, queued
 * in Room, and uploaded by [NsAnalyticsUploadWorker] to a MockWebServer standing in for the
 * backend.
 *
 * NS Analytics is quota billed, so the central guard here is that the record types uploaded are
 * exactly the ones the survey setup chose, and that an Intent API start never widens them.
 */
@RunWith(AndroidJUnit4::class)
class NsAnalyticsDestinationTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private val server = MockWebServer()
    private val uploadBodies = CopyOnWriteArrayList<String>()

    @Volatile
    private var answerWithQuotaExceeded = false

    @Volatile
    private var registerResponse: MockResponse = MockResponse().setResponseCode(404)

    private lateinit var service: NetworkSurveyService

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.endsWith(REGISTER_PATH) == true) return registerResponse
                if (request.path?.endsWith(UPLOAD_PATH) != true) return MockResponse().setResponseCode(404)
                val body = request.body.readUtf8()
                if (answerWithQuotaExceeded) {
                    return MockResponse().setResponseCode(402)
                        .setHeader(RETRY_AFTER_HEADER, RETRY_AFTER_SECONDS)
                        .setBody(QUOTA_EXCEEDED_BODY)
                }
                uploadBodies.add(body)
                val count = JsonParser.parseString(body).asJsonObject.getAsJsonArray("records")
                    .sumOf { it.asJsonObject.getAsJsonArray("messages").size() }
                return MockResponse().setResponseCode(200).setBody(successBody(count))
            }
        }
        server.start(InetAddress.getByName(LOOPBACK_HOST), 0)

        val context = DestinationTestSupport.context()
        // Intent control is off by default; the intent test needs it on.
        DestinationTestSupport.resetPreferences {
            it.putBoolean(NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL, true)
        }
        NsAnalyticsSecureStorage.clearAllCredentials(context)
        SurveyDatabase.getInstance(context).nsAnalyticsDao().clearQueue()
        NsAnalyticsSecureStorage.storeRegistrationData(
            context, DEVICE_TOKEN, WORKSPACE_ID, server.url("/").toString(), DEVICE_ID, WORKSPACE_NAME
        )
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        NsAnalyticsSecureStorage.clearAllCredentials(DestinationTestSupport.context())
        server.shutdown()
    }

    @Test
    fun registration_storesTheWorkspace_andEachFailureIsExplained() = runBlocking {
        val context = DestinationTestSupport.context()
        val registration = NsAnalyticsRegistration(context)
        val code = NsAnalyticsQrData("ns-test-token", WORKSPACE_ID, server.url("/").toString(), "From the code")

        val failures = mapOf(
            MockResponse().setResponseCode(400).setBody("""{"error":"Token already used"}""") to RegistrationError.CodeRejected,
            MockResponse().setResponseCode(400).setBody("""{"error":"Device limit reached (5/5)"}""") to RegistrationError.DeviceLimit,
            MockResponse().setResponseCode(403).setBody("""{"error":"x","error_code":"LICENSE_EXPIRED"}""") to RegistrationError.License,
            MockResponse().setResponseCode(503) to RegistrationError.Server,
        )
        for ((response, error) in failures) {
            registerResponse = response
            assertThat(registration.register(code)).isEqualTo(RegistrationResult.Failure(error))
        }

        registerResponse = MockResponse().setResponseCode(200).setBody(
            """{"device_id":"$DEVICE_ID","device_token":"$DEVICE_TOKEN","workspace_id":"$WORKSPACE_ID",""" +
                """"workspace_name":"Acme","message":"ok"}"""
        )
        assertThat(registration.register(code)).isEqualTo(RegistrationResult.Success("Acme"))
        assertThat(NsAnalyticsSecureStorage.getWorkspaceName(context)).isEqualTo("Acme")

        // A server that cannot be reached is a network problem, not a bad code.
        val unreachable = code.copy(apiUrl = "http://$LOOPBACK_HOST:1/")
        assertThat(registration.register(unreachable)).isEqualTo(RegistrationResult.Failure(RegistrationError.Network))
    }

    @Test
    fun uploadedTypes_matchTheSurveySetup() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR, RecordType.WIFI), setOf(Destination.NS_ANALYTICS))

        DestinationTestSupport.injectLte(service)
        DestinationTestSupport.injectWifi(service)
        DestinationTestSupport.injectGnss(service)
        DestinationTestSupport.injectBluetooth(service)
        awaitQueuedMarkers(2)

        assertThat(runUploadWorker()).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(uploadedMarkerTypes()).containsExactly(
            NsAnalyticsConstants.RECORD_TYPE_LTE, NsAnalyticsConstants.RECORD_TYPE_WIFI
        )
        assertThat(uploadedTypes()).containsNoneOf(
            NsAnalyticsConstants.RECORD_TYPE_GNSS, NsAnalyticsConstants.RECORD_TYPE_BLUETOOTH
        )
    }

    @Test
    fun intentStartWhileRecording_neverWidensWhatNsAnalyticsReceives() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.NS_ANALYTICS))

        // The real Intent API path: a START_SURVEY intent through onStartCommand, which merges into
        // the running survey instead of replacing it.
        val context = DestinationTestSupport.context()
        context.startService(
            Intent(context, NetworkSurveyService::class.java)
                .setAction(NetworkSurveyService.ACTION_START_SURVEY)
                .putExtra(NetworkSurveyConstants.EXTRA_RECORD_TYPES, arrayOf("cellular", "wifi", "gnss"))
                .putExtra(NetworkSurveyConstants.EXTRA_DESTINATIONS, arrayOf("ns_analytics", "log_files"))
        )
        assertThat(awaitCondition {
            Destination.LOG_FILES in service.surveyStateFlow.value.destinations
        }).isTrue()

        DestinationTestSupport.injectLte(service)
        DestinationTestSupport.injectWifi(service)
        DestinationTestSupport.injectGnss(service)
        awaitQueuedMarkers(1)
        // Give any wrongly registered Wi-Fi or GNSS listener time to queue its record too.
        Thread.sleep(QUIET_PERIOD_MS)

        runUploadWorker()
        assertThat(uploadedMarkerTypes()).containsExactly(NsAnalyticsConstants.RECORD_TYPE_LTE)
    }

    @Test
    fun quotaExceeded_pausesUploads_andASuccessfulRecheckResumesThem() {
        val context = DestinationTestSupport.context()
        // Set before the survey starts, so the periodic upload it schedules is refused too and the
        // record stays queued for the explicit run below.
        answerWithQuotaExceeded = true
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.NS_ANALYTICS))
        DestinationTestSupport.injectLte(service)
        awaitQueuedMarkers(1)

        assertThat(runUploadWorker()).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(NsAnalyticsSecureStorage.isUploadPausedForQuota(context)).isTrue()
        val recheck = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(QUOTA_RECHECK_WORK_NAME).get()
        assertThat(recheck.map { it.state }).contains(WorkInfo.State.ENQUEUED)

        answerWithQuotaExceeded = false
        assertThat(runUploadWorker()).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(NsAnalyticsSecureStorage.isUploadPausedForQuota(context)).isFalse()
        assertThat(uploadedMarkerTypes()).contains(NsAnalyticsConstants.RECORD_TYPE_LTE)
    }

    private fun runUploadWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<NsAnalyticsUploadWorker>(DestinationTestSupport.context()).build().doWork()
    }

    /**
     * Records reach the Room queue on a background executor, so wait for them to land. Starting NS
     * Analytics also schedules the real periodic upload, which can send them before this looks, so
     * records already uploaded to the fake server count too.
     */
    private fun awaitQueuedMarkers(expected: Int) {
        val dao = SurveyDatabase.getInstance(DestinationTestSupport.context()).nsAnalyticsDao()
        assertThat(awaitCondition {
            val queued = dao.getUploadableRecords(QUEUE_SCAN_LIMIT, NsAnalyticsConstants.MAX_RETRY_COUNT)
                .count { it.protobufJson.contains(MARKER_SERIAL) }
            val uploaded = batches().sumOf { batch ->
                batch.getAsJsonArray("messages").count { it.toString().contains(MARKER_SERIAL) }
            }
            queued + uploaded >= expected
        }).isTrue()
    }

    private fun batches() = uploadBodies.flatMap { body ->
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("records").map { it.asJsonObject }
    }

    private fun uploadedTypes(): Set<String> = batches().map { it.get("type").asString }.toSet()

    private fun uploadedMarkerTypes(): Set<String> = batches()
        .filter { batch -> batch.getAsJsonArray("messages").any { it.toString().contains(MARKER_SERIAL) } }
        .map { it.get("type").asString }
        .toSet()

    private companion object {
        const val UPLOAD_PATH = "api/v1/device/upload"
        const val REGISTER_PATH = "api/v1/device/register"
        const val DEVICE_TOKEN = "ns-test-device-token"
        const val DEVICE_ID = "ns-test-device-id"
        const val WORKSPACE_ID = "ns-test-workspace"
        const val WORKSPACE_NAME = "Test workspace"
        const val QUOTA_RECHECK_WORK_NAME = "ns_analytics_quota_recheck"
        const val RETRY_AFTER_HEADER = "Retry-After"
        const val RETRY_AFTER_SECONDS = "600"
        const val QUEUE_SCAN_LIMIT = 1_000
        const val QUIET_PERIOD_MS = 2_000L

        const val QUOTA_EXCEEDED_BODY = """{"error":"quota_exceeded","error_code":"QUOTA_EXCEEDED",
            "message":"Monthly record limit reached","current_usage":1000,"max_records":1000,
            "limit_type":"monthly"}"""

        fun successBody(count: Int) = """{"batch_id":"test","status":"completed","message":null,
            "processed":$count,"skipped":0,"failed":0,"total_records":$count,
            "workspace_name":"$WORKSPACE_NAME"}"""
    }
}
