package com.craxiom.networksurvey.ui.scout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.scout.model.NearbyScout
import com.craxiom.networksurvey.scout.model.NearbyScoutStatus
import com.craxiom.networksurvey.scout.model.ScoutDistance
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The Scouts the pairing scan hears, strongest first. Each row says what can be done with it
 * ("Ready to pair", "Paired with another phone. Hold its BOOT button...") and how close it is in
 * words, never in dBm; a row that can't pair now is dimmed and has no Pair button.
 */
@Composable
fun NearbyScoutList(nearby: List<NearbyScout>, onPair: (NearbyScout) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = NsTheme.shapes.panel, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(horizontal = NsTheme.spacing.l)) {
            nearby.forEachIndexed { index, scout ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NearbyScoutRow(scout) { onPair(scout) }
            }
        }
    }
}

@Composable
private fun NearbyScoutRow(scout: NearbyScout, onPair: () -> Unit) {
    val status = scout.status
    // The Scout paired here is already in use; it needs no Pair
    val pairable = status.canPair && status != NearbyScoutStatus.PAIRED_HERE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .padding(vertical = NsTheme.spacing.s)
            .alpha(if (status.canPair) 1f else UNAVAILABLE_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        ScoutBadge(R.drawable.ic_sensors, accent = pairable)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
            ScoutName(scout.name, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            // How close it is matters only for a Scout that can pair now; the others say what to do first
            val statusText = stringResource(statusRes(status))
            Text(
                if (pairable) stringResource(R.string.dot_join, statusText, stringResource(distanceRes(scout.distance))) else statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pairable) {
            FilledTonalButton(onClick = onPair, modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget)) {
                Text(stringResource(R.string.scout_pair_action))
            }
        }
    }
}

private fun statusRes(status: NearbyScoutStatus): Int = when (status) {
    NearbyScoutStatus.PAIRED_HERE -> R.string.scout_pair_status_paired_here
    NearbyScoutStatus.WAS_RESET -> R.string.scout_pair_status_was_reset
    NearbyScoutStatus.PAIRED_WITH_ANOTHER_PHONE -> R.string.scout_pair_status_other_phone
    NearbyScoutStatus.NEEDS_FIRMWARE_UPDATE -> R.string.scout_word_firmware
    NearbyScoutStatus.APP_UPDATE_NEEDED -> R.string.scout_pair_status_app_update
    NearbyScoutStatus.READY_TO_PAIR -> R.string.scout_pair_status_ready
}

private fun distanceRes(distance: ScoutDistance): Int = when (distance) {
    ScoutDistance.VERY_CLOSE -> R.string.scout_distance_very_close
    ScoutDistance.NEARBY -> R.string.scout_distance_nearby
    ScoutDistance.WEAK_SIGNAL -> R.string.scout_distance_weak
}

private const val UNAVAILABLE_ALPHA = 0.7f
