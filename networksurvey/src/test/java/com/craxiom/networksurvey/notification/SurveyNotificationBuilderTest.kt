package com.craxiom.networksurvey.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.LeftOutItem
import com.craxiom.networksurvey.survey.status.StatusWord
import com.craxiom.networksurvey.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * One test per row of the survey notification state table: title, collapsed text, expanded text,
 * small icon, and chronometer for every state the service can be in.
 */
@RunWith(RobolectricTestRunner::class)
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

        assertEquals("Ready", title(n))
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

        assertEquals("Recording", title(n))
        assertEquals("Log files", text(n))
        assertEquals("Log files\nScanning: Cellular, Phone state, Wi-Fi, GNSS", bigText(n))
        assertTrue(chronometer(n))
        assertEquals(sessionStart, n.`when`)
        assertEquals(R.drawable.ic_notification_survey, icon(n))
    }

    @Test
    fun leftOutItems_getALineEach() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, scanningCellular = true, sessionStartMs = sessionStart,
                leftOut = listOf(
                    LeftOutItem.Type(RecordType.BLUETOOTH, SkipReason.RADIO_OFF),
                    LeftOutItem.Dest(Destination.MQTT, SkipReason.NOT_CONFIGURED),
                ),
            )
        )
        val expanded = bigText(n)!!
        assertTrue(expanded, expanded.contains("Bluetooth is off, so Bluetooth isn't being recorded"))
        assertTrue(expanded, expanded.contains("MQTT stream isn't set up"))
    }

    @Test
    fun mqttConnectedOnly_listsMqtt() {
        val n = builder.build(
            SurveyNotificationState(mqttState = ConnectionState.CONNECTED, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Recording", title(n))
        assertEquals("MQTT stream", text(n))
        assertEquals("MQTT stream\nScanning: Cellular", bigText(n))
        assertTrue(chronometer(n))
    }

    @Test
    fun mqttConnecting_showsConnectingLineCollapsedAndDestinationsExpanded() {
        val n = builder.build(
            SurveyNotificationState(
                mqttState = ConnectionState.CONNECTING, fileLogging = true, scanningCellular = true, sessionStartMs = sessionStart,
                destinationStatuses = mapOf(
                    Destination.LOG_FILES to DestinationStatus(StatusWord.SAVING),
                    Destination.MQTT to DestinationStatus(StatusWord.CONNECTING),
                ),
            )
        )

        assertEquals("Recording", title(n))
        assertEquals("MQTT stream: Connecting", text(n))
        assertEquals("MQTT stream: Connecting\nLog files, MQTT stream\nScanning: Cellular", bigText(n))
    }

    @Test
    fun aStreamThatCantConnect_saysSo_andHealthyOrPausedDestinationsGetNoLine() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, mqttState = ConnectionState.CONNECTING, grpcState = ConnectionState.CONNECTED,
                communitySurvey = true, sessionStartMs = sessionStart,
                destinationStatuses = mapOf(
                    Destination.LOG_FILES to DestinationStatus(StatusWord.SAVING),
                    Destination.MQTT to DestinationStatus(StatusWord.CANT_CONNECT),
                    Destination.GRPC to DestinationStatus(StatusWord.LIVE),
                    Destination.COMMUNITY to DestinationStatus(StatusWord.COLLECTING),
                ),
            )
        )

        assertEquals("MQTT stream: Can't connect", text(n))
        assertFalse(bigText(n).orEmpty().contains("gRPC stream:"))
        assertFalse(bigText(n).orEmpty().contains("Community databases:"))
    }

    @Test
    fun loggingAndMqtt_listsBothInFixedOrder() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, mqttState = ConnectionState.CONNECTED, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Log files, MQTT stream", text(n))
    }

    @Test
    fun mqttDropMode_showsDroppingWithFileLoggingContinues() {
        val n = builder.build(
            SurveyNotificationState(
                fileLogging = true, mqttState = ConnectionState.CONNECTED, mqttDropping = true,
                scanningCellular = true, sessionStartMs = sessionStart
            )
        )

        assertEquals("Recording", title(n))
        assertEquals(context.getString(R.string.mqtt_drop_mode_title), text(n))
        assertEquals(
            context.getString(R.string.mqtt_drop_mode_title) + "\n" + context.getString(R.string.mqtt_drop_mode_subtitle) +
                "\nLog files, MQTT stream\nScanning: Cellular",
            bigText(n),
        )
        assertEquals(R.drawable.ic_notification_survey, icon(n))
    }

    @Test
    fun backpressurePause_showsSurveysPausedWithNetworkInterrupted() {
        val n = builder.build(
            SurveyNotificationState(mqttState = ConnectionState.CONNECTED, pausedForBackpressure = true, sessionStartMs = sessionStart)
        )

        assertEquals("Paused", title(n))
        assertEquals("Network interrupted", text(n))
        assertEquals("Network interrupted\nMQTT stream", bigText(n))
        assertEquals(R.drawable.ic_pause, icon(n))
    }

    @Test
    fun batteryPause_showsSurveysPausedWithBatteryLevel() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, pausedForBattery = true, batteryLevel = 14, sessionStartMs = sessionStart)
        )

        assertEquals("Paused", title(n))
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

        assertEquals("Paused", title(n))
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

        assertEquals("Recording", title(n))
        assertEquals("Community databases", text(n))
        assertEquals("Community databases\nScanning: Cellular, Wi-Fi", bigText(n))
    }

    @Test
    fun nsAnalyticsSurvey_listsNsAnalytics() {
        val n = builder.build(
            SurveyNotificationState(nsAnalytics = true, scanningCellular = true, phoneStateActive = true, sessionStartMs = sessionStart)
        )

        assertEquals("Recording", title(n))
        assertEquals("NS Analytics", text(n))
        assertEquals("NS Analytics\nScanning: Cellular, Phone state", bigText(n))
    }

    @Test
    fun grpcConnected_appearsAsDestination() {
        val n = builder.build(
            SurveyNotificationState(fileLogging = true, grpcState = ConnectionState.CONNECTED, scanningCellular = true, sessionStartMs = sessionStart)
        )

        assertEquals("Log files, gRPC stream", text(n))
    }

    @Test
    fun grpcConnecting_showsConnectingLine() {
        val n = builder.build(
            SurveyNotificationState(
                grpcState = ConnectionState.CONNECTING, sessionStartMs = sessionStart,
                destinationStatuses = mapOf(Destination.GRPC to DestinationStatus(StatusWord.RECONNECTING)),
            )
        )

        assertEquals("Recording", title(n))
        assertEquals("gRPC stream: Reconnecting", text(n))
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

        assertEquals("Log files, MQTT stream, gRPC stream, Community databases, NS Analytics", text(n))
        assertEquals(
            "Log files, MQTT stream, gRPC stream, Community databases, NS Analytics\nScanning: Cellular, Phone state, Wi-Fi, Bluetooth, GNSS, Call records",
            bigText(n)
        )
    }

    @Test
    fun disconnectingMqtt_doesNotCountAsActive() {
        val n = builder.build(SurveyNotificationState(mqttState = ConnectionState.DISCONNECTING))

        assertEquals("Ready", title(n))
        assertNull(text(n))
    }

    @Test
    fun notificationIsOngoingAndOnlyAlertsOnce() {
        val n = builder.build(SurveyNotificationState(fileLogging = true, sessionStartMs = sessionStart))

        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.CATEGORY_SERVICE, n.category)
    }

    @Test
    fun overrideOn_addsUsingYourOwnSettingsToTheExpandedText() {
        val n = builder.build(SurveyNotificationState(fileLogging = true, overrideOn = true))
        assertEquals("Log files\nUsing your own settings", bigText(n))
    }
}
