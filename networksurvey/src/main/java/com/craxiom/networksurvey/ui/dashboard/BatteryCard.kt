package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

private const val ANDROID_BATTERY_ICON = android.R.drawable.ic_lock_idle_low_battery

private val BatteryWarningBackground = Color(0xFFFFF8E1)
private val BatteryPausedBackground = Color(0xFFFFEBEE)
private val BatteryWarningText = Color(0xFFE65100)
private val BatteryPausedText = Color(0xFFC62828)
private val BatteryIconWarning = Color(0xFFFF6F00)
private val BatteryIconPaused = Color(0xFFD32F2F)

/**
 * Card that displays battery management status when logging is paused due to low battery
 * or when the battery is approaching the pause threshold.
 */
@Composable
fun BatteryCard(
    state: BatteryUiState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = DashboardCardDefaults.margin, vertical = 4.dp),
            shape = DashboardCardDefaults.shape,
            elevation = DashboardCardDefaults.elevation(),
        ) {
            val backgroundColor =
                if (state.isPaused) BatteryPausedBackground else BatteryWarningBackground
            val textColor = if (state.isPaused) BatteryPausedText else BatteryWarningText
            val iconColor = if (state.isPaused) BatteryIconPaused else BatteryIconWarning

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = ANDROID_BATTERY_ICON),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isPaused) {
                        stringResource(R.string.battery_paused_title)
                    } else {
                        stringResource(R.string.battery_warning_title)
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = ANDROID_BATTERY_ICON),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = iconColor,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (state.isPaused) {
                                stringResource(
                                    R.string.battery_status_paused_with_level,
                                    state.batteryLevel,
                                    state.batteryThreshold,
                                )
                            } else {
                                stringResource(
                                    R.string.battery_warning_message,
                                    state.batteryLevel,
                                )
                            },
                            color = textColor,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (state.isPaused) {
                                stringResource(
                                    R.string.battery_management_paused_description,
                                    state.batteryThreshold,
                                )
                            } else {
                                stringResource(
                                    R.string.battery_pause_active_description,
                                    state.batteryThreshold,
                                )
                            },
                            color = textColor,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
