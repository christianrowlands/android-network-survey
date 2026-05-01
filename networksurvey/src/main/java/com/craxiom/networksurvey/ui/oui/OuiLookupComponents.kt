package com.craxiom.networksurvey.ui.oui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.util.copyTextToClipboard

private const val MAX_HEX_BYTES = 6
private val MacFontSize = 22.sp
private val MacLetterSpacing = 1.5.sp
private const val MAC_PLACEHOLDER = "··"

/**
 * Card frame shared by every non-empty state on the OUI Lookup screen. Matches
 * `MaterialTheme.shapes.medium` and uses the standard ElevatedCard surface so the screen
 * blends with other Compose details cards in the app.
 */
@Composable
internal fun SurfaceCard(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/**
 * Renders the typed MAC address as 6 colon-separated bytes in monospace, with the first 3 octets
 * (the OUI) drawn in the primary color and the last 3 (the NIC suffix) plus colons and unfilled
 * placeholder dots drawn in the muted onSurfaceVariant color. Visually teaches the user which
 * bytes participate in the lookup.
 */
@Composable
internal fun MacDisplay(query: String) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text: AnnotatedString = buildAnnotatedString {
        val hex = query.replace(":", "")
        val ouiStyle = SpanStyle(color = primary)
        val mutedStyle = SpanStyle(color = muted)
        for (byteIndex in 0 until MAX_HEX_BYTES) {
            val start = byteIndex * 2
            val byteText = if (start + 2 <= hex.length) {
                hex.substring(start, start + 2)
            } else if (start < hex.length) {
                hex.substring(start) + MAC_PLACEHOLDER.substring(1)
            } else {
                MAC_PLACEHOLDER
            }
            val style = if (byteIndex < 3) ouiStyle else mutedStyle
            withStyle(style) { append(byteText) }
            if (byteIndex < MAX_HEX_BYTES - 1) {
                withStyle(mutedStyle) { append(":") }
            }
        }
    }
    val spokenMac = query.replace(":", " : ").ifEmpty { "empty" }
    Text(
        text = text,
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = "MAC address $spokenMac" },
        fontFamily = FontFamily.Monospace,
        fontSize = MacFontSize,
        fontWeight = FontWeight.Medium,
        letterSpacing = MacLetterSpacing,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ResultCard(vendor: String, query: String) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.oui_lookup_copy_clip_label)
    val toastMessage = stringResource(R.string.oui_lookup_copy_toast)
    SurfaceCard {
        Column {
            SectionLabel(stringResource(R.string.oui_lookup_section_vendor))
            Spacer(Modifier.height(4.dp))
            Text(
                text = vendor,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column {
            SectionLabel(stringResource(R.string.oui_lookup_section_mac_address))
            Spacer(Modifier.height(8.dp))
            MacDisplay(query)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.oui_lookup_legend_oui),
                )
                LegendItem(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = stringResource(R.string.oui_lookup_legend_nic),
                )
            }
        }
        Button(
            onClick = {
                copyTextToClipboard(context, clipLabel, vendor, toastMessage)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.oui_lookup_copy_vendor))
        }
    }
}

@Composable
internal fun MessageCard(
    query: String,
    title: String,
    body: String,
    pillLabel: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showMacDisplay: Boolean = true,
) {
    SurfaceCard {
        if (showMacDisplay) {
            MacDisplay(query)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pillLabel != null) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = pillLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

