package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.SurveyTypes

private val DividerColor = DashboardCardDefaults.CardHeaderBlue

/**
 * Card for managing community upload scanning, displaying upload progress,
 * results, and queue counts. Hidden when external data upload is not allowed by MDM.
 */
@Composable
fun UploadScanningCard(
    state: UploadUiState,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onUpload: () -> Unit,
    onCancelUpload: () -> Unit,
    onNavigateToUploadSettings: () -> Unit,
    onHelpClick: () -> Unit,
    shouldStartCellular: Boolean,
    shouldStartWifi: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 4.dp),
        shape = DashboardCardDefaults.shape,
        elevation = DashboardCardDefaults.elevation(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        DashboardCardHeader(
            icon = R.drawable.ic_upload_24,
            title = stringResource(R.string.card_title_upload),
            onHelpClick = onHelpClick,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // Description
            Text(
                text = stringResource(R.string.upload_scanning_description_text),
                style = MaterialTheme.typography.bodySmall,
                color = DashboardCardDefaults.TextSecondary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Start/Stop button + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = state.scanningActive,
                        label = "scanningButton",
                    ) { isActive ->
                        if (isActive) {
                            Button(onClick = onStopScanning) {
                                Text(text = stringResource(R.string.stop_scanning))
                            }
                        } else {
                            Button(onClick = onStartScanning) {
                                Text(text = stringResource(R.string.start_scanning))
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (state.scanningActive) {
                                stringResource(R.string.scanning_active)
                            } else {
                                stringResource(R.string.scanning_inactive)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.scanningActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                DashboardCardDefaults.TextPrimary
                            },
                        )

                        if (state.scanningActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }

                    // Active survey type indicators
                    if (state.scanningActive && state.activeSurveys.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (SurveyTypes.CELLULAR in state.activeSurveys) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_cellular),
                                    contentDescription = stringResource(R.string.cellular_title),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (SurveyTypes.WIFI in state.activeSurveys) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_wifi),
                                    contentDescription = stringResource(R.string.wifi_title),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // Upload settings button
                IconButton(onClick = onNavigateToUploadSettings) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_upload_settings),
                        contentDescription = null,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = DividerColor,
            )

            // Upload progress
            when (val progress = state.uploadProgress) {
                is UploadProgressState.InProgress -> {
                    UploadProgressSection(
                        progress = progress,
                        onCancel = onCancelUpload,
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = DividerColor,
                    )
                }

                is UploadProgressState.Finished -> {
                    UploadResultsSection(result = progress)

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = DividerColor,
                    )
                }

                is UploadProgressState.Hidden -> { /* no-op */
                }
            }

            // Queue counts section
            UploadQueueSection(
                cellularCount = state.cellularQueueCount,
                wifiCount = state.wifiQueueCount,
                autoUploadEnabled = state.autoUploadEnabled,
                uploadButtonEnabled = state.uploadButtonEnabled,
                shouldStartCellular = shouldStartCellular,
                shouldStartWifi = shouldStartWifi,
                onUpload = onUpload,
            )
        }
    }
}

/**
 * Upload progress indicator with cancel button.
 */
@Composable
private fun UploadProgressSection(
    progress: UploadProgressState.InProgress,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.upload_progress),
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearProgressIndicator(
                progress = { progress.progress.toFloat() / progress.maxProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.upload_percentage, progress.progress),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (progress.statusMessage.isNotEmpty()) {
            Text(
                text = progress.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = DashboardCardDefaults.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_cancel),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = stringResource(R.string.cancel))
        }
    }
}

/**
 * Upload results display showing OpenCelliD and BeaconDB status.
 */
@Composable
private fun UploadResultsSection(result: UploadProgressState.Finished) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.upload_result),
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (result.cancelled) {
            Text(
                text = stringResource(R.string.uploader_canceled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            // OpenCelliD result
            Row {
                Text(
                    text = stringResource(R.string.opencellid_upload),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.ocidResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (result.ocidResultMessage.isNotEmpty()) {
                Text(
                    text = result.ocidResultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // BeaconDB result
            Row {
                Text(
                    text = stringResource(R.string.beacondb_upload),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.beaconDbResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (result.beaconDbResultMessage.isNotEmpty()) {
                Text(
                    text = result.beaconDbResultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                )
            }
        }
    }
}

/**
 * Queue counts section showing cellular and WiFi record counts with upload button.
 */
@Composable
private fun UploadQueueSection(
    cellularCount: Int,
    wifiCount: Int,
    autoUploadEnabled: Boolean,
    uploadButtonEnabled: Boolean,
    shouldStartCellular: Boolean,
    shouldStartWifi: Boolean,
    onUpload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.upload_queue_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (autoUploadEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auto_upload_status_enabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                // Cellular count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_cellular),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = DashboardCardDefaults.TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            cellularCount == 0 && !shouldStartCellular ->
                                stringResource(R.string.cellular_upload_queue_count_disabled)

                            cellularCount >= 0 ->
                                stringResource(R.string.cellular_upload_queue_count, cellularCount)

                            else -> stringResource(R.string.cellular_upload_queue_count_initial)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // WiFi count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wifi),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = DashboardCardDefaults.TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            wifiCount == 0 && !shouldStartWifi ->
                                stringResource(R.string.wifi_upload_queue_count_disabled)

                            wifiCount >= 0 ->
                                stringResource(R.string.wifi_upload_queue_count, wifiCount)

                            else -> stringResource(R.string.wifi_upload_queue_count_initial)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Upload button
            Button(
                onClick = onUpload,
                enabled = uploadButtonEnabled,
            ) {
                Text(
                    text = stringResource(R.string.upload_survey_records),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
