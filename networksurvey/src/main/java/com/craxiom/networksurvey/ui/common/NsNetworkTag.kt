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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants
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
 *  - [NetworkTagVariant.New] - success-tinted text on the success container, marking a tower
 *    found new during a Community upload (the Map's serving-cell card)
 *  - [NetworkTagVariant.Tracker] - tracker-tinted text/border, surface background, marking a
 *    Bluetooth device that looks like an AirTag
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
        NetworkTagVariant.Tracker -> tags.tracker
        NetworkTagVariant.Excluded -> NsTheme.colors.status.warning.content
        NetworkTagVariant.New -> NsTheme.colors.status.success.content
        NetworkTagVariant.Standard -> Color.Unspecified
    }

    val bg = when (variant) {
        NetworkTagVariant.Standard, is NetworkTagVariant.Security, NetworkTagVariant.Excluded, NetworkTagVariant.Tracker ->
            MaterialTheme.colorScheme.surfaceContainerHighest

        NetworkTagVariant.Passpoint -> Color.Transparent
        NetworkTagVariant.New -> NsTheme.colors.status.success.container
    }
    val borderColor = when (variant) {
        NetworkTagVariant.Standard -> MaterialTheme.colorScheme.outlineVariant
        is NetworkTagVariant.Security, NetworkTagVariant.Excluded, NetworkTagVariant.New, NetworkTagVariant.Tracker ->
            semanticColor.copy(alpha = TAG_BORDER_ALPHA)

        NetworkTagVariant.Passpoint -> semanticColor.copy(alpha = PASSPOINT_BORDER_ALPHA)
    }
    val textColor = when (variant) {
        NetworkTagVariant.Standard -> MaterialTheme.colorScheme.onSurface
        is NetworkTagVariant.Security, NetworkTagVariant.Excluded, NetworkTagVariant.Passpoint, NetworkTagVariant.New,
        NetworkTagVariant.Tracker -> semanticColor
    }

    Text(
        text = text,
        style = NsTheme.textStyles.tag,
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

    object New : NetworkTagVariant()

    object Tracker : NetworkTagVariant()
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

/**
 * A security label (from WifiBeaconMessageConstants.getEncryptionTypeString, or a group's labels
 * joined) in the phone's language: "Open" and "Unknown" are words, the protocol names are not. The
 * label itself stays English, since [classifySecurity] and the sort read it.
 */
@Composable
fun securityLabelText(label: String): String {
    val open = stringResource(R.string.wifi_security_open)
    val unknown = stringResource(R.string.unknown)
    return label.split(WifiBeaconMessageConstants.SECURITY_LABEL_SEPARATOR)
        .joinToString(WifiBeaconMessageConstants.SECURITY_LABEL_SEPARATOR) {
            when (it) {
                WifiBeaconMessageConstants.OPEN_LABEL -> open
                WifiBeaconMessageConstants.UNKNOWN_LABEL -> unknown
                else -> it
            }
        }
}
