package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A bold section title followed by its content. Used in help dialogs to group related items.
 */
@Composable
internal fun HelpSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    content()
}

/**
 * A single bulleted help item with a bold label and a description.
 */
@Composable
internal fun HelpItem(label: String, description: String) {
    Text(
        text = buildAnnotatedString {
            append("• ")
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(label)
            }
            append(" - $description")
        },
        style = MaterialTheme.typography.bodyMedium
    )
}
