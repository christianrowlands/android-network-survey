package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Opacity of a tinted tag's border, over its text color. */
private const val TAG_BORDER_ALPHA = 0.3f
private const val PASSPOINT_BORDER_ALPHA = 0.35f

/**
 * Small pill used for Security ("WPA2"), Standard ("802.11ax"), Passpoint, and Excluded labels
 * on Wi-Fi list rows and the Details hero.
 *
 * Four visual variants:
 *  - [NetworkTagVariant.Standard] - neutral (surface background, onSurface text) for 802.11xx
 *  - [NetworkTagVariant.Security] - semantic-tinted text/border, surface background
 *  - [NetworkTagVariant.Passpoint] - accent-tinted border + text, transparent background
 *  - [NetworkTagVariant.Excluded] - warning-tinted text/border, surface background, marking
 *    SSIDs excluded from survey data output (files, uploads, streaming)
 */
@Composable
fun NsNetworkTag(
    text: String,
    variant: NetworkTagVariant,
    modifier: Modifier = Modifier,
) {
    val tags = NsTheme.colors.tag
    val semanticColor = when (variant) {
        is NetworkTagVariant.Security -> when (variant.type) {
            SecurityType.OPEN -> tags.securityOpen
            SecurityType.ENTERPRISE -> tags.securityEnterprise
            SecurityType.STANDARD -> tags.securityStandard
        }

        NetworkTagVariant.Passpoint -> tags.passpoint
        NetworkTagVariant.Excluded -> NsTheme.colors.status.warning.content
        NetworkTagVariant.Standard -> Color.Unspecified
    }

    val bg = when (variant) {
        NetworkTagVariant.Standard, is NetworkTagVariant.Security, NetworkTagVariant.Excluded ->
            MaterialTheme.colorScheme.surfaceContainerHighest

        NetworkTagVariant.Passpoint -> Color.Transparent
    }
    val borderColor = when (variant) {
        NetworkTagVariant.Standard -> MaterialTheme.colorScheme.outlineVariant
        is NetworkTagVariant.Security, NetworkTagVariant.Excluded -> semanticColor.copy(alpha = TAG_BORDER_ALPHA)
        NetworkTagVariant.Passpoint -> semanticColor.copy(alpha = PASSPOINT_BORDER_ALPHA)
    }
    val textColor = when (variant) {
        NetworkTagVariant.Standard -> MaterialTheme.colorScheme.onSurface
        is NetworkTagVariant.Security, NetworkTagVariant.Excluded, NetworkTagVariant.Passpoint -> semanticColor
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color = bg)
            .border(
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(5.dp),
            )
            .heightIn(min = 19.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** Which look an [NsNetworkTag] takes. */
sealed class NetworkTagVariant {
    object Standard : NetworkTagVariant()

    data class Security(val type: SecurityType) : NetworkTagVariant()

    object Passpoint : NetworkTagVariant()

    object Excluded : NetworkTagVariant()
}

/** Coarse security classes that get their own tag color. */
enum class SecurityType {
    OPEN,
    ENTERPRISE,
    STANDARD,
}

/**
 * Classifies a security label string (from WifiBeaconMessageConstants.getEncryptionTypeString)
 * into one of [SecurityType].
 */
fun classifySecurity(label: String): SecurityType {
    val upper = label.uppercase()
    return when {
        upper.contains("OPEN") -> SecurityType.OPEN
        upper.contains("ENT") -> SecurityType.ENTERPRISE
        else -> SecurityType.STANDARD
    }
}
