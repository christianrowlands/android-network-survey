package com.craxiom.networksurvey.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.R
import java.util.Locale

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

    /**
     * Neutral ground, panel, ink and rule colors for the dark scheme. A panel's shadow can't be
     * seen on a dark ground, so the fill step alone sets cards apart: [Panel] sits well above
     * [Ground] (about 1.24:1, lifted from the prototype's 1.11:1 in Oct 2026), with the nav and
     * CTA bar chrome ([Nav]) between them.
     */
    object Dark {
        val Ground = Color(0xFF0E1116)
        val PanelLowest = Color(0xFF0B0E12)
        val Panel = Color(0xFF21262E)
        val Nav = Color(0xFF181C22)
        val PanelHigh = Color(0xFF292F38)
        val Ink = Color(0xFFE8EBF0)
        val Ink2 = Color(0xFFA3ABB6)
        val Ink3 = Color(0xFF8992A0)
        val Rule = Color(0xFF343A44)
        val SurfaceBright = Color(0xFF3A414B)
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

        /**
         * Warning text on a light panel. The amber above is about 4:1 on white, under the 4.5:1
         * that body text needs; this deeper amber is about 6:1.
         */
        val AmberDeepLight = Color(0xFF8A5300)

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
     * Network tag text colors (Wi-Fi security type, Passpoint, hidden SSID, the Bluetooth tracker
     * tag), light and dark. The light values are darkened versions of the dark ones so tag text
     * keeps 4.5:1 on the tag fill.
     */
    object Tag {
        val SecurityStandardLight = Color(0xFF156B4A)
        val SecurityEnterpriseLight = Color(0xFF0B6680)
        val SecurityOpenLight = Color(0xFFA53D24)
        val PasspointLight = Color(0xFF4255BA)
        val HiddenSsidLight = Color(0xFF4F6672)
        val TrackerLight = Color(0xFF7B2F8E)

        val SecurityStandardDark = Color(0xFF77D8B0)
        val SecurityEnterpriseDark = Color(0xFF6FCFE0)
        val SecurityOpenDark = Color(0xFFE88A6C)
        val PasspointDark = Color(0xFF8FA0F0)
        val HiddenSsidDark = Color(0xFF9AADB7)
        val TrackerDark = Color(0xFFCE93D8)
    }

    /**
     * The Wi-Fi spectrum's series colors, one per SSID (picked by the SSID's hash so a network keeps
     * its color from scan to scan). Unchanged from the spectrum's original palette.
     */
    val SpectrumSeries: List<Color> = listOf(
        Color(0xFF835DB1),
        Color(0xFF852659),
        Color(0xFFB42D2D),
        Color(0xFFD33838),
        Color(0xFFE65100),
        Color(0xFFF57F17),
        Color(0xFFFF6F00),
        Color(0xFFE65100),
        Color(0xFFF9A825),
        Color(0xFF9E9D24),
        Color(0xFF558B2F),
        Color(0xFF2E7D32),
        Color(0xFF00695C),
        Color(0xFF004D40),
        Color(0xFF01579B),
        Color(0xFF1C50A0),
        Color(0xFF464B83),
        Color(0xFF63559E),
    )
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
 * The accent colors the user can pick in Settings, in the order the picker lists them. Navy, the
 * [DEFAULT], was picked on a device from a round of candidates (Oct 2026); Sky is the launcher
 * icon's beacon; Slate was the first redesign accent, from the old launcher icon. The rest come
 * from the prototype's accent control, plus Graphite. Violet, Green and Orange sit near the
 * Cellular, GNSS and status colors; that was accepted for the choice they give.
 *
 * Each accent has its own shade per scheme, as Material's tones do: a deep fill with white text in
 * the light scheme, and a light fill with dark text in the dark scheme, so every accent reads the
 * same way as Navy in both.
 *
 * Only the Compose UI follows the user's pick. The XML colors, and so the few places that still
 * read them (system DayNight widgets, the uploader notification color), stay on [DEFAULT].
 *
 * @property label The accent's name in the picker.
 */
enum class NsAccent(@StringRes val label: Int, val light: NsAccentPalette, val dark: NsAccentPalette) {
    // White on the navy fill is about 11:1; dark text on the dark-scheme fill about 10:1.
    NAVY(
        label = R.string.accent_navy,
        light = NsAccentPalette(
            fill = Color(0xFF1E3A6E),
            ink = Color(0xFF1E3A6E),
            soft = Color(0xFFE3EAF6),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFFA9C1F5),
            ink = Color(0xFFCFDCFA),
            soft = Color(0xFF1C2B4A),
            onFill = Color(0xFF0E1116),
        ),
    ),
    // The launcher icon's beacon color. The icon Sky (#22C3E6) is the dark-scheme fill, with dark text
    // at about 9:1; on a light panel it is about 2:1, so the light scheme uses a deeper Sky.
    SKY(
        label = R.string.accent_sky,
        light = NsAccentPalette(
            fill = Color(0xFF0A7EA4),
            ink = Color(0xFF086A8A),
            soft = Color(0xFFDDF3FA),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFF22C3E6),
            ink = Color(0xFF8FE0F2),
            soft = Color(0xFF0C2D38),
            onFill = Color(0xFF0E1116),
        ),
    ),
    SLATE(
        label = R.string.accent_slate,
        light = NsAccentPalette(
            fill = Color(0xFF3F5178),
            ink = Color(0xFF3F5178),
            soft = Color(0xFFE9EDF6),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFF8FA5DB),
            ink = Color(0xFFB4C4EA),
            soft = Color(0xFF243048),
            onFill = Color(0xFF0E1116),
        ),
    ),
    // A near-neutral accent for those who want no color at all.
    GRAPHITE(
        label = R.string.accent_graphite,
        light = NsAccentPalette(
            fill = Color(0xFF3D434D),
            ink = Color(0xFF3D434D),
            soft = Color(0xFFE8EAEE),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFFC5CDD8),
            ink = Color(0xFFDCE1E8),
            soft = Color(0xFF2A2F37),
            onFill = Color(0xFF0E1116),
        ),
    ),
    // A green-leaning teal, so it stays apart from Sky.
    TEAL(
        label = R.string.accent_teal,
        light = NsAccentPalette(
            fill = Color(0xFF0F766E),
            ink = Color(0xFF0C6159),
            soft = Color(0xFFD9F2EE),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFF4FD1C1),
            ink = Color(0xFF9BE8DE),
            soft = Color(0xFF0F2E2B),
            onFill = Color(0xFF0E1116),
        ),
    ),
    // The dark Indigo stays saturated so it does not read as a second Navy or Slate.
    INDIGO(
        label = R.string.accent_indigo,
        light = NsAccentPalette(
            fill = Color(0xFF3B5BDB),
            ink = Color(0xFF3B5BDB),
            soft = Color(0xFFE5EAFB),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFF7C8CFF),
            ink = Color(0xFFB4BFFF),
            soft = Color(0xFF1E2A4E),
            onFill = Color(0xFF0E1116),
        ),
    ),
    VIOLET(
        label = R.string.accent_violet,
        light = NsAccentPalette(
            fill = Color(0xFF6D4DE0),
            ink = Color(0xFF6D4DE0),
            soft = Color(0xFFEDE8FB),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFFC0ABFF),
            ink = Color(0xFFDCD0FF),
            soft = Color(0xFF2A2150),
            onFill = Color(0xFF0E1116),
        ),
    ),
    GREEN(
        label = R.string.accent_green,
        light = NsAccentPalette(
            fill = Color(0xFF1B7F4B),
            ink = Color(0xFF166B3F),
            soft = Color(0xFFDFF3E7),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFF7AD8A3),
            ink = Color(0xFFA9E9C4),
            soft = Color(0xFF153521),
            onFill = Color(0xFF0E1116),
        ),
    ),
    ORANGE(
        label = R.string.accent_orange,
        light = NsAccentPalette(
            fill = Color(0xFFC4502A),
            ink = Color(0xFFA23E22),
            soft = Color(0xFFFBE7E0),
            onFill = Color(0xFFFFFFFF),
        ),
        dark = NsAccentPalette(
            fill = Color(0xFFFFA083),
            ink = Color(0xFFFFC6B3),
            soft = Color(0xFF3B2018),
            onFill = Color(0xFF0E1116),
        ),
    );

    /** The value stored in the accent preference. */
    val prefValue: String get() = name.lowercase(Locale.ROOT)

    /** Returns this accent's palette for the given scheme. */
    fun palette(darkTheme: Boolean): NsAccentPalette = if (darkTheme) dark else light

    companion object {
        /**
         * The accent the app ships with. The XML colors mirror it (`XmlComposeColorParityTest`),
         * so changing it means updating `values/colors.xml` and `values-night/colors.xml` too.
         */
        val DEFAULT = NAVY

        /** @return The accent stored as [value], or [DEFAULT] when it is missing or unknown. */
        fun fromPrefValue(value: String?): NsAccent = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
