package com.craxiom.networksurvey.ui.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.util.copyTextToClipboard
import com.craxiom.networksurvey.util.LocationStatusHelper.LocationState
import com.craxiom.networksurvey.util.MeasurementFormatter
import kotlinx.coroutines.delay
import java.text.DecimalFormat

private val LocationGreen = Color(0xFF388E3C)
private val LocationYellow = Color(0xFFFBC02D)
private val LocationRed = Color(0xFFD32F2F)

private val locationFormat = DecimalFormat("###.#####")

/**
 * Compact location status display with expand/collapse for details.
 * This is NOT a Card - it uses a simple Column with clickable modifier,
 * matching the original minimal layout design.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocationCard(
    state: LocationUiState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    // Location hint timer - show after 15 seconds of SEARCHING with no fix
    LaunchedEffect(state.state) {
        showHint = false
        if (state.state == LocationState.SEARCHING) {
            delay(15_000L)
            showHint = true
        }
    }

    val context = LocalContext.current

    val iconColor = when (state.state) {
        LocationState.FIX -> LocationGreen
        LocationState.SEARCHING -> LocationYellow
        else -> LocationRed
    }

    val statusText = when (state.state) {
        LocationState.FIX -> stringResource(R.string.location_status_fixed)
        LocationState.SEARCHING -> stringResource(R.string.location_status_searching)
        LocationState.GPS_DISABLED -> stringResource(R.string.location_status_gps_disabled)
        LocationState.NO_GPS -> stringResource(R.string.location_status_no_gps)
        LocationState.MISSING_PERMISSION -> stringResource(R.string.location_status_missing_permission)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (state.hasLocation) {
                        expanded = !expanded
                    }
                },
                onLongClick = {
                    if (state.hasLocation) {
                        copyTextToClipboard(
                            context,
                            context.getString(R.string.location_clip_label),
                            getDetailsText(context, state),
                            context.getString(R.string.location_copied),
                        )
                    }
                },
            )
            .padding(horizontal = DashboardCardDefaults.margin, vertical = 8.dp),
    ) {
        // Compact row: icon + status + accuracy
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_my_location),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconColor,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = DashboardCardDefaults.TextPrimary,
                modifier = Modifier.weight(1f),
            )

            if (state.state == LocationState.FIX) {
                Text(
                    text = stringResource(
                        R.string.location_status_accuracy,
                        MeasurementFormatter.formatAccuracy(context, state.accuracy),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardCardDefaults.TextSecondary,
                )
            }
        }

        // Expandable details row
        AnimatedVisibility(
            visible = expanded && state.hasLocation,
            enter = expandVertically(tween(200)),
            exit = shrinkVertically(tween(200)),
        ) {
            Text(
                text = getDetailsText(context, state),
                style = MaterialTheme.typography.bodySmall,
                color = DashboardCardDefaults.TextSecondary,
                modifier = Modifier.padding(start = 32.dp, top = 4.dp),
            )
        }

        // Location hint
        AnimatedVisibility(
            visible = showHint && state.state == LocationState.SEARCHING,
            enter = expandVertically() + androidx.compose.animation.fadeIn(),
        ) {
            Text(
                text = AnnotatedString.fromHtml(
                    stringResource(R.string.location_hint_open_settings)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 32.dp, top = 4.dp)
                    .combinedClickable(onClick = onNavigateToSettings),
            )
        }
    }
}

private fun getDetailsText(context: Context, state: LocationUiState): String {
    val latLon =
        "${locationFormat.format(state.latitude)}, ${locationFormat.format(state.longitude)}"
    val altitude = MeasurementFormatter.formatAltitude(context, state.altitude)
    return context.getString(R.string.location_details_format, latLon, altitude)
}
