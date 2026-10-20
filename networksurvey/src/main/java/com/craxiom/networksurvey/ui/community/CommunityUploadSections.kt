package com.craxiom.networksurvey.ui.community

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
import androidx.compose.material3.Icon
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
import com.craxiom.networksurvey.ui.common.NsSeeOnMapButton
import com.craxiom.networksurvey.ui.common.percentText

// The upload progress, result and queue sections, moved from the old Dashboard's upload card.

/**
 * Upload progress indicator with cancel button.
 */
@Composable
fun UploadProgressSection(
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
                text = percentText(progress.progress),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (progress.statusMessage.isNotEmpty()) {
            Text(
                text = progress.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun UploadResultsSection(result: UploadProgressState.Finished, onSeeOnMap: (() -> Unit)?) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                )
            }

            if (onSeeOnMap != null) {
                NsSeeOnMapButton(onClick = onSeeOnMap)
            }
        }
    }
}
