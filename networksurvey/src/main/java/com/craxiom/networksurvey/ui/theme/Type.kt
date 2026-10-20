package com.craxiom.networksurvey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tabular figures everywhere, as in the prototype (`font-variant-numeric: tabular-nums`), so live
 * counters, timers and dBm values do not jitter as their digits change.
 */
private const val TABULAR_FIGURES = "tnum"

private val base = Typography()

/**
 * The app's Material 3 type scale. Roboto (the platform default) at the prototype's sizes; the
 * M3 defaults already match the prototype for titleLarge (22, top bar), titleMedium (16, section
 * header), bodyMedium (14), labelMedium (12) and labelSmall (11). bodyLarge drops to 15, the
 * prototype's row title size.
 */
val NsTypography: Typography = Typography(
    displayLarge = base.displayLarge.tabular(),
    displayMedium = base.displayMedium.tabular(),
    displaySmall = base.displaySmall.tabular(),
    headlineLarge = base.headlineLarge.tabular(),
    headlineMedium = base.headlineMedium.tabular(),
    headlineSmall = base.headlineSmall.tabular(),
    titleLarge = base.titleLarge.tabular(),
    titleMedium = base.titleMedium.tabular(),
    titleSmall = base.titleSmall.tabular(),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp).tabular(),
    bodyMedium = base.bodyMedium.tabular(),
    bodySmall = base.bodySmall.tabular(),
    labelLarge = base.labelLarge.tabular(),
    labelMedium = base.labelMedium.tabular(),
    labelSmall = base.labelSmall.tabular(),
)

private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_FIGURES)

/**
 * Text styles the prototype uses that have no Material 3 role. Read through
 * [NsTheme.textStyles]. Compose has no text-transform, so [overline] callers uppercase the string
 * themselves.
 */
object NsTextStyles {
    /** The elapsed-time readout on the Recording state. */
    val timer = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** A one-line state headline on a hero panel. */
    val stateHeadline = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 35.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** The selected setup's name on the setup card. */
    val setupName = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Small uppercase section kicker ("SETUP", "RECORDING"). */
    val overline = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Compact one-line text: the Signals sub-tab labels and the scan strip. */
    val compact = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** The small label inside a network tag pill ("WPA2", "NEW", "AirTag"). */
    val tag = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** The SSID labels drawn on the Wi-Fi spectrum charts. */
    val chartLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )

    /** Identifiers read character by character: Mission ID, BSSID, MAC address, file names. */
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Large identifiers typed or read character by character: the OUI MAC, the PLMN MCC and MNC fields. */
    val monoReadout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 1.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}
