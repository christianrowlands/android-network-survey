package com.craxiom.networksurvey.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors the Material 3 [androidx.compose.material3.ColorScheme] has no role for: record types,
 * survey states, status, and the Wi-Fi tags. Read them through [NsTheme.colors] so they follow
 * the light or dark scheme.
 */
@Immutable
data class NsColors(
    val protocol: NsProtocolColors,
    val state: NsSurveyStateColors,
    val status: NsStatusColors,
    val tag: NsTagColors,
    val signalText: NsSignalTextColors,
)

/** One color per record type, used everywhere that record type appears. */
@Immutable
data class NsProtocolColors(
    val cellular: Color,
    val phoneState: Color,
    val wifi: Color,
    val bluetooth: Color,
    val gnss: Color,
    val cdr: Color,
)

/** A foreground color and the tinted container it sits on. */
@Immutable
data class NsColorPair(
    val content: Color,
    val container: Color,
)

/**
 * The survey-state vocabulary, one color pair per state, shared by the Survey screen and the
 * notification: Ready (nothing running, all set), Recording, Paused (deliberate, resumes on its
 * own) and Needs attention (no fix, stalled, a destination failing).
 */
@Immutable
data class NsSurveyStateColors(
    val ready: NsColorPair,
    val recording: NsColorPair,
    val paused: NsColorPair,
    val attention: NsColorPair,
)

/**
 * Generic status colors for rows, icons and badges: working, degraded, failing.
 *
 * @property warningText Amber for warning text on a panel; meets 4.5:1 in both schemes, unlike
 *                       [warning]'s content color in light mode.
 */
@Immutable
data class NsStatusColors(
    val success: NsColorPair,
    val warning: NsColorPair,
    val danger: NsColorPair,
    val warningText: Color,
)

/**
 * Text colors for the five signal buckets, strongest first. Meters, dots and map layers keep the
 * brighter [NsPalette.Signal] fill ramp; text drawn in a signal color uses these.
 */
@Immutable
data class NsSignalTextColors(
    val strong: Color,
    val good: Color,
    val fair: Color,
    val weak: Color,
    val veryWeak: Color,
)

/** Text colors for the Wi-Fi security, Passpoint and hidden-SSID tags, and the Bluetooth tracker tag. */
@Immutable
data class NsTagColors(
    val securityStandard: Color,
    val securityEnterprise: Color,
    val securityOpen: Color,
    val passpoint: Color,
    val hiddenSsid: Color,
    val tracker: Color,
)

/** Builds the extended colors for one scheme and accent. */
fun nsColors(darkTheme: Boolean, accent: NsAccent = NsAccent.DEFAULT): NsColors {
    val a = accent.palette(darkTheme)
    val status = if (darkTheme) {
        NsStatusColors(
            success = NsColorPair(NsPalette.Status.GreenDark, NsPalette.Status.GreenSoftDark),
            warning = NsColorPair(NsPalette.Status.AmberDark, NsPalette.Status.AmberSoftDark),
            danger = NsColorPair(NsPalette.Status.RedDark, NsPalette.Status.RedSoftDark),
            warningText = NsPalette.Status.AmberDark,
        )
    } else {
        NsStatusColors(
            success = NsColorPair(NsPalette.Status.GreenLight, NsPalette.Status.GreenSoftLight),
            warning = NsColorPair(NsPalette.Status.AmberLight, NsPalette.Status.AmberSoftLight),
            danger = NsColorPair(NsPalette.Status.RedLight, NsPalette.Status.RedSoftLight),
            warningText = NsPalette.Status.AmberDeepLight,
        )
    }
    return NsColors(
        protocol = if (darkTheme) darkProtocol else lightProtocol,
        state = NsSurveyStateColors(
            ready = NsColorPair(a.ink, a.soft),
            recording = status.success,
            paused = status.warning,
            attention = status.danger,
        ),
        status = status,
        tag = if (darkTheme) darkTags else lightTags,
        signalText = if (darkTheme) darkSignalText else lightSignalText,
    )
}

private val lightProtocol = NsProtocolColors(
    cellular = NsPalette.Protocol.CellularLight,
    phoneState = NsPalette.Protocol.PhoneStateLight,
    wifi = NsPalette.Protocol.WifiLight,
    bluetooth = NsPalette.Protocol.BluetoothLight,
    gnss = NsPalette.Protocol.GnssLight,
    cdr = NsPalette.Protocol.CdrLight,
)

private val darkProtocol = NsProtocolColors(
    cellular = NsPalette.Protocol.CellularDark,
    phoneState = NsPalette.Protocol.PhoneStateDark,
    wifi = NsPalette.Protocol.WifiDark,
    bluetooth = NsPalette.Protocol.BluetoothDark,
    gnss = NsPalette.Protocol.GnssDark,
    cdr = NsPalette.Protocol.CdrDark,
)

private val lightTags = NsTagColors(
    securityStandard = NsPalette.Tag.SecurityStandardLight,
    securityEnterprise = NsPalette.Tag.SecurityEnterpriseLight,
    securityOpen = NsPalette.Tag.SecurityOpenLight,
    passpoint = NsPalette.Tag.PasspointLight,
    hiddenSsid = NsPalette.Tag.HiddenSsidLight,
    tracker = NsPalette.Tag.TrackerLight,
)

private val darkTags = NsTagColors(
    securityStandard = NsPalette.Tag.SecurityStandardDark,
    securityEnterprise = NsPalette.Tag.SecurityEnterpriseDark,
    securityOpen = NsPalette.Tag.SecurityOpenDark,
    passpoint = NsPalette.Tag.PasspointDark,
    hiddenSsid = NsPalette.Tag.HiddenSsidDark,
    tracker = NsPalette.Tag.TrackerDark,
)

private val lightSignalText = NsSignalTextColors(
    strong = NsPalette.SignalText.StrongLight,
    good = NsPalette.SignalText.GoodLight,
    fair = NsPalette.SignalText.FairLight,
    weak = NsPalette.SignalText.WeakLight,
    veryWeak = NsPalette.SignalText.VeryWeakLight,
)

private val darkSignalText = NsSignalTextColors(
    strong = NsPalette.SignalText.StrongDark,
    good = NsPalette.SignalText.GoodDark,
    fair = NsPalette.SignalText.FairDark,
    weak = NsPalette.SignalText.WeakDark,
    veryWeak = NsPalette.SignalText.VeryWeakDark,
)

/**
 * Provides [NsColors] to the composition; set by [NsTheme]. Defaults to the dark set of
 * [NsAccent.DEFAULT].
 */
val LocalNsColors = staticCompositionLocalOf { nsColors(darkTheme = true) }
