package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.plmn.displayName
import com.craxiom.networksurvey.data.plmn.secondaryName
import com.craxiom.networksurvey.ui.plmn.isoToFlag

/*
 * The tower's provider on the tower sheet: the row at the top of the detail view's identity card,
 * and the name line of each row in the list of towers at one site.
 */

/**
 * The provider row at the top of the tower details: the map color dot, the provider name and a
 * line under it. Every state has the same two lines, so the sheet does not jump when the lookup
 * answers; only a name too long for one line (shown on up to two) makes the row taller. A PLMN shared by several names or countries expands in place to list them, and a
 * failed lookup tries again when tapped.
 */
@Composable
internal fun TowerProviderRow(
    provider: TowerProvider,
    providerColor: Color,
    onColorClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val known = provider as? TowerProvider.Known
    var expanded by remember(provider) { mutableStateOf(false) }
    val rowAction: Modifier = when {
        known?.hasMore == true -> Modifier.clickable(
            onClickLabel = stringResource(if (expanded) R.string.tower_provider_hide_all else R.string.tower_provider_show_all),
            onClick = { expanded = !expanded }
        )

        provider is TowerProvider.Unavailable -> Modifier.clickable(
            onClickLabel = stringResource(R.string.tower_provider_retry),
            onClick = onRetry
        )

        else -> Modifier.semantics(mergeDescendants = true) {}
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(rowAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderColorDot(providerColor, onColorClick)
            ProviderLines(provider, Modifier.weight(1f).padding(vertical = 4.dp))
            if (known?.hasMore == true) {
                Icon(
                    painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        if (known != null) {
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                SharedPlmnRecords(known.records)
            }
        }
    }
}

/**
 * The name line of a row in the list of towers at one site: the provider name with its MCC-MNC
 * after it, or the MCC-MNC alone until (or unless) the name is known. Always one line, so the
 * rows keep their height.
 */
@Composable
internal fun TowerListProviderLine(provider: TowerProvider, mcc: String, mnc: String) {
    val plmn = stringResource(R.string.mcc_mnc_value, mcc, mnc)
    val name = (provider as? TowerProvider.Known)?.name
    if (name == null) {
        Text(
            text = stringResource(R.string.label_value_pair, stringResource(R.string.mcc_mnc_label), plmn),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // The name gives way first: the MCC-MNC is what tells two rows apart
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = plmn,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** The provider's map color, tappable to change it. */
@Composable
private fun ProviderColorDot(color: Color, onClick: () -> Unit) {
    val description = stringResource(R.string.tower_provider_change_color)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .size(DOT_SIZE)
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun ProviderLines(provider: TowerProvider, modifier: Modifier) {
    val nameStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val detailStyle = MaterialTheme.typography.bodySmall
    val loadingDescription = stringResource(R.string.tower_provider_loading)

    when (provider) {
        TowerProvider.Loading -> Column(
            modifier = modifier.clearAndSetSemantics { contentDescription = loadingDescription },
            verticalArrangement = Arrangement.spacedBy(LINE_GAP)
        ) {
            PlaceholderLine(nameStyle, NAME_PLACEHOLDER_WIDTH)
            PlaceholderLine(detailStyle, DETAIL_PLACEHOLDER_WIDTH)
        }

        is TowerProvider.Known -> Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
            KnownNameLine(provider, nameStyle)
            KnownDetailLine(provider, detailStyle)
        }

        TowerProvider.NotListed -> TwoLines(
            name = stringResource(R.string.tower_provider_unknown),
            detail = stringResource(R.string.tower_provider_not_listed),
            nameStyle = nameStyle,
            detailStyle = detailStyle,
            modifier = modifier
        )

        is TowerProvider.Unavailable -> TwoLines(
            name = stringResource(R.string.tower_provider_unavailable),
            detail = stringResource(if (provider.offline) R.string.tower_provider_offline else R.string.tower_provider_failed),
            nameStyle = nameStyle,
            detailStyle = detailStyle,
            modifier = modifier
        )
    }
}

@Composable
private fun KnownNameLine(provider: TowerProvider.Known, style: TextStyle) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = provider.name ?: stringResource(R.string.tower_provider_unknown),
            style = style,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (provider.otherNameCount > 0) {
            MoreNamesBadge(provider.otherNameCount)
        }
    }
}

/**
 * The line under the name: how many countries share the PLMN, else the legal name (when it is not
 * the brand) and the country with its flag.
 */
@Composable
private fun KnownDetailLine(provider: TowerProvider.Known, style: TextStyle) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (provider.countryCount > 1) {
        Text(
            text = pluralStringResource(R.plurals.tower_provider_shared_countries, provider.countryCount, provider.countryCount),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val record = provider.records.first()
    val legalName = record.secondaryName()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (legalName != null) {
            // The legal name gives way first: the country is the shorter, more useful half
            Text(
                text = legalName,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(text = SEPARATOR, style = style, color = color)
        }
        Text(text = isoToFlag(record.iso), style = style)
        record.countryName()?.let {
            Text(text = it, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The "+1" after the name when other provider names share the PLMN. */
@Composable
private fun MoreNamesBadge(count: Int) {
    val description = pluralStringResource(R.plurals.tower_provider_more_names, count, count)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        Text(
            text = stringResource(R.string.tower_provider_more_badge, count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Every operator and country sharing the PLMN, under the provider row once it is expanded. */
@Composable
private fun SharedPlmnRecords(records: List<PlmnRecord>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DOT_SLOT_WIDTH, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        records.forEach { record ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {}
            ) {
                Text(text = isoToFlag(record.iso), style = MaterialTheme.typography.bodyLarge)
                Column {
                    Text(
                        text = record.displayName() ?: stringResource(R.string.tower_provider_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    record.countryName()?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoLines(name: String, detail: String, nameStyle: TextStyle, detailStyle: TextStyle, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LINE_GAP)) {
        Text(
            text = name,
            style = nameStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = detail,
            style = detailStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A rounded bar one line of [style] tall: a blank line of text in that style, so it matches the
 * text that replaces it at any font size.
 */
@Composable
private fun PlaceholderLine(style: TextStyle, widthFraction: Float) {
    Text(
        text = " ",
        style = style,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

private fun PlmnRecord.countryName(): String? =
    country?.takeIf { it.isNotBlank() } ?: region?.takeIf { it.isNotBlank() }

private const val SEPARATOR = "·"
private const val NAME_PLACEHOLDER_WIDTH = 0.5f
private const val DETAIL_PLACEHOLDER_WIDTH = 0.75f
private val DOT_SIZE = 12.dp
private val DOT_SLOT_WIDTH = 48.dp
private val LINE_GAP = 2.dp
