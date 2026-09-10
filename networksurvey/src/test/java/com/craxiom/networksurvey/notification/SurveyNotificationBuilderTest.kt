package com.craxiom.networksurvey.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import org.robolectric.RuntimeEnvironment
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One test per row of the survey notification state table: title, collapsed text, expanded text,
 * small icon, and chronometer for every state the service can be in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SurveyNotificationBuilderTest {

    private lateinit var context: Context
    private lateinit var builder: SurveyNotificationBuilder

    private val sessionStart = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        builder = SurveyNotificationBuilder(context)
    }

    private fun title(n: Notification) = n.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
    private fun text(n: Notification) = n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
    private fun bigText(n: Notification) = n.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
    private fun chronometer(n: Notification) = n.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER)
    private fun icon(n: Notification) = n.smallIcon.resId

    @Test
    fun idleService_showsSurveyInactiveWithNoTextOrChronometer() {
        val n = builder.build(SurveyNotificationState())

        assertEquals("Survey Inactive", title(n))
        assertNull(text(n))
        assertNull(bigText(n))
        assertFalse(chronometer(n))
        assertEquals(R.drawable.ic_notification_survey, icon(n))
    }

    @Test
    fun fileLoggingOnly_listsLogFilesAndScannedProtocols() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, scanningCellular = true, scanningWifi = true, scanningGnss = true,
                phoneStateActive = true, sessionStartMs = sessionStart
            )
        )

        assertEquals("Survey Active", title(n))
        assertEquals("Log files", text(n))
        assertEquals("Log files\nScanning: Cellular, Phone State, Wi-Fi, GNSS", bigText(n))
        assertTrue(chronometer(n))
        assertEquals(sessionStart, n.`when`)
        assertEquals(R.drawable.ic_notification_survey, icon(n))
    }

    @Test
    fun mqttConnectedOnly_listsMqtt() {
        val n = builder.build(
            SurveyNotificationState(mqttState = ConnectionState.CONNECTED, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Survey Active", title(n))
        assertEquals("MQTT", text(n))
        assertEquals("MQTT\nScanning: Cellular", bigText(n))
        assertTrue(chronometer(n))
    }

    @Test
    fun mqttConnecting_showsConnectingLineCollapsedAndDestinationsExpanded() {
        val n = builder.build(
            SurveyNotificationState(mqttState = ConnectionState.CONNECTING, fileLogging = true, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Survey Active", title(n))
        assertEquals("MQTT: Attempting to Connect", text(n))
        assertEquals("MQTT: Attempting to Connect\nLog files, MQTT\nScanning: Cellular", bigText(n))
    }

    @Test
    fun loggingAndMqtt_listsBothInFixedOrder() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, mqttState = ConnectionState.CONNECTED, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Log files, MQTT", text(n))
    }

    @Test
    fun mqttDropMode_showsDroppingWithFileLoggingContinues() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, mqttState = ConnectionState.CONNECTED, mqttDropping = true,
                scanningCellular = true, sessionStartMs = sessionStart
            )
        )

        assertEquals("Survey Active", title(n))
        assertEquals("MQTT Messages Dropping", text(n))
        assertEquals("MQTT Messages Dropping\nFile Logging Continues\nLog files, MQTT\nScanning: Cellular", bigText(n))
        assertEquals(R.drawable.ic_notification_survey, icon(n))
    }

    @Test
    fun backpressurePause_showsSurveysPausedWithNetworkInterrupted() {
        val n = builder.build(
            SurveyNotificationState(mqttState = ConnectionState.CONNECTED, pausedForBackpressure = true, sessionStartMs = sessionStart)
        )

        assertEquals("Surveys Paused", title(n))
        assertEquals("Network interrupted", text(n))
        assertEquals("Network interrupted\nMQTT", bigText(n))
        assertEquals(R.drawable.ic_pause, icon(n))
    }

    @Test
    fun batteryPause_showsSurveysPausedWithBatteryLevel() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, pausedForBattery = true, batteryLevel = 14, sessionStartMs = sessionStart)
        )

        assertEquals("Surveys Paused", title(n))
        assertEquals("Battery low (14%)", text(n))
        assertEquals("Battery low (14%)\nLog files", bigText(n))
        assertEquals(R.drawable.ic_pause, icon(n))
    }

    @Test
    fun batteryPauseOutranksStallAndDropMode() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, mqttState = ConnectionState.CONNECTED, mqttDropping = true,
                pausedForBattery = true, batteryLevel = 9, stalledMinutes = 10, sessionStartMs = sessionStart
            )
        )

        assertEquals("Surveys Paused", title(n))
        assertEquals("Battery low (9%)", text(n))
    }

    @Test
    fun stall_showsNoRecordsTitleWithDestinationsAsText() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, scanningCellular = true, stalledMinutes = 5, sessionStartMs = sessionStart)
        )

        assertEquals("No records for 5 min", title(n))
        assertEquals("Log files", text(n))
        assertEquals("Log files\nScanning: Cellular", bigText(n))
        assertEquals(R.drawable.ic_pause, icon(n))
        assertTrue(chronometer(n))
    }

    @Test
    fun communitySurvey_listsOpenCellidAndBeaconDb() {
        val n = builder.build(
            SurveyNotificationState(communitySurvey = true, scanningCellular = true, scanningWifi = true, sessionStartMs = sessionStart)
        )

        assertEquals("Survey Active", title(n))
        assertEquals("OpenCelliD & BeaconDB", text(n))
        assertEquals("OpenCelliD & BeaconDB\nScanning: Cellular, Wi-Fi", bigText(n))
    }

    @Test
    fun nsAnalyticsSurvey_listsNsAnalytics() {
        val n = builder.build(
            SurveyNotificationState(nsAnalytics = true, scanningCellular = true, phoneStateActive = true, sessionStartMs = sessionStart)
        )

        assertEquals("Survey Active", title(n))
        assertEquals("NS Analytics", text(n))
        assertEquals("NS Analytics\nScanning: Cellular, Phone State", bigText(n))
    }

    @Test
    fun grpcConnected_appearsAsDestination() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, grpcState = ConnectionState.CONNECTED, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Log files, gRPC", text(n))
    }

    @Test
    fun grpcConnecting_showsConnectingLine() {
        val n = builder.build(
            SurveyNotificationState(grpcState = ConnectionState.CONNECTING, sessionStartMs = sessionStart)
        )

        assertEquals("Survey Active", title(n))
        assertEquals("gRPC: Attempting to Connect", text(n))
    }

    @Test
    fun everythingAtOnce_listsAllDestinationsInOrder() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, mqttState = ConnectionState.CONNECTED, grpcState = ConnectionState.CONNECTED,
                communitySurvey = true, nsAnalytics = true,
                scanningCellular = true, scanningWifi = true, scanningBluetooth = true, scanningGnss = true,
                phoneStateActive = true, cdrActive = true, sessionStartMs = sessionStart
            )
        )

        assertEquals("Log files, MQTT, gRPC, OpenCelliD & BeaconDB, NS Analytics", text(n))
        assertEquals(
            "Log files, MQTT, gRPC, OpenCelliD & BeaconDB, NS Analytics\nScanning: Cellular, Phone State, Wi-Fi, Bluetooth, GNSS, CDR",
            bigText(n)
        )
    }

    @Test
    fun disconnectingMqtt_doesNotCountAsActive() {
        val n = builder.build(SurveyNotificationState(mqttState = ConnectionState.DISCONNECTING))

        assertEquals("Survey Inactive", title(n))
        assertNull(text(n))
    }

    @Test
    fun notificationIsOngoingAndOnlyAlertsOnce() {
        val n = builder.build(SurveyNotificationState(fileLogging = true, sessionStartMs = sessionStart))

        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.CATEGORY_SERVICE, n.category)
    }
}
