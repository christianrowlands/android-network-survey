package com.craxiom.networksurvey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * The Network Survey theme: Material 3 with the redesign's palette, type scale and shapes, plus
 * the extended [NsColors].
 *
 * [darkTheme] defaults to the configuration's night mode, which the Theme setting drives through
 * `AppCompatDelegate` (see [ThemeMode]); call sites should not pass it except in previews.
 *
 * Dynamic color (wallpaper-derived schemes) is deliberately not supported. It would replace the
 * accent and the per-protocol colors that carry meaning across the app, so do not add it back as
 * a "nice to have".
 */
@Composable
fun NsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: NsAccent = NsAccent.DEFAULT,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(darkTheme, accent) {
        if (darkTheme) nsDarkColorScheme(accent) else nsLightColorScheme(accent)
    }
    val nsColors = remember(darkTheme, accent) { nsColors(darkTheme, accent) }

    CompositionLocalProvider(LocalNsColors provides nsColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NsTypography,
            shapes = NsMaterialShapes,
            content = content
        )
    }
}

/**
 * Access to the Network Survey tokens that sit beside [MaterialTheme]: extended colors, spacing,
 * shapes and the text styles that have no Material 3 role.
 */
object NsTheme {
    val colors: NsColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNsColors.current

    val spacing: NsSpacing get() = NsSpacing

    val shapes: NsShapes get() = NsShapes

    val textStyles: NsTextStyles get() = NsTextStyles
}
