package com.craxiom.networksurvey.ui.wifi.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared chrome for the Wi-Fi Details cards. The card frame and section header live here so all
 * cards (Hero, Radio, Signal, Capabilities, Survey Data) share the same surface and title style.
 */
@Composable
internal fun DetailsCardFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Card header used by every details card. Title is rendered as Title Case (not uppercased) so it
 * is visually distinct from the 11sp UPPERCASE field labels rendered inside cells. Optional
 * [trailingContent] hosts per-card actions like info or settings icons.
 */
@Composable
internal fun CardHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailingContent()
    }
}

/**
 * Copies [text] to the system clipboard with [clipLabel] and shows a short toast with
 * [toastMessage]. Used by long-press copy actions on SSID/BSSID text in the Wi-Fi screens.
 */
internal fun copyTextToClipboard(
    context: Context,
    clipLabel: String,
    text: String,
    toastMessage: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
