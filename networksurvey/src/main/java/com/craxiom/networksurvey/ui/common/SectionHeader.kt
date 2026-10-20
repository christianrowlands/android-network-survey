package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * A section heading with an optional count or state on the right ("3 of 6 on"), or an [action]
 * (a text button such as "Manage") in its place.
 *
 * The right-hand part stays on the title's line only when the whole title fits beside it on one
 * line. Otherwise it moves to its own line under the title, start-aligned, so a long count, a
 * translation or a large font never squeezes the title into a narrow column.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    topPadding: Dp = NsTheme.spacing.l,
    action: (@Composable () -> Unit)? = null,
) {
    val end: (@Composable () -> Unit)? = when {
        trailing != null || action != null -> {
            {
                // One measurable for whatever the right-hand part emits
                Box {
                    if (trailing != null) {
                        Text(
                            text = trailing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    action?.invoke()
                }
            }
        }

        else -> null
    }
    NsBesideOrBelow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = if (action == null) NsTheme.spacing.s else 0.dp),
        rule = BesideRule.WHOLE_LINE,
        centerEnd = action != null,
        main = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        },
        end = end ?: {},
    )
}
