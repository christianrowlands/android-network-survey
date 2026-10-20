package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * A single option in an [NsSegmentedToggle].
 *
 * @property label the visible text, which is also what TalkBack reads
 * @property icon an optional leading icon
 * @property contentDescription replaces [label] for TalkBack when the label alone is not enough
 */
data class SegmentedOption(
    val label: String,
    val icon: ImageVector? = null,
    val contentDescription: String? = null,
)

/** How an [NsSegmentedToggle] looks and what it is to TalkBack. */
enum class SegmentedStyle {
    /** A compact bordered switch between views of the same content. */
    TOGGLE,

    /** A 48dp tab row on the panel fill, one tab per option (the Signals sub-tabs). */
    TABS,
}

/** The fixed height of a [SegmentedStyle.TABS] row: one touch target. */
private val TAB_ROW_HEIGHT = 48.dp

/** The inset of the selected pill inside a tab: a ~40dp pill in the 48dp row, close side by side. */
private val TAB_PILL_INSET_VERTICAL = 4.dp
private val TAB_PILL_INSET_HORIZONTAL = 2.dp

private val PILL = RoundedCornerShape(999.dp)

/**
 * A pill-shaped segmented control with one selected segment highlighted in the accent. As a
 * [SegmentedStyle.TOGGLE] it is the Watchlist "Match by" selector and the History List/Map switch;
 * as [SegmentedStyle.TABS] it is the Signals sub-tab row.
 *
 * @param fillWidth when true each segment takes equal width (a full-width control); when false the
 *   control hugs its content (a compact switch). Tabs always fill the width.
 */
@Composable
fun NsSegmentedToggle(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
    style: SegmentedStyle = SegmentedStyle.TOGGLE,
) {
    val tabs = style == SegmentedStyle.TABS
    val rowModifier = if (tabs) {
        modifier
            .fillMaxWidth()
            // Intrinsic height, so the full-height tabs fill the row and not the screen.
            .height(IntrinsicSize.Min)
            .heightIn(min = TAB_ROW_HEIGHT)
            .clip(PILL)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    } else {
        modifier
            .clip(PILL)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), PILL)
            .padding(3.dp)
    }
    Row(
        modifier = rowModifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(if (tabs) 0.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val weight = if (fillWidth || tabs) Modifier.weight(1f) else Modifier
            if (tabs) {
                Tab(option, selected, { onSelected(index) }, weight)
            } else {
                Segment(option, selected, { onSelected(index) }, weight)
            }
        }
    }
}

@Composable
private fun RowScope.Segment(option: SegmentedOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(PILL)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, onClick = onClick)
            .heightIn(min = 34.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .optionDescription(option),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentContent(option, selected, FontWeight.SemiBold)
    }
}

/** A full-height tab, so the whole 48dp row is the touch target, with the pill drawn inside. */
@Composable
private fun RowScope.Tab(option: SegmentedOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = TAB_ROW_HEIGHT)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .optionDescription(option)
            .padding(horizontal = TAB_PILL_INSET_HORIZONTAL, vertical = TAB_PILL_INSET_VERTICAL),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(PILL)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = NsTheme.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CappedFontScale { SegmentContent(option, selected, FontWeight.Medium) }
        }
    }
}

@Composable
private fun SegmentContent(option: SegmentedOption, selected: Boolean, weight: FontWeight) {
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    option.icon?.let {
        Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
    }
    Text(
        text = option.label,
        color = fg,
        style = NsTheme.textStyles.compact,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Replaces the label for TalkBack only when the option asks for it. */
private fun Modifier.optionDescription(option: SegmentedOption): Modifier =
    option.contentDescription?.let { description -> semantics { contentDescription = description } } ?: this
