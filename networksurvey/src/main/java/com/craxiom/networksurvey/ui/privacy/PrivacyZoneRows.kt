package com.craxiom.networksurvey.ui.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.common.NsHeroStatusCard
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.util.MeasurementFormatter

/*
 * The Privacy zones page's status card and zone rows, the delete confirmation the page and the
 * editor share, and the "Privacy zones" row that Settings and the Community databases page share.
 */

/**
 * The page's status card: turned off by the organization, inside a zone right now, or how many
 * zones there are (with the error styling once the list is full).
 */
@Composable
internal fun PrivacyZonesHero(state: PrivacyZonesUiState) {
    val inside = state.insideZone
    when {
        !state.allowed -> NsHeroStatusCard(
            title = stringResource(R.string.privacy_zones_hero_off),
            subtitle = stringResource(R.string.privacy_zones_hero_off_body),
            icon = painterResource(R.drawable.ic_domain),
        )

        inside != null -> NsHeroStatusCard(
            title = if (inside.name.isBlank()) {
                stringResource(R.string.privacy_zones_hero_inside_unnamed)
            } else {
                stringResource(R.string.privacy_zones_hero_inside, inside.name)
            },
            subtitle = stringResource(R.string.privacy_zones_hero_inside_body),
            icon = painterResource(R.drawable.ic_shield),
        )

        else -> {
            val count = state.zones.size
            NsHeroStatusCard(
                title = if (state.isFull) {
                    stringResource(R.string.privacy_zones_hero_full)
                } else {
                    pluralStringResource(R.plurals.privacy_zones_hero_count, count, count)
                },
                subtitle = pluralStringResource(R.plurals.count_of_max_format, count, count, PrivacyZone.MAX_ZONES),
                icon = painterResource(R.drawable.ic_shield),
                isError = state.isFull,
            )
        }
    }
}

/**
 * One saved zone: its name and radius (in the user's units), "inside now" while the survey is
 * paused in it. A tap edits it; the trailing action asks before deleting it. While the
 * organization turns zones off ([editable] false) the row is read-only: no tap, no delete.
 */
@Composable
internal fun PrivacyZoneRow(zone: PrivacyZone, inside: Boolean, editable: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val name = zoneDisplayName(zone)
    val radius = stringResource(R.string.privacy_zone_row_radius, formatRadius(zone.radiusMeters))
    val detail = if (inside) joinedWithDot(radius, stringResource(R.string.privacy_zone_inside_now)) else radius
    val clickModifier = if (editable) {
        Modifier.clickable(onClickLabel = stringResource(R.string.privacy_zone_edit_title), onClick = onEdit)
    } else {
        Modifier
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (editable) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.remove_format, name),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Asks before deleting a zone. [name] is the zone's saved name (blank reads as "Unnamed zone"), not
 * a name typed in the editor and not saved yet.
 */
@Composable
internal fun PrivacyZoneDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NsConfirmationDialog(
        title = stringResource(R.string.privacy_zone_delete_title, name.ifBlank { stringResource(R.string.privacy_zone_unnamed) }),
        message = stringResource(R.string.privacy_zone_delete_message),
        confirmText = stringResource(R.string.privacy_zone_delete_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

/**
 * The "Privacy zones" row: the count, or a nudge when there are none. Turned off by the
 * organization it shows "Set by your organization" and a tap explains instead of opening the page.
 *
 * @param summary   The plural phrase after the count (Settings and the Community page word it differently).
 * @param noneText  The phrase after "None" when there are none.
 */
@Composable
fun PrivacyZonesRow(
    count: Int,
    allowed: Boolean,
    summary: Int,
    noneText: Int,
    onOpen: () -> Unit,
    onBlocked: () -> Unit,
) {
    NsRow(
        title = stringResource(R.string.privacy_zones_title),
        subtitle = when {
            !allowed -> joinedWithDot(stringResource(R.string.privacy_zones_off), stringResource(R.string.privacy_zones_summary_off))
            count == 0 -> joinedWithDot(stringResource(R.string.privacy_zones_none), stringResource(noneText))
            else -> joinedWithDot(pluralStringResource(R.plurals.privacy_zones_count, count, count), pluralStringResource(summary, count))
        },
        icon = R.drawable.ic_shield,
        locked = !allowed,
        onClick = if (allowed) onOpen else onBlocked,
    )
}

/** The zone's name, or "Unnamed zone" for a zone saved without one. */
@Composable
internal fun zoneDisplayName(zone: PrivacyZone): String = zone.name.ifBlank { stringResource(R.string.privacy_zone_unnamed) }

/** A radius in the user's units ("250m", "820ft"). */
@Composable
internal fun formatRadius(radiusMeters: Int): String = MeasurementFormatter.formatDistance(LocalContext.current, radiusMeters.toDouble())

/** [parts] joined with the app's dot separator, so no translatable sentence carries the dot itself. */
@Composable
internal fun joinedWithDot(vararg parts: String): String = parts.joinToString(stringResource(R.string.survey_dot_separator))
