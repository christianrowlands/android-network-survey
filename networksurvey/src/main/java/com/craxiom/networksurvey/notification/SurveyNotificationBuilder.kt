package com.craxiom.networksurvey.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * Builds the single ongoing survey notification from a [SurveyNotificationState].
 *
 * Layout rules, in plain terms:
 *  - The title carries the state: Surveys Paused, No records for N min, Survey Active, or
 *    Survey Inactive, in that priority order.
 *  - The collapsed text is the most important health line if there is one (battery, network
 *    interrupted, MQTT dropping, connecting), otherwise the list of destinations records are
 *    going to (Log files, MQTT, gRPC, OpenCelliD & BeaconDB, NS Analytics).
 *  - The expanded text adds every applicable health line, the destinations, and the protocols
 *    being scanned.
 *  - Elapsed time is shown with the OS chronometer so it never needs a re-post.
 *
 * Nothing here composes sentences. Every line is either a string resource or a list joined with
 * [R.string.notification_list_separator], so translations do not have to fight word order.
 */
class SurveyNotificationBuilder(private val context: Context) {

    /**
     * Build the notification for the given state.
     */
    fun build(state: SurveyNotificationState): Notification {
        val title = title(state)
        val healthLines = healthLines(state)
        val destinations = destinationsLine(state)
        val collapsedText = healthLines.firstOrNull() ?: destinations

        val expandedLines = buildList {
            addAll(healthLines)
            if (destinations != null) add(destinations)
            protocolsLine(state)?.let { add(it) }
        }

        val builder = NotificationCompat.Builder(context, NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setTicker(title)
            .setSmallIcon(smallIcon(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(dashboardIntent())

        if (collapsedText != null) {
            builder.setContentText(collapsedText)
        }
        if (expandedLines.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedLines.joinToString("\n")))
        }

        val sessionStart = state.sessionStartMs
        if (sessionStart != null && state.anyOutputActive) {
            builder.setWhen(sessionStart).setShowWhen(true).setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    /**
     * The title tier for the state. Pauses win over a stall, which wins over the plain active title.
     */
    fun title(state: SurveyNotificationState): String {
        return when {
            state.paused -> context.getString(R.string.queue_paused_title)
            state.stalledMinutes != null -> context.resources.getQuantityString(
                R.plurals.notification_stall_title, state.stalledMinutes, state.stalledMinutes
            )
            state.anyOutputActive -> context.getString(R.string.scanning_active)
            else -> context.getString(R.string.scanning_inactive)
        }
    }

    /**
     * Every health line that applies, most important first. Empty when the survey is healthy.
     */
    fun healthLines(state: SurveyNotificationState): List<String> = buildList {
        if (state.pausedForBattery) {
            add(context.getString(R.string.notification_health_battery_low, state.batteryLevel))
        }
        if (state.pausedForBackpressure) {
            add(context.getString(R.string.notification_health_network_interrupted))
        }
        if (state.mqttDropping) {
            add(context.getString(R.string.mqtt_drop_mode_title))
            add(context.getString(R.string.mqtt_drop_mode_subtitle))
        }
        if (state.mqttState == ConnectionState.CONNECTING) {
            add(connectingLine(R.string.notification_dest_mqtt))
        }
        if (state.grpcState == ConnectionState.CONNECTING) {
            add(connectingLine(R.string.notification_dest_grpc))
        }
    }

    /**
     * The fixed-order list of places records are going, or null when nothing is running.
     */
    fun destinationsLine(state: SurveyNotificationState): String? {
        val names = buildList {
            if (state.fileLogging) add(context.getString(R.string.notification_dest_log_files))
            if (state.mqttActive) add(context.getString(R.string.notification_dest_mqtt))
            if (state.grpcActive) add(context.getString(R.string.notification_dest_grpc))
            if (state.communitySurvey) add(context.getString(R.string.notification_dest_community))
            if (state.nsAnalytics) add(context.getString(R.string.ns_analytics))
        }
        return joinOrNull(names)
    }

    /**
     * The protocols being scanned, in dashboard order, or null when nothing is being scanned.
     */
    fun protocolsLine(state: SurveyNotificationState): String? {
        val names = buildList {
            if (state.scanningCellular) add(context.getString(R.string.cellular_title))
            if (state.phoneStateActive) add(context.getString(R.string.phone_state_title))
            if (state.scanningWifi) add(context.getString(R.string.wifi_title))
            if (state.scanningBluetooth) add(context.getString(R.string.bluetooth_title))
            if (state.scanningGnss) add(context.getString(R.string.gnss_title))
            if (state.cdrActive) add(context.getString(R.string.cdr_title))
        }
        val joined = joinOrNull(names) ?: return null
        return context.getString(R.string.notification_protocols_line, joined)
    }

    @DrawableRes
    private fun smallIcon(state: SurveyNotificationState): Int {
        return if (state.paused || state.stalledMinutes != null) R.drawable.ic_pause else R.drawable.ic_notification_survey
    }

    private fun connectingLine(destinationRes: Int): String {
        return context.getString(
            R.string.notification_health_connecting,
            context.getString(destinationRes),
            context.getString(R.string.mqtt_connecting)
        )
    }

    private fun joinOrNull(names: List<String>): String? {
        if (names.isEmpty()) return null
        return names.joinToString(context.getString(R.string.notification_list_separator))
    }

    private fun dashboardIntent(): PendingIntent {
        val intent = Intent(context, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_DASHBOARD, true)
        }
        return PendingIntent.getActivity(
            context,
            NetworkSurveyConstants.LOGGING_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
