package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The small uppercase label over a group of rows on a long list page such as Settings ("SURVEY",
 * "PHONE"), as in the prototype. It is a heading for TalkBack.
 */
@Composable
fun NsGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = NsTheme.spacing.xs, top = NsTheme.spacing.s)
            .semantics { heading() },
    )
}
