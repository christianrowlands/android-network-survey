package com.craxiom.networksurvey.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

private const val PULSE_DURATION_MS = 900
private const val PULSE_MIN_ALPHA = 0.35f
private val STATUS_DOT_SIZE = 7.dp
private val SORT_PILL_MIN_HEIGHT = 32.dp
private val SORT_PILL_BORDER = 1.dp

// Long localized sort names ellipsize instead of pushing the strip's other pieces out.
private val SORT_PILL_LABEL_MAX_WIDTH = 150.dp

/** The small green dot that pulses on a list's strip while it updates live. */
@Composable
fun NsPulsingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan-dot")
    val alpha by transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-dot-alpha",
    )
    Box(
        modifier = modifier
            .size(STATUS_DOT_SIZE)
            .clip(CircleShape)
            .background(NsTheme.colors.status.success.content)
            .alpha(alpha),
    )
}

/**
 * The pill on a list's strip that names the sort and opens the sort choices; a 48dp target.
 *
 * @param contentDescription What TalkBack reads, when the visible [text] is not enough.
 */
@Composable
fun NsSortPill(text: String, onClick: () -> Unit, contentDescription: String? = null) {
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(NsTheme.shapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(SORT_PILL_BORDER, MaterialTheme.colorScheme.outlineVariant), NsTheme.shapes.pill)
            .clickable(onClick = onClick)
            .heightIn(min = SORT_PILL_MIN_HEIGHT)
            .padding(horizontal = NsTheme.spacing.m, vertical = NsTheme.spacing.s - NsTheme.spacing.xxs)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = SORT_PILL_LABEL_MAX_WIDTH),
        )
    }
}
