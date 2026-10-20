package com.craxiom.networksurvey.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Text color pairs in both schemes and every accent meet WCAG contrast: 4.5:1 for Material text
 * roles and tag text, 3:1 for survey-state and status badges (the prototype's own green and amber
 * badge text measures about 3.9:1 on its soft fill, a candidate to darken later).
 */
class ColorContrastTest {

    @Test
    fun materialTextPairsMeetAa() {
        forEachScheme { label, scheme, _ ->
            textPairs(scheme).forEach { (name, fg, bg) -> assertContrast("$label $name", fg, bg, TEXT_MIN) }
        }
    }

    @Test
    fun tagTextMeetsAaOnTheTagFill() {
        forEachScheme { label, scheme, colors ->
            val fill = scheme.surfaceContainerHighest
            with(colors.tag) {
                listOf(
                    "securityStandard" to securityStandard,
                    "securityEnterprise" to securityEnterprise,
                    "securityOpen" to securityOpen,
                    "passpoint" to passpoint,
                    "hiddenSsid" to hiddenSsid,
                ).forEach { (name, fg) -> assertContrast("$label tag $name", fg, fill, TEXT_MIN) }
            }
            assertContrast("$label hiddenSsid on panel", colors.tag.hiddenSsid, scheme.surfaceContainerLow, TEXT_MIN)
        }
    }

    @Test
    fun signalTextMeetsAaOnSurfaces() {
        forEachScheme { label, scheme, colors ->
            with(colors.signalText) {
                listOf("strong" to strong, "good" to good, "fair" to fair, "weak" to weak, "veryWeak" to veryWeak)
                    .forEach { (name, fg) ->
                        assertContrast("$label signal $name on surface", fg, scheme.surface, TEXT_MIN)
                        assertContrast("$label signal $name on panel", fg, scheme.surfaceContainerLow, TEXT_MIN)
                    }
            }
        }
    }

    @Test
    fun stateAndStatusBadgesMeetLargeTextContrast() {
        forEachScheme { label, _, colors ->
            listOf(
                "ready" to colors.state.ready,
                "recording" to colors.state.recording,
                "paused" to colors.state.paused,
                "attention" to colors.state.attention,
                "success" to colors.status.success,
                "warning" to colors.status.warning,
                "danger" to colors.status.danger,
            ).forEach { (name, pair) ->
                assertContrast("$label state $name", pair.content, pair.container, BADGE_MIN)
            }
        }
    }

    private fun textPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onPrimary/primary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary/secondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary/tertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onError/error", s.onError, s.error),
        Triple("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
        Triple("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface),
        Triple("primary/surfaceContainerLow", s.primary, s.surfaceContainerLow),
        Triple("onSurface/surface", s.onSurface, s.surface),
        Triple("onSurface/surfaceContainerLow", s.onSurface, s.surfaceContainerLow),
        Triple("onSurface/surfaceContainer", s.onSurface, s.surfaceContainer),
        Triple("onSurface/surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh),
        Triple("onSurface/surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest),
        Triple("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface),
        Triple("onSurfaceVariant/surfaceContainerLow", s.onSurfaceVariant, s.surfaceContainerLow),
        Triple("onSurfaceVariant/surfaceContainerHighest", s.onSurfaceVariant, s.surfaceContainerHighest),
    )

    private fun forEachScheme(block: (String, ColorScheme, NsColors) -> Unit) {
        for (accent in NsAccent.entries) {
            block("${accent.name} light", nsLightColorScheme(accent), nsColors(darkTheme = false, accent = accent))
            block("${accent.name} dark", nsDarkColorScheme(accent), nsColors(darkTheme = true, accent = accent))
        }
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast %.2f is below %.1f".format(ratio, minimum), ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val BADGE_MIN = 3.0
    }
}
