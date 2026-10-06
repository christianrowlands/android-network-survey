package com.craxiom.networksurvey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The raw color values of the Network Survey design system, and the only place in the app where
 * theme hex values are written down.
 *
 * Values come from the redesign prototype's CSS tokens. Composables should not read this object
 * directly: use [androidx.compose.material3.MaterialTheme.colorScheme] for surfaces, text and the
 * accent, and [NsTheme.colors] for protocol, survey-state and status colors, so light and dark
 * resolve automatically. The exceptions are code paths that run outside a composition (MapLibre
 * layers, enum color properties), which use the theme-independent groups here ([Signal], [Band]).
 *
 * The XML side (`res/values/colors.xml` and `res/values-night/colors.xml`) mirrors these values;
 * `XmlComposeColorParityTest` keeps the two in step.
 */
object NsPalette {

    /** Scrim behind modal sheets and dialogs; components apply their own alpha. */
    val Scrim = Color(0xFF000000)

    /** Neutral ground, panel, ink and rule colors for the light scheme. */
    object Light {
        val Ground = Color(0xFFF2F4F7)
        val Panel = Color(0xFFFFFFFF)
        val Nav = Color(0xFFFFFFFF)
        val PanelHigh = Color(0xFFECEFF3)
        val Ink = Color(0xFF1B1F26)
        val Ink2 = Color(0xFF5B6372)
        val Ink3 = Color(0xFF8A93A0)
        val Rule = Color(0xFFE3E6EB)
        val SurfaceDim = Color(0xFFDCE0E6)
        val Toast = Color(0xFF2A2F36)
    }

    /** Neutral ground, panel, ink and rule colors for the dark scheme. */
    object Dark {
        val Ground = Color(0xFF0E1116)
        val PanelLowest = Color(0xFF0B0E12)
        val Panel = Color(0xFF181C22)
        val Nav = Color(0xFF151920)
        val PanelHigh = Color(0xFF1F242C)
        val Ink = Color(0xFFE8EBF0)
        val Ink2 = Color(0xFFA3ABB6)
        val Ink3 = Color(0xFF7B8490)
        val Rule = Color(0xFF2A3038)
        val SurfaceBright = Color(0xFF2F353E)
    }

    /**
     * Status colors with their soft container variants. Green, amber and red each mean one thing:
     * working, paused or degraded, and failing.
     */
    object Status {
        val GreenLight = Color(0xFF1E8E3E)
        val GreenSoftLight = Color(0xFFE6F4EA)
        val AmberLight = Color(0xFFB26A00)
        val AmberSoftLight = Color(0xFFFFF3E0)
        val RedLight = Color(0xFFC62828)
        val RedSoftLight = Color(0xFFFDE8E7)

        val GreenDark = Color(0xFF5DC777)
        val GreenSoftDark = Color(0xFF173222)
        val AmberDark = Color(0xFFF0B860)
        val AmberSoftDark = Color(0xFF3A2B12)
        val RedDark = Color(0xFFFF7A70)
        val RedSoftDark = Color(0xFF3A1B1A)

        /** Text on a light green or red fill where the prototype green is too light for 4.5:1. */
        val GreenDeepLight = Color(0xFF18753A)
        val OnGreenContainerLight = Color(0xFF0D5A28)
        val OnGreenContainerDark = Color(0xFF9BE3AE)
        val OnRedContainerLight = Color(0xFF8C1D18)
        val OnRedContainerDark = Color(0xFFFFDAD6)
    }

    /** Per-record-type colors, used everywhere a record type appears (chips, tiles, records bar). */
    object Protocol {
        val CellularLight = Color(0xFF6D4DE0)
        val PhoneStateLight = Color(0xFFD99A00)
        val WifiLight = Color(0xFF0E9BB0)
        val BluetoothLight = Color(0xFF2F6BFF)
        val GnssLight = Color(0xFF1E8E3E)
        val CdrLight = Color(0xFFD9486B)

        val CellularDark = Color(0xFFA48BFF)
        val PhoneStateDark = Color(0xFFF2C15A)
        val WifiDark = Color(0xFF4FD0E4)
        val BluetoothDark = Color(0xFF7FA4FF)
        val GnssDark = Color(0xFF5DC777)
        val CdrDark = Color(0xFFFF8AA6)
    }

    /**
     * The five-step signal strength ramp, strongest first. Theme-independent because it also
     * colors MapLibre layers and View screens; it mirrors the XML `rssi_*` colors.
     */
    object Signal {
        val Strong = Color(0xFF388E3C)
        val Good = Color(0xFFFBC02D)
        val Fair = Color(0xFFF57C00)
        val Weak = Color(0xFFD32F2F)
        val VeryWeak = Color(0xFFD50000)
    }

    /**
     * The signal ramp for text (dBm readouts), strongest first. The fill ramp in [Signal] is too
     * light for text on light panels (yellow measures about 1.6:1), so text uses these instead.
     */
    object SignalText {
        val StrongLight = Color(0xFF276B2B)
        val GoodLight = Color(0xFF7F6400)
        val FairLight = Color(0xFFA84B06)
        val WeakLight = Color(0xFFC62828)
        val VeryWeakLight = Color(0xFFA50E0E)

        val StrongDark = Color(0xFF5DC777)
        val GoodDark = Color(0xFFFBC02D)
        val FairDark = Color(0xFFF57C00)
        val WeakDark = Color(0xFFFF7A70)
        val VeryWeakDark = Color(0xFFFF5252)
    }

    /** Wi-Fi band dot colors. Theme-independent: they are always drawn with a halo. */
    object Band {
        val Ghz24 = Color(0xFFF2C94C)
        val Ghz5 = Color(0xFF56CCF2)
        val Ghz6 = Color(0xFFBB6BD9)
    }

    /**
     * Wi-Fi tag text colors (security type, Passpoint, hidden SSID), light and dark. The light
     * values are darkened versions of the dark ones so tag text keeps 4.5:1 on the tag fill.
     */
    object Tag {
        val SecurityStandardLight = Color(0xFF156B4A)
        val SecurityEnterpriseLight = Color(0xFF0B6680)
        val SecurityOpenLight = Color(0xFFA53D24)
        val PasspointLight = Color(0xFF4255BA)
        val HiddenSsidLight = Color(0xFF4F6672)

        val SecurityStandardDark = Color(0xFF77D8B0)
        val SecurityEnterpriseDark = Color(0xFF6FCFE0)
        val SecurityOpenDark = Color(0xFFE88A6C)
        val PasspointDark = Color(0xFF8FA0F0)
        val HiddenSsidDark = Color(0xFF90A4AE)
    }
}

/**
 * One accent palette in both schemes. [fill] colors buttons and selected controls, [ink] is accent
 * text on a surface, and [soft] is the tinted container behind selected chips, tabs and the nav
 * pill. [onFill] is the text and icon color drawn on [fill].
 */
data class NsAccentPalette(
    val fill: Color,
    val ink: Color,
    val soft: Color,
    val onFill: Color,
)

/**
 * The accent colors the app can be built with. Slate ships; Teal (the pre-redesign app color)
 * stays defined so the accent can be switched back with a one-line change.
 */
enum class NsAccent(val light: NsAccentPalette, val dark: NsAccentPalette) {
    SLATE(
        light = NsAccentPalette(
            fill = Color(0xFF3F5178),
            ink = Color(0xFF3F5178),
            soft = Color(0xFFE9EDF6),
            onFill = Color(0xFFFFFFFF),
        ),
        // Dark text on the light slate fill: white measures about 2.5:1, this is about 8:1.
        dark = NsAccentPalette(
            fill = Color(0xFF8FA5DB),
            ink = Color(0xFFB4C4EA),
            soft = Color(0xFF243048),
            onFill = Color(0xFF0E1116),
        ),
    ),
    TEAL(
        light = NsAccentPalette(
            fill = Color(0xFF0F6681),
            ink = Color(0xFF0F6681),
            soft = Color(0xFFDCEFF6),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFF1E9DC2),
            ink = Color(0xFF62CBEA),
            soft = Color(0xFF0F2E3A),
            onFill = Color(0xFF0E1116),
        ),
    );

    /** Returns this accent's palette for the given scheme. */
    fun palette(darkTheme: Boolean): NsAccentPalette = if (darkTheme) dark else light
}
