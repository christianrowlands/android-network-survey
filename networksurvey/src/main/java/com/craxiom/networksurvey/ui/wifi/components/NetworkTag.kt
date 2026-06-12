package com.craxiom.networksurvey.ui.wifi.components

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
import com.craxiom.networksurvey.ui.theme.WifiTokens

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
fun NetworkTag(
    text: String,
    variant: NetworkTagVariant,
    modifier: Modifier = Modifier,
) {
    val semanticColor = when (variant) {
        is NetworkTagVariant.Security -> when (variant.type) {
            SecurityType.OPEN -> WifiTokens.SecurityOpen
            SecurityType.ENTERPRISE -> WifiTokens.SecurityEnterprise
            SecurityType.STANDARD -> WifiTokens.SecurityMint
        }

        NetworkTagVariant.Passpoint -> WifiTokens.PasspointBlue
        NetworkTagVariant.Excluded -> WifiTokens.SignalFair
        NetworkTagVariant.Standard -> Color.Unspecified
    }

    val bg = when (variant) {
        NetworkTagVariant.Standard, is NetworkTagVariant.Security, NetworkTagVariant.Excluded ->
            MaterialTheme.colorScheme.surfaceContainerHighest

        NetworkTagVariant.Passpoint -> Color.Transparent
    }
    val borderColor = when (variant) {
        NetworkTagVariant.Standard -> MaterialTheme.colorScheme.outlineVariant
        is NetworkTagVariant.Security, NetworkTagVariant.Excluded -> semanticColor.copy(alpha = 0.3f)
        NetworkTagVariant.Passpoint -> WifiTokens.PasspointBlue.copy(alpha = 0.35f)
    }
    val textColor = when (variant) {
        NetworkTagVariant.Standard -> MaterialTheme.colorScheme.onSurface
        is NetworkTagVariant.Security, NetworkTagVariant.Excluded -> semanticColor
        NetworkTagVariant.Passpoint -> WifiTokens.PasspointBlue
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

sealed class NetworkTagVariant {
    object Standard : NetworkTagVariant()

    data class Security(val type: SecurityType) : NetworkTagVariant()

    object Passpoint : NetworkTagVariant()

    object Excluded : NetworkTagVariant()
}

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
