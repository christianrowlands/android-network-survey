package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

private val QueuePausedBackground = Color(0xFFFFF3E0)
private val QueuePausedText = Color(0xFFE65100)
private val QueuePausedIcon = Color(0xFFFF6D00)

/**
 * Card that displays queue backpressure or MQTT drop mode warnings.
 * Shows when MQTT streaming is backed up, either pausing all scanning
 * or dropping MQTT messages while file logging continues.
 */
@Composable
fun QueueStatusCard(
    state: QueueUiState,
    onAdjustLimit: () -> Unit,
    onDisableLimit: () -> Unit,
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QueuePausedBackground)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_cloud_off),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = QueuePausedIcon,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isDropping) {
                        stringResource(R.string.mqtt_drop_mode_title)
                    } else {
                        stringResource(R.string.queue_paused_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = QueuePausedText,
                )
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QueuePausedBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cloud_off),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = QueuePausedIcon,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (state.isDropping) {
                                stringResource(R.string.mqtt_drop_mode_subtitle)
                            } else {
                                stringResource(R.string.queue_paused_subtitle)
                            },
                            color = QueuePausedText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when {
                                state.isDropping && state.isUnderMdmControl ->
                                    stringResource(R.string.mqtt_drop_mode_mdm_description)

                                state.isDropping ->
                                    stringResource(R.string.mqtt_drop_mode_description)

                                state.isUnderMdmControl ->
                                    stringResource(R.string.queue_paused_mdm_description)

                                else ->
                                    stringResource(R.string.queue_paused_description)
                            },
                            color = QueuePausedText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onAdjustLimit) {
                        Text(
                            text = if (state.isUnderMdmControl) {
                                stringResource(R.string.queue_paused_view_settings)
                            } else {
                                stringResource(R.string.queue_paused_adjust_limit)
                            },
                        )
                    }

                    if (!state.isUnderMdmControl) {
                        TextButton(onClick = onDisableLimit) {
                            Text(
                                text = stringResource(R.string.queue_paused_disable_limit),
                            )
                        }
                    }
                }
            }
        }
    }
}
