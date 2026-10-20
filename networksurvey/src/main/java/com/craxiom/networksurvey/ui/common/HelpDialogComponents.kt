package com.craxiom.networksurvey.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

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
