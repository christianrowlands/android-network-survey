package com.craxiom.networksurvey.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material 3 color schemes built from [NsPalette].
 *
 * Prototype tokens map onto roles directly: the ground is `background`/`surface`, the panel is
 * `surfaceContainerLowest`/`surfaceContainerLow` (cards), the nav bar is `surfaceContainer`, the
 * rule is `outlineVariant`, the accent is `primary` with its soft tint as `primaryContainer` and
 * `secondaryContainer` (the nav pill). Roles the prototype never defines are derived from the
 * nearest prototype token; `ColorContrastTest` holds every text pair to 4.5:1.
 *
 * The XML theme (`res/values/colors.xml`, `res/values-night/colors.xml`) mirrors the Slate schemes.
 */
fun nsLightColorScheme(accent: NsAccent = NsAccent.SLATE): ColorScheme {
    val a = accent.light
    return lightColorScheme(
        primary = a.fill,
        onPrimary = a.onFill,
        primaryContainer = a.soft,
        onPrimaryContainer = a.ink,
        inversePrimary = accent.dark.fill,
        secondary = a.fill,
        onSecondary = a.onFill,
        secondaryContainer = a.soft,
        onSecondaryContainer = a.ink,
        tertiary = NsPalette.Status.GreenDeepLight,
        onTertiary = NsPalette.Light.Panel,
        tertiaryContainer = NsPalette.Status.GreenSoftLight,
        onTertiaryContainer = NsPalette.Status.OnGreenContainerLight,
        background = NsPalette.Light.Ground,
        onBackground = NsPalette.Light.Ink,
        surface = NsPalette.Light.Ground,
        onSurface = NsPalette.Light.Ink,
        surfaceVariant = NsPalette.Light.Rule,
        onSurfaceVariant = NsPalette.Light.Ink2,
        surfaceTint = a.fill,
        inverseSurface = NsPalette.Light.Toast,
        inverseOnSurface = NsPalette.Light.Ground,
        error = NsPalette.Status.RedLight,
        onError = NsPalette.Light.Panel,
        errorContainer = NsPalette.Status.RedSoftLight,
        onErrorContainer = NsPalette.Status.OnRedContainerLight,
        outline = NsPalette.Light.Ink3,
        outlineVariant = NsPalette.Light.Rule,
        scrim = NsPalette.Scrim,
        surfaceBright = NsPalette.Light.Panel,
        surfaceDim = NsPalette.Light.SurfaceDim,
        surfaceContainerLowest = NsPalette.Light.Panel,
        surfaceContainerLow = NsPalette.Light.Panel,
        surfaceContainer = NsPalette.Light.Nav,
        surfaceContainerHigh = NsPalette.Light.PanelHigh,
        surfaceContainerHighest = NsPalette.Light.Rule,
    )
}

/** Dark counterpart of [nsLightColorScheme]. Dark is the prototype's default rendering. */
fun nsDarkColorScheme(accent: NsAccent = NsAccent.SLATE): ColorScheme {
    val a = accent.dark
    return darkColorScheme(
        primary = a.fill,
        onPrimary = a.onFill,
        primaryContainer = a.soft,
        onPrimaryContainer = a.ink,
        inversePrimary = accent.light.fill,
        secondary = a.fill,
        onSecondary = a.onFill,
        secondaryContainer = a.soft,
        onSecondaryContainer = a.ink,
        tertiary = NsPalette.Status.GreenDark,
        onTertiary = NsPalette.Dark.Ground,
        tertiaryContainer = NsPalette.Status.GreenSoftDark,
        onTertiaryContainer = NsPalette.Status.OnGreenContainerDark,
        background = NsPalette.Dark.Ground,
        onBackground = NsPalette.Dark.Ink,
        surface = NsPalette.Dark.Ground,
        onSurface = NsPalette.Dark.Ink,
        surfaceVariant = NsPalette.Dark.Rule,
        onSurfaceVariant = NsPalette.Dark.Ink2,
        surfaceTint = a.fill,
        inverseSurface = NsPalette.Dark.Ink,
        inverseOnSurface = NsPalette.Dark.Panel,
        error = NsPalette.Status.RedDark,
        onError = NsPalette.Dark.Ground,
        errorContainer = NsPalette.Status.RedSoftDark,
        onErrorContainer = NsPalette.Status.OnRedContainerDark,
        outline = NsPalette.Dark.Ink3,
        outlineVariant = NsPalette.Dark.Rule,
        scrim = NsPalette.Scrim,
        surfaceBright = NsPalette.Dark.SurfaceBright,
        surfaceDim = NsPalette.Dark.Ground,
        surfaceContainerLowest = NsPalette.Dark.PanelLowest,
        surfaceContainerLow = NsPalette.Dark.Panel,
        surfaceContainer = NsPalette.Dark.Nav,
        surfaceContainerHigh = NsPalette.Dark.PanelHigh,
        surfaceContainerHighest = NsPalette.Dark.Rule,
    )
}
