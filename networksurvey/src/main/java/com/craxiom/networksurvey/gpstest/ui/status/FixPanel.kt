package com.craxiom.networksurvey.gpstest.ui.status

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.ui.sky.PanelTitle
import com.craxiom.networksurvey.gpstest.util.DateTimeUtils
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.StatusLabel
import com.craxiom.networksurvey.ui.common.annotateUrls
import com.craxiom.networksurvey.ui.common.copyOnLongPress
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.MeasurementFormatter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.text.DateFormat as JavaDateFormat

/** Each list column needs this much (times the font scale) before the list splits in two. */
private val LIST_COLUMN_MIN_WIDTH = 160.dp

/** A list row's minimum height: dense, like the old card, with long press still easy to hit. */
private val LIST_ROW_MIN_HEIGHT = 32.dp

/** How often "Last fix ... ago" counts up while the fix is lost. */
private const val AGE_TICK_MS = 1_000L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** The date time skeletons of the fix time: hours, minutes and seconds, then with the date. */
private const val TIME_24_HOUR = "Hms"
private const val TIME_12_HOUR = "hmsa"
private const val DATE_TIME_24_HOUR = "yMMMdHmsz"
private const val DATE_TIME_12_HOUR = "yMMMdhmsaz"

/** One line of the list; [value] is null when the phone did not report it. */
private data class FixValue(@StringRes val label: Int, val value: String?)

/**
 * The location card: the fix state and horizontal accuracy in the header, the coordinates large
 * with a copy button, then every other reading as a label and value list: position quality on the
 * left, time and motion on the right, one column at large text. After the fix is lost the
 * coordinates dim and say when the last fix was. Long press copies a value (a coordinate copies the
 * whole location); a fix time days away from the phone's clock spans the card in the error color
 * and explains itself on a tap.
 *
 * @param onCopyLocation Copies the location, or null when there is none yet.
 * @param onExplainTime Explains a fix time days away from the phone's clock ([TimeWrongDialog]).
 */
@Composable
fun FixPanel(
    readings: FixReadings,
    onCopyLocation: (() -> Unit)?,
    onCopyValue: (label: String, value: String) -> Unit,
    onExplainTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = rememberFixFormatter()
    val quality = listOf(
        FixValue(R.string.gps_altitude_label, readings.altitudeMeters?.let(format::altitude)),
        FixValue(R.string.gps_altitude_msl_label, readings.altitudeMslMeters?.let(format::altitude)),
        FixValue(R.string.gnss_vertical_accuracy_label, readings.verticalAccuracyMeters?.let(format::accuracy)),
        FixValue(R.string.gps_num_sats_label, stringResource(R.string.gps_num_sats_value, readings.satellitesUsed, readings.satellitesInView)),
        FixValue(R.string.pdop_label, readings.pdop?.let { stringResource(R.string.pdop_value, it) }),
        FixValue(R.string.hvdop_label, readings.hdop?.let { stringResource(R.string.hvdop_value, it, readings.vdop ?: it) }),
    )
    val time = FixValue(R.string.gps_fix_time_label, readings.timeMs?.let { format.time(it, withDate = !readings.timeValid) })
    val motion = listOfNotNull(
        time.takeIf { readings.timeValid },
        FixValue(R.string.gps_ttff_label, readings.ttff),
        FixValue(R.string.gps_speed_label, readings.speedMetersPerSecond?.let(format::speed)),
        FixValue(R.string.gps_speed_acc_label, readings.speedAccuracyMetersPerSecond?.let(format::speedAccuracy)),
        FixValue(R.string.gps_bearing_label, readings.bearingDegrees?.let { stringResource(R.string.gps_bearing_value, it) }),
        FixValue(R.string.gps_bearing_acc_label, readings.bearingAccuracyDegrees?.let { stringResource(R.string.gps_bearing_acc_value, it) }),
    )

    NsPanel(modifier) {
        FixHeader(readings, readings.accuracyMeters?.takeIf { readings.fix }?.let(format::accuracy))
        Coordinates(readings, format, onCopyLocation)
        HorizontalDivider(Modifier.padding(vertical = NsTheme.spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
        if (!readings.timeValid) TimeWrongRow(time, onCopyValue, onExplainTime)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = NsTheme.spacing.l
            val twoColumns = maxWidth >= LIST_COLUMN_MIN_WIDTH * LocalDensity.current.fontScale * 2 + gap
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    FixList(quality, onCopyValue, Modifier.weight(1f))
                    FixList(motion, onCopyValue, Modifier.weight(1f))
                }
            } else {
                FixList(quality + motion, onCopyValue)
            }
        }
    }
}

/** "Location", Fix or No fix, and the horizontal accuracy while there is a fix. One TalkBack item. */
@Composable
private fun FixHeader(readings: FixReadings, accuracy: String?) {
    val title = stringResource(R.string.gnss_status_location_title)
    val state = stringResource(if (readings.fix) R.string.gnss_fix else R.string.gnss_no_fix)
    val spokenAccuracy = accuracy?.let { stringResource(R.string.gnss_accuracy_spoken, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                heading()
                contentDescription = listOfNotNull(title, state, spokenAccuracy).joinToString(", ")
            },
    ) {
        // The header speaks as one item; its parts say nothing on their own.
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            PanelTitle(title, Modifier.weight(1f))
            StatusLabel(
                text = state,
                color = if (readings.fix) NsTheme.colors.status.success.content else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (accuracy != null) {
                Text(
                    text = stringResource(R.string.gnss_accuracy_short, accuracy),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The coordinates, large, with a copy button; dimmed with "Last fix ..., ... ago" once the fix is
 * lost, and "--" before the first fix, in the same place so nothing jumps when the fix arrives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Coordinates(readings: FixReadings, format: FixFormatter, onCopyLocation: (() -> Unit)?) {
    val color = if (readings.fix) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val copyLabel = stringResource(R.string.gnss_status_copy_location)
    Row(Modifier.fillMaxWidth().padding(top = NsTheme.spacing.s), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            Coordinate(R.string.gps_latitude_label, readings.latitude, color, copyLabel, onCopyLocation)
            Coordinate(R.string.gps_longitude_label, readings.longitude, color, copyLabel, onCopyLocation)
        }
        IconButton(onClick = { onCopyLocation?.invoke() }, enabled = onCopyLocation != null) {
            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = copyLabel)
        }
    }
    val lastFix = readings.timeMs.takeIf { !readings.fix && readings.hasPosition && readings.timeValid }
    if (lastFix != null) LastFixLine(lastFix, format)
}

@Composable
private fun Coordinate(@StringRes label: Int, degrees: Double?, color: Color, copyLabel: String, onCopy: (() -> Unit)?) {
    val name = stringResource(label)
    val text = degrees?.let { stringResource(R.string.lat_or_lon, it) } ?: stringResource(R.string.signal_none)
    val gestures = if (onCopy != null && degrees != null) Modifier.copyOnLongPress(copyLabel, text, onCopy) else Modifier
    Column(Modifier.semantics { contentDescription = "$name, $text" }.then(gestures)) {
        Column(Modifier.clearAndSetSemantics {}) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = color)
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Last fix 12:29:26, 2 min ago", counting up each second. */
@Composable
private fun LastFixLine(timeMs: Long, format: FixFormatter) {
    val ageMs by produceState(System.currentTimeMillis() - timeMs, timeMs) {
        while (true) {
            value = System.currentTimeMillis() - timeMs
            delay(AGE_TICK_MS)
        }
    }
    val seconds = (ageMs / MS_PER_SECOND).coerceAtLeast(0)
    val age = if (seconds < SECONDS_PER_MINUTE) {
        stringResource(R.string.settings_seconds, seconds.toInt())
    } else {
        val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
        pluralStringResource(R.plurals.survey_minutes_short, minutes, minutes)
    }
    Text(
        text = stringResource(R.string.gnss_last_fix, format.time(timeMs, withDate = false), age),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = NsTheme.spacing.xs),
    )
}

/** A column of label and value rows. */
@Composable
private fun FixList(values: List<FixValue>, onCopyValue: (String, String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        values.forEach { FixRow(it, onCopyValue) }
    }
}

/**
 * One reading: its label at the start, its value at the end. Long press copies it; [onExplain],
 * for a fix time days away from the phone's clock, makes a tap explain it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FixRow(
    fix: FixValue,
    onCopyValue: (String, String) -> Unit,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onExplain: (() -> Unit)? = null,
) {
    val label = stringResource(fix.label)
    val text = fix.value ?: stringResource(R.string.signal_none)
    val copyLabel = stringResource(R.string.cellular_copy_action)
    val copy: (() -> Unit)? = if (fix.value != null) ({ onCopyValue(label, text) }) else null
    val gestures = when {
        onExplain != null -> Modifier.combinedClickable(
            onClickLabel = stringResource(R.string.gnss_status_time_wrong_action),
            onLongClickLabel = copyLabel,
            onLongClick = copy,
            onClick = onExplain,
        )

        copy != null -> Modifier.copyOnLongPress(copyLabel, text, copy)
        else -> Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LIST_ROW_MIN_HEIGHT)
            .semantics { contentDescription = "$label, $text" }
            .then(gestures),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row speaks as one item; its label and value say nothing on their own.
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fix.value != null) valueColor else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A fix time days away from the phone's clock: the whole width, the error color, a tap explains. */
@Composable
private fun TimeWrongRow(time: FixValue, onCopyValue: (String, String) -> Unit, onExplainTime: () -> Unit) {
    FixRow(fix = time, onCopyValue = onCopyValue, valueColor = MaterialTheme.colorScheme.error, onExplain = onExplainTime)
}

/** Explains a fix time days away from the phone's clock, as GPSTest did; its link opens the details. */
@Composable
fun TimeWrongDialog(timeMs: Long?, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val fixTime = remember(timeMs, locale) {
        timeMs?.let { JavaDateFormat.getDateTimeInstance(JavaDateFormat.LONG, JavaDateFormat.LONG, locale).format(it) }.orEmpty()
    }
    val message = pluralStringResource(
        R.plurals.gps_error_time_message,
        DateTimeUtils.NUM_DAYS_TIME_VALID,
        fixTime,
        DateTimeUtils.NUM_DAYS_TIME_VALID,
    )
    NsMessageDialog(
        title = stringResource(R.string.gps_error_time_title),
        message = annotateUrls(message, MaterialTheme.colorScheme.primary),
        onDismiss = onDismiss,
    )
}

/** Formats the panel's readings in the phone's locale, units setting and 12 or 24 hour clock. */
private class FixFormatter(private val context: Context, private val locale: Locale, is24Hour: Boolean) {
    private val timeFormat = pattern(if (is24Hour) TIME_24_HOUR else TIME_12_HOUR)
    private val dateTimeFormat = pattern(if (is24Hour) DATE_TIME_24_HOUR else DATE_TIME_12_HOUR)

    private fun pattern(skeleton: String) = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    fun time(timeMs: Long, withDate: Boolean): String = (if (withDate) dateTimeFormat else timeFormat).format(timeMs)
    fun altitude(meters: Double): String = MeasurementFormatter.formatAltitude(context, meters)
    fun speed(metersPerSecond: Float): String = MeasurementFormatter.formatSpeed(context, metersPerSecond)
    fun speedAccuracy(metersPerSecond: Float): String = MeasurementFormatter.formatSpeedAccuracy(context, metersPerSecond)
    fun accuracy(meters: Float): String = MeasurementFormatter.formatAccuracy(context, meters)
}

/** The formatter, made again when the language, the 24 hour setting or the time zone changes. */
@Composable
private fun rememberFixFormatter(): FixFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeZone = TimeZone.getDefault()
    return remember(context, locale, is24Hour, timeZone) { FixFormatter(context, locale, is24Hour) }
}
