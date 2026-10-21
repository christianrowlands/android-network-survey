package com.craxiom.networksurvey.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The shared building blocks of the Survey screen and the destination pages: the panel, slim
 * notices, text actions and status words. The section header is in `SectionHeader.kt`.
 */

/** One white (light) or dark gray (dark) panel on the cool gray ground. */
@Composable
fun NsPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NsTheme.shapes.panel,
        color = color,
        shadowElevation = PANEL_SHADOW,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = NsTheme.spacing.panelPaddingHorizontal,
                vertical = NsTheme.spacing.panelPaddingVertical,
            ),
            content = content,
        )
    }
}

/**
 * The panel at the top of a destination page that says how it is doing: an icon, a title and a
 * subtitle, and an optional action under them. The text is read by TalkBack as one item and
 * announced politely when it changes, so a status that moves from Connecting to Streaming is heard
 * without stealing focus.
 *
 * @param titleColor The title's color, for a status word in its level's color.
 * @param action     A button for what the status offers, end-aligned under the text.
 */
@Composable
fun NsStatusPanel(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = Color.Unspecified,
    action: (@Composable () -> Unit)? = null,
) {
    NsPanel(modifier = modifier) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(ROW_ICON))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { action() }
        }
    }
}

/**
 * A slim line of text with a leading icon and optional actions, used for the location line, the
 * organization line and the upgrade notice. Actions are 48dp text buttons; they move under the text
 * when they would squeeze its words (large font, long translation).
 */
@Composable
fun InlineNotice(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = color,
    emphasized: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NsTheme.spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(SMALL_ICON))
        NsBesideOrBelow(
            modifier = Modifier.weight(1f),
            main = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
                    color = color,
                )
            },
            end = { if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
        )
    }
}

/** A text action sized to the 48dp touch target. */
@Composable
fun NsTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = NsTheme.spacing.touchTarget)) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** The colored status word on the right of a destination row ("Ready", "Streaming", "Paused"). */
@Composable
fun StatusLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier,
    )
}

val SMALL_ICON = 18.dp
val ROW_ICON = 24.dp

/** The Material 3 opacity for disabled content. */
const val DISABLED_ALPHA = 0.38f
private val PANEL_SHADOW = 1.dp
