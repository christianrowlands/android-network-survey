package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import java.util.Date
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.LiveDestinationUi
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.StatusAction
import com.craxiom.networksurvey.survey.status.StatusLevel
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.survey.DestinationStatusText
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.ROW_ICON
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.common.StatusLabel

/**
 * "Where it's going" while Recording: each destination in the survey, what is happening to its
 * records (a word in its level's color and a detail line), an action when there is one, and a way
 * to its page.
 */
@Composable
fun LiveDestinationRows(
    destinations: List<LiveDestinationUi>,
    info: DestinationInfo,
    nowMs: Long,
    onOpen: (Destination) -> Unit,
    onUploadNow: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.survey_where_its_going))
        NsPanel {
            destinations.forEachIndexed { index, live ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LiveRow(live, info, nowMs, onOpen, onUploadNow)
            }
        }
    }
}

@Composable
private fun LiveRow(
    live: LiveDestinationUi,
    info: DestinationInfo,
    nowMs: Long,
    onOpen: (Destination) -> Unit,
    onUploadNow: (Destination) -> Unit,
) {
    val (statusText, statusColor) = destinationStatusLabel(live.status)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.survey_dest_open)) { onOpen(live.destination) }
            .padding(vertical = NsTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        Icon(
            painterResource(live.destination.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ROW_ICON),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(live.destination.labelRes), style = MaterialTheme.typography.bodyLarge)
            val detail = destinationStatusDetail(live.destination, live.status, info, nowMs)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            live.savedThisSurvey?.let {
                Text(
                    countText(R.plurals.survey_saved_for_upload_count, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusActionButton(live.status.action, live.destination, onUploadNow, onOpen)
        }
        StatusLabel(statusText, statusColor)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(SMALL_ICON),
        )
    }
}

/** The action a status offers under its detail line: Upload now, or Open for a problem its page explains. */
@Composable
fun StatusActionButton(action: StatusAction?, destination: Destination, onUploadNow: (Destination) -> Unit, onOpen: (Destination) -> Unit) {
    when (action) {
        StatusAction.UPLOAD_NOW -> NsTextAction(stringResource(R.string.survey_upload_now), onClick = { onUploadNow(destination) })
        StatusAction.OPEN -> NsTextAction(stringResource(R.string.survey_action_open), onClick = { onOpen(destination) })
        null -> Unit
    }
}

/** The status word and its level's color, shared with the destination pages. */
@Composable
fun destinationStatusLabel(status: DestinationStatus): Pair<String, Color> =
    DestinationStatusText.word(LocalResources.current, status.word) to statusLevelColor(status.level)

/** The color for a status level: green arriving, slate on its way, amber held, red not arriving. */
@Composable
fun statusLevelColor(level: StatusLevel): Color {
    val colors = NsTheme.colors.status
    return when (level) {
        StatusLevel.ARRIVING -> colors.success.content
        StatusLevel.ON_ITS_WAY -> MaterialTheme.colorScheme.primary
        StatusLevel.HELD -> colors.warningText
        StatusLevel.NOT_ARRIVING -> colors.danger.content
    }
}

/**
 * The detail line for a status: the status's own detail, or the destination's description when
 * there is nothing to add.
 */
@Composable
fun destinationStatusDetail(destination: Destination, status: DestinationStatus, info: DestinationInfo, nowMs: Long): String =
    DestinationStatusText.detail(
        resources = LocalResources.current,
        detail = status.detail,
        address = streamAddress(destination, info),
        formatTime = rememberTimeFormatter(),
        nowMs = nowMs,
    ) ?: defaultDetail(destination, info)

/** Formats a time of day ("09:12") the way the phone is set to show it. */
@Composable
fun rememberTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    return remember(context) {
        val timeFormat = DateFormat.getTimeFormat(context)
        val format: (Long) -> String = { timeFormat.format(Date(it)) }
        format
    }
}

/** The MQTT broker or gRPC server a stream status names. */
@Composable
fun streamAddress(destination: Destination, info: DestinationInfo): String = when (destination) {
    Destination.MQTT -> if (info.mqttFromOrganization) {
        stringResource(R.string.survey_status_org_broker)
    } else {
        info.mqttBrokerLabel ?: stringResource(R.string.survey_status_the_broker)
    }

    Destination.GRPC -> info.grpcAddress ?: stringResource(R.string.survey_status_the_server)
    else -> ""
}
