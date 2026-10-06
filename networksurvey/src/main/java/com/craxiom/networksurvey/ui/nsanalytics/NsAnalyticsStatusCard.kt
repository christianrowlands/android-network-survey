package com.craxiom.networksurvey.ui.nsanalytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsSeeOnMapButton
import com.craxiom.networksurvey.ui.dashboard.DashboardCardDefaults
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * A reusable NS Analytics status card that can be used in both the main NS Analytics screen
 * and the dashboard. This card shows the current survey status, record counts, and control buttons.
 *
 * @param isSurveyActive Whether a survey is currently active
 * @param surveyStartTime The timestamp when the survey started (0 if not active)
 * @param cellularCount Number of cellular records collected
 * @param wifiCount Number of WiFi records collected
 * @param bluetoothCount Number of Bluetooth records collected
 * @param gnssCount Number of GNSS records collected
 * @param phoneStateCount Number of phone state records collected
 * @param onToggleSurvey Callback when the start/stop survey button is clicked
 * @param onOpenDetails Callback when the open details button is clicked (optional, only shown in dashboard)
 * @param modifier Optional modifier for the card
 * @param showDetailedInfo Whether to show detailed information (for full screen view)
 */
@Composable
fun NsAnalyticsStatusCard(
    modifier: Modifier = Modifier,
    isSurveyActive: Boolean,
    surveyStartTime: Long = 0L,
    cellularCount: Int = 0,
    wifiCount: Int = 0,
    bluetoothCount: Int = 0,
    gnssCount: Int = 0,
    phoneStateCount: Int = 0,
    onToggleSurvey: () -> Unit,
    onOpenDetails: (() -> Unit)? = null,
    onSeeOnMap: (() -> Unit)? = null,
    showDetailedInfo: Boolean = true
) {
    val totalCount = cellularCount + wifiCount + bluetoothCount + gnssCount + phoneStateCount

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Survey Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (isSurveyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (isSurveyActive) R.string.scanning_active else R.string.scanning_inactive),
                        style = MaterialTheme.typography.titleMedium,
                        color = DashboardCardDefaults.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isSurveyActive && showDetailedInfo) {
                    Text(
                        text = elapsedTimeText(surveyStartTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Records Section - Always visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(
                            if (isSurveyActive) R.string.ns_analytics_records_collected
                            else R.string.ns_analytics_records_pending
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (!isSurveyActive && totalCount > 0) {
                        Text(
                            text = stringResource(R.string.ns_analytics_upload_status_ready),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Text(
                    text = pluralStringResource(R.plurals.ns_analytics_records_total, totalCount, totalCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = DashboardCardDefaults.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Protocol distribution - Always show if records exist
            if (totalCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))

                NsAnalyticsProtocolDistributionBar(
                    cellularCount = cellularCount,
                    wifiCount = wifiCount,
                    bluetoothCount = bluetoothCount,
                    gnssCount = gnssCount,
                    phoneStateCount = phoneStateCount
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val protocol = NsTheme.colors.protocol
                    NsAnalyticsProtocolCountLabel(stringResource(R.string.cellular_title), cellularCount, protocol.cellular)
                    NsAnalyticsProtocolCountLabel(stringResource(R.string.wifi_title), wifiCount, protocol.wifi)
                    NsAnalyticsProtocolCountLabel(stringResource(R.string.bluetooth_title), bluetoothCount, protocol.bluetooth)
                    NsAnalyticsProtocolCountLabel(stringResource(R.string.gnss_title), gnssCount, protocol.gnss)
                    NsAnalyticsProtocolCountLabel(stringResource(R.string.phone_state_title), phoneStateCount, protocol.phoneState)
                }
            } else if (!isSurveyActive) {
                // No records and survey inactive
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ns_analytics_start_survey_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Survey Control Buttons
            Spacer(modifier = Modifier.height(16.dp))

            if (onOpenDetails != null) {
                // Dashboard view - show buttons side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleSurvey,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSurveyActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isSurveyActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isSurveyActive) Icons.Default.Clear else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = stringResource(if (isSurveyActive) R.string.survey_stop_short else R.string.survey_start_short)
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenDetails,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_details),
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp)
                        )
                        Text(stringResource(R.string.details_button))
                    }
                }

                if (isSurveyActive && onSeeOnMap != null) {
                    NsSeeOnMapButton(onClick = onSeeOnMap, modifier = Modifier.fillMaxWidth())
                }
            } else {
                // NS Analytics Connection Screen - show full width button
                Button(
                    onClick = onToggleSurvey,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSurveyActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isSurveyActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isSurveyActive) Icons.Default.Clear else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(if (isSurveyActive) R.string.stop_scanning else R.string.start_scanning)
                    )
                }
            }
        }
    }
}

/**
 * Protocol distribution bar showing the proportion of each protocol type
 */
@Composable
fun NsAnalyticsProtocolDistributionBar(
    cellularCount: Int,
    wifiCount: Int,
    bluetoothCount: Int,
    gnssCount: Int,
    phoneStateCount: Int = 0
) {
    val total = cellularCount + wifiCount + bluetoothCount + gnssCount + phoneStateCount
    if (total == 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        if (cellularCount > 0) {
            Box(
                modifier = Modifier
                    .weight(cellularCount.toFloat())
                    .fillMaxHeight()
                    .background(NsTheme.colors.protocol.cellular)
            )
        }
        if (wifiCount > 0) {
            Box(
                modifier = Modifier
                    .weight(wifiCount.toFloat())
                    .fillMaxHeight()
                    .background(NsTheme.colors.protocol.wifi)
            )
        }
        if (bluetoothCount > 0) {
            Box(
                modifier = Modifier
                    .weight(bluetoothCount.toFloat())
                    .fillMaxHeight()
                    .background(NsTheme.colors.protocol.bluetooth)
            )
        }
        if (gnssCount > 0) {
            Box(
                modifier = Modifier
                    .weight(gnssCount.toFloat())
                    .fillMaxHeight()
                    .background(NsTheme.colors.protocol.gnss)
            )
        }
        if (phoneStateCount > 0) {
            Box(
                modifier = Modifier
                    .weight(phoneStateCount.toFloat())
                    .fillMaxHeight()
                    .background(NsTheme.colors.protocol.phoneState)
            )
        }
    }
}

/**
 * Protocol count label showing the protocol name and count
 */
@Composable
fun NsAnalyticsProtocolCountLabel(
    label: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * How long ago the survey started, e.g. "Started 1 h 5 min ago". Empty when there is no start time.
 */
@Composable
private fun elapsedTimeText(startTime: Long): String {
    if (startTime == 0L) return ""
    val elapsed = System.currentTimeMillis() - startTime
    val minutes = (elapsed / 1000 / 60).toInt()
    val hours = minutes / 60

    return when {
        hours > 0 -> stringResource(R.string.ns_analytics_started_hours_ago, hours, minutes % 60)
        minutes > 0 -> stringResource(R.string.ns_analytics_started_minutes_ago, minutes)
        else -> stringResource(R.string.ns_analytics_just_started)
    }
}