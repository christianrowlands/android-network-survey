package com.craxiom.networksurvey.ui.scout.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.common.NsPulsingDot
import com.craxiom.networksurvey.ui.scout.ScoutLevel
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The small pieces every NS Scout surface shares: the status word with its dot, the Scout's badge
 * and its name as an identifier.
 */

/** The color of a status word at [level]. */
val ScoutLevel.color: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        ScoutLevel.OK -> NsTheme.colors.status.success.content
        ScoutLevel.WARN -> NsTheme.colors.status.warningText
        ScoutLevel.BAD -> NsTheme.colors.status.danger.content
        ScoutLevel.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/**
 * The status word after a dot in its level's color. It is the only TalkBack live region on a Scout
 * surface (its own node, so the row around it can change every second without being announced).
 * [live] pulses the dot, for a Scout that is capturing during a survey.
 */
@Composable
fun ScoutStateWord(word: String, level: ScoutLevel, modifier: Modifier = Modifier, live: Boolean = false) {
    val color = level.color
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(WORD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live && level == ScoutLevel.OK) {
            NsPulsingDot()
        } else {
            Box(Modifier.size(DOT).clip(CircleShape).background(color))
        }
        Text(
            text = word,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (level == ScoutLevel.OFF) FontWeight.Normal else FontWeight.Medium,
            color = color,
        )
    }
}

/** The rounded square holding the Scout glyph (accent) or, for this phone, the Wi-Fi glyph (neutral). */
@Composable
fun ScoutBadge(@DrawableRes icon: Int, modifier: Modifier = Modifier, accent: Boolean = true, size: Dp = BADGE) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(NsTheme.shapes.tile)
            .background(if (accent) colors.primaryContainer else colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (accent) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
    }
}

/**
 * A Scout's name ("NS-ABCD") in the identifier style, read by TalkBack one character at a time
 * ("N S A B C D") rather than as a word.
 */
@Composable
fun ScoutName(name: String, modifier: Modifier = Modifier, style: TextStyle = NsTheme.textStyles.mono, color: Color = Color.Unspecified) {
    Text(
        text = name,
        style = style,
        color = color,
        modifier = modifier.semantics { contentDescription = spokenIdentifier(name) },
    )
}

/** @return [identifier] spelled out for speech: "NS-ABCD" becomes "N S A B C D". */
private fun spokenIdentifier(identifier: String): String =
    identifier.filter { it.isLetterOrDigit() }.toList().joinToString(SPOKEN_SEPARATOR)

private const val SPOKEN_SEPARATOR = " "
private val DOT = 8.dp
private val WORD_GAP = 6.dp
private val BADGE = 40.dp
