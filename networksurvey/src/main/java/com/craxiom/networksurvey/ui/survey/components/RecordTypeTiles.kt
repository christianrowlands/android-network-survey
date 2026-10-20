package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.model.ItemLock
import com.craxiom.networksurvey.ui.survey.model.RecordTypeTileUi
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.NsAdaptiveGrid
import com.craxiom.networksurvey.ui.common.ROW_ICON
import com.craxiom.networksurvey.ui.common.SMALL_ICON

/**
 * The "What should it survey?" tiles, two per row (one at a large font scale), each an icon beside
 * its name and description. An on tile is tinted with the accent and shows a check (so on and off
 * never differ by color alone); a tile the organization set keeps the on look with a building
 * glyph and opens the lock sheet instead of toggling; Phone state included with Cellular shows the
 * on look dimmed.
 */
@Composable
fun RecordTypeTiles(
    tiles: List<RecordTypeTileUi>,
    onToggle: (RecordType) -> Unit,
    onLocked: (RecordTypeTileUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One column when a tile's longest word would not fit in half the width (large font, long
    // translation), so a name like "Bluetooth" is never broken in the middle
    NsAdaptiveGrid(maxColumns = TILES_PER_ROW, modifier = modifier.fillMaxWidth(), gap = NsTheme.spacing.s) {
        tiles.forEach { tile ->
            RecordTypeTile(
                tile = tile,
                onToggle = { onToggle(tile.type) },
                onLocked = { onLocked(tile) },
            )
        }
    }
}

@Composable
private fun RecordTypeTile(
    tile: RecordTypeTileUi,
    onToggle: () -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(tile.type.labelRes)
    val description = when {
        tile.notSentAnywhere -> stringResource(R.string.survey_type_not_sent_anywhere)
        tile.lock == ItemLock.INCLUDED_WITH_CELLULAR -> stringResource(R.string.survey_type_included_with_cellular)
        else -> stringResource(tile.type.descriptionRes)
    }
    val colors = MaterialTheme.colorScheme
    val descriptionColor = if (tile.notSentAnywhere) NsTheme.colors.status.warningText else colors.onSurfaceVariant
    val interaction = when (tile.lock) {
        ItemLock.NONE -> Modifier.toggleable(value = tile.on, role = Role.Checkbox, onValueChange = { onToggle() })
        ItemLock.ORGANIZATION -> Modifier
            .clickable(onClickLabel = stringResource(R.string.survey_lock_learn_why), onClick = onLocked)
            .lockedDescription(stringResource(R.string.survey_lock_announcement, name, onOffWord(tile.on)))

        ItemLock.INCLUDED_WITH_CELLULAR -> Modifier
            .clickable(onClickLabel = stringResource(R.string.survey_lock_learn_why), onClick = onLocked)
            .lockedDescription(stringResource(R.string.survey_included_announcement, name))
    }

    // Phone state that rides along with Cellular is on but not the user's choice: the on look,
    // dimmed and without the check, as in the voted design.
    val included = tile.lock == ItemLock.INCLUDED_WITH_CELLULAR
    Surface(
        modifier = modifier
            .heightIn(min = NsTheme.spacing.rowMinHeight)
            .alpha(if (included) INCLUDED_ALPHA else 1f),
        shape = NsTheme.shapes.tile,
        color = if (tile.on) colors.primaryContainer else colors.surfaceContainerLow,
        border = BorderStroke(TILE_BORDER, if (tile.on) colors.primary else colors.outlineVariant),
    ) {
        Row(
            modifier = interaction.padding(NsTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            Icon(
                painter = painterResource(tile.type.iconRes),
                contentDescription = null,
                tint = if (tile.on) tile.type.color else colors.outline,
                modifier = Modifier.size(ROW_ICON),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = descriptionColor)
            }
            TileBadge(tile, shown = !included, Modifier.align(Alignment.Top))
        }
    }
}

/**
 * The tile's check, warning or building glyph, or an empty slot the same size when it has none, so
 * a tile is as wide on as off: the grid's column count, and the description's line breaks, then
 * stay put when a tile is toggled.
 */
@Composable
private fun TileBadge(tile: RecordTypeTileUi, shown: Boolean, modifier: Modifier) {
    when {
        !shown -> Spacer(modifier.size(SMALL_ICON))

        tile.notSentAnywhere -> Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = NsTheme.colors.status.warningText,
            modifier = modifier.size(SMALL_ICON),
        )

        tile.lock == ItemLock.ORGANIZATION -> Icon(
            painter = painterResource(R.drawable.ic_domain),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = modifier.size(SMALL_ICON),
        )

        tile.on -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(SMALL_ICON),
        )

        else -> Spacer(modifier.size(SMALL_ICON))
    }
}

/** "on" or "off" for TalkBack announcements. */
@Composable
fun onOffWord(on: Boolean): String = stringResource(if (on) R.string.survey_state_on else R.string.survey_state_off)

private fun Modifier.lockedDescription(text: String): Modifier = semantics(mergeDescendants = true) {
    contentDescription = text
}

private const val TILES_PER_ROW = 2
private val TILE_BORDER = 1.dp
private const val INCLUDED_ALPHA = 0.6f
