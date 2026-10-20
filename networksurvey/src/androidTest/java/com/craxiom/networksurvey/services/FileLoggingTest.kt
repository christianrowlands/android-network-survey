package com.craxiom.networksurvey.services

import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.destinations.awaitCondition
import com.craxiom.networksurvey.logging.LogFileProvider
import com.craxiom.networksurvey.services.DestinationTestSupport.MARKER_SERIAL
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.logfiles.LogFilesListing
import com.craxiom.networksurvey.ui.logfiles.LogFilesRepository
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End to end tests of the Log files destination: the survey writes one CSV per record type under
 * Downloads/NetworkSurveyData/csv, rolls files over at the configured size, and adds the device
 * status CSV whenever a scanning record type is being logged.
 */
@RunWith(AndroidJUnit4::class)
class FileLoggingTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private lateinit var service: NetworkSurveyService
    private var testStartMs = 0L

    @Before
    fun setUp() {
        DestinationTestSupport.resetPreferences {
            it.putString(NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB, ROLLOVER_SIZE_MB)
        }
        // File timestamps have one second resolution on some file systems.
        testStartMs = System.currentTimeMillis() - 1_000L
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        DestinationTestSupport.stop(service)
        newFiles().forEach { it.delete() }
    }

    @Test
    fun eachRecordType_isWrittenToItsOwnCsv() {
        val result = DestinationTestSupport.start(service, FILE_PREFIXES.keys, setOf(Destination.LOG_FILES))

        DestinationTestSupport.injectLte(service)
        DestinationTestSupport.injectWifi(service)
        DestinationTestSupport.injectBluetooth(service)
        DestinationTestSupport.injectGnss(service)
        DestinationTestSupport.injectPhoneState(service)

        for ((type, prefix) in FILE_PREFIXES) {
            // A type the survey itself skipped (for example Bluetooth while the adapter is still
            // turning on) is the phone's state, not a logging bug; the start result says so.
            result.recordTypeProblems[type]?.let { reason ->
                println("Skipping the $prefix check: the survey skipped $type ($reason)")
                continue
            }
            assertWithMessage(prefix).that(awaitCondition { markerFiles(prefix).isNotEmpty() }).isTrue()
            // Comment lines (the app version) first, then a header row, then the records.
            val rows = markerFiles(prefix).first().readLines().filterNot { it.startsWith(CSV_COMMENT) }
            assertWithMessage(prefix).that(rows.size).isAtLeast(2)
            assertWithMessage(prefix).that(rows.first()).contains(",")
            assertWithMessage(prefix).that(rows.first()).doesNotContain(MARKER_SERIAL)
        }
        assertThat(result.recordTypeProblems.keys).doesNotContain(RecordType.CELLULAR)
    }

    @Test
    fun loggingAScanningType_alsoWritesTheDeviceStatusCsv() {
        DestinationTestSupport.start(service, setOf(RecordType.WIFI), setOf(Destination.LOG_FILES))

        assertThat(awaitCondition(DEVICE_STATUS_TIMEOUT_MS) {
            newFiles(NetworkSurveyConstants.DEVICESTATUS_FILE_NAME_PREFIX).any { it.length() > 0 }
        }).isTrue()
    }

    @Test
    fun aFullFile_rollsOverToANewOne() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))

        repeat(ROLLOVER_RECORDS) { DestinationTestSupport.injectLte(service) }

        assertThat(awaitCondition {
            newFiles(NetworkSurveyConstants.LTE_FILE_NAME_PREFIX).size >= 2
        }).isTrue()
    }

    @Test
    fun theLogFilesPage_listsTheNewFile_andSharesItThroughTheProvider() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.LOG_FILES))
        DestinationTestSupport.injectLte(service)
        assertThat(awaitCondition { markerFiles(NetworkSurveyConstants.LTE_FILE_NAME_PREFIX).isNotEmpty() }).isTrue()
        val written = markerFiles(NetworkSurveyConstants.LTE_FILE_NAME_PREFIX).first()

        val listing = LogFilesRepository.forLogFiles().list() as LogFilesListing.Files
        assertThat(listing.files.map { it.name }).contains(written.name)

        val context = DestinationTestSupport.context()
        val uri = FileProvider.getUriForFile(context, context.packageName + LogFileProvider.AUTHORITY_SUFFIX, written)
        val shared = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        assertThat(shared).contains(MARKER_SERIAL)
    }

    private fun csvDirectory() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME
    )

    private fun newFiles(prefix: String = ""): List<File> = csvDirectory().listFiles().orEmpty()
        .filter { it.name.startsWith(prefix) && it.lastModified() >= testStartMs }

    private fun markerFiles(prefix: String) = newFiles(prefix).filter { it.readText().contains(MARKER_SERIAL) }

    private companion object {
        const val ROLLOVER_SIZE_MB = "1"
        const val CSV_COMMENT = "#"

        /** The logger checks the file size every 5,000 records; this crosses 1 MB at a check. */
        const val ROLLOVER_RECORDS = 20_000
        const val DEVICE_STATUS_TIMEOUT_MS = 30_000L

        val FILE_PREFIXES = mapOf(
            RecordType.CELLULAR to NetworkSurveyConstants.LTE_FILE_NAME_PREFIX,
            RecordType.WIFI to NetworkSurveyConstants.WIFI_FILE_NAME_PREFIX,
            RecordType.BLUETOOTH to NetworkSurveyConstants.BLUETOOTH_FILE_NAME_PREFIX,
            RecordType.GNSS to NetworkSurveyConstants.GNSS_FILE_NAME_PREFIX,
            RecordType.PHONE_STATE to NetworkSurveyConstants.PHONESTATE_FILE_NAME_PREFIX,
        )
    }
}
