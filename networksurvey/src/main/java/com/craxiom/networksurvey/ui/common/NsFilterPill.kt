package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

private val FILTER_PILL_MIN_HEIGHT = 36.dp

/**
 * A multi-select pill in a list's filter sheet: tinted while on, outlined while off, read by
 * TalkBack as a checkbox.
 *
 * @param leading An optional mark before the label, such as a band's color dot.
 */
@Composable
fun NsFilterPill(
    label: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val border = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(NsTheme.shapes.pill)
            .background(background)
            .border(BorderStroke(1.dp, border), NsTheme.shapes.pill)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .heightIn(min = FILTER_PILL_MIN_HEIGHT)
            .padding(horizontal = NsTheme.spacing.m + NsTheme.spacing.xxs, vertical = NsTheme.spacing.s - NsTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s - NsTheme.spacing.xxs),
    ) {
        leading?.invoke()
        Text(text = label, style = NsTheme.textStyles.compact, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
