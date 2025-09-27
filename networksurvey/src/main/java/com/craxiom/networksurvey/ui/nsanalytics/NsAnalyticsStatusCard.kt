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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
 * @param onToggleSurvey Callback when the start/stop survey button is clicked
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
    onToggleSurvey: () -> Unit,
    showDetailedInfo: Boolean = true
) {
    val totalCount = cellularCount + wifiCount + bluetoothCount + gnssCount

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1F24)
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
                                if (isSurveyActive) Color(0xFF4285F4) else Color.Gray,
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSurveyActive) "Survey in Progress" else "Survey Inactive",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isSurveyActive && showDetailedInfo) {
                    Text(
                        text = getElapsedTime(surveyStartTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
                        text = if (isSurveyActive) "Records Collected" else "Pending Records",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    if (!isSurveyActive && totalCount > 0) {
                        Text(
                            text = "Ready to upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                Text(
                    text = "$totalCount total",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
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
                    gnssCount = gnssCount
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NsAnalyticsProtocolCountLabel("Cell", cellularCount, Color(0xFFA855F7))
                    NsAnalyticsProtocolCountLabel("Wi-Fi", wifiCount, Color(0xFF06B6D4))
                    NsAnalyticsProtocolCountLabel("BT", bluetoothCount, Color(0xFF3B82F6))
                    NsAnalyticsProtocolCountLabel("GPS", gnssCount, Color(0xFF22C55E))
                }
            } else if (!isSurveyActive) {
                // No records and survey inactive
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Start a survey to begin collecting data",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Survey Control Button
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleSurvey,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSurveyActive) Color(0xFFE53935) else Color(0xFF4CAF50)
                )
            ) {
                Icon(
                    imageVector = if (isSurveyActive) Icons.Default.Clear else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (isSurveyActive) "Stop Survey" else "Start Survey"
                )
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
    gnssCount: Int
) {
    val total = cellularCount + wifiCount + bluetoothCount + gnssCount
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
                    .background(Color(0xFFA855F7))
            )
        }
        if (wifiCount > 0) {
            Box(
                modifier = Modifier
                    .weight(wifiCount.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF06B6D4))
            )
        }
        if (bluetoothCount > 0) {
            Box(
                modifier = Modifier
                    .weight(bluetoothCount.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF3B82F6))
            )
        }
        if (gnssCount > 0) {
            Box(
                modifier = Modifier
                    .weight(gnssCount.toFloat())
                    .fillMaxHeight()
                    .background(Color(0xFF22C55E))
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
 * Utility function to get elapsed time string
 */
private fun getElapsedTime(startTime: Long): String {
    if (startTime == 0L) return ""
    val elapsed = System.currentTimeMillis() - startTime
    val minutes = (elapsed / 1000 / 60).toInt()
    val hours = minutes / 60

    return when {
        hours > 0 -> "Started ${hours}h ${minutes % 60}min ago"
        minutes > 0 -> "Started ${minutes}min ago"
        else -> "Just started"
    }
}