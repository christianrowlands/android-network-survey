package com.craxiom.networksurvey.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The XML colors (`values/colors.xml`, `values-night/colors.xml`) and the Compose palette are two
 * copies of one design. Before the redesign they drifted apart unnoticed; this pins every shared
 * color so a change to one side fails here until the other side matches.
 */
@RunWith(RobolectricTestRunner::class)
class XmlComposeColorParityTest {

    @Test
    fun lightXmlColorsMatchCompose() {
        RuntimeEnvironment.setQualifiers("notnight")
        assertParity(nsLightColorScheme(NsAccent.DEFAULT), nsColors(darkTheme = false))
    }

    @Test
    fun darkXmlColorsMatchCompose() {
        RuntimeEnvironment.setQualifiers("night")
        assertParity(nsDarkColorScheme(NsAccent.DEFAULT), nsColors(darkTheme = true))
    }

    private fun assertParity(scheme: ColorScheme, colors: NsColors) {
        val roles = mapOf(
            R.color.md_theme_primary to scheme.primary,
            R.color.md_theme_onPrimary to scheme.onPrimary,
            R.color.md_theme_primaryContainer to scheme.primaryContainer,
            R.color.md_theme_onPrimaryContainer to scheme.onPrimaryContainer,
            R.color.md_theme_secondary to scheme.secondary,
            R.color.md_theme_onSecondary to scheme.onSecondary,
            R.color.md_theme_secondaryContainer to scheme.secondaryContainer,
            R.color.md_theme_onSecondaryContainer to scheme.onSecondaryContainer,
            R.color.md_theme_tertiary to scheme.tertiary,
            R.color.md_theme_onTertiary to scheme.onTertiary,
            R.color.md_theme_tertiaryContainer to scheme.tertiaryContainer,
            R.color.md_theme_onTertiaryContainer to scheme.onTertiaryContainer,
            R.color.md_theme_error to scheme.error,
            R.color.md_theme_onError to scheme.onError,
            R.color.md_theme_errorContainer to scheme.errorContainer,
            R.color.md_theme_onErrorContainer to scheme.onErrorContainer,
            R.color.md_theme_background to scheme.background,
            R.color.md_theme_onBackground to scheme.onBackground,
            R.color.md_theme_surface to scheme.surface,
            R.color.md_theme_onSurface to scheme.onSurface,
            R.color.md_theme_surfaceVariant to scheme.surfaceVariant,
            R.color.md_theme_onSurfaceVariant to scheme.onSurfaceVariant,
            R.color.md_theme_outline to scheme.outline,
            R.color.md_theme_outlineVariant to scheme.outlineVariant,
            R.color.md_theme_inverseSurface to scheme.inverseSurface,
            R.color.md_theme_inverseOnSurface to scheme.inverseOnSurface,
            R.color.md_theme_inversePrimary to scheme.inversePrimary,
            R.color.md_theme_surfaceDim to scheme.surfaceDim,
            R.color.md_theme_surfaceBright to scheme.surfaceBright,
            R.color.md_theme_surfaceContainerLowest to scheme.surfaceContainerLowest,
            R.color.md_theme_surfaceContainerLow to scheme.surfaceContainerLow,
            R.color.md_theme_surfaceContainer to scheme.surfaceContainer,
            R.color.md_theme_surfaceContainerHigh to scheme.surfaceContainerHigh,
            R.color.md_theme_surfaceContainerHighest to scheme.surfaceContainerHighest,

            R.color.ns_protocol_cellular to colors.protocol.cellular,
            R.color.ns_protocol_phone_state to colors.protocol.phoneState,
            R.color.ns_protocol_wifi to colors.protocol.wifi,
            R.color.ns_protocol_bluetooth to colors.protocol.bluetooth,
            R.color.ns_protocol_gnss to colors.protocol.gnss,
            R.color.ns_protocol_cdr to colors.protocol.cdr,

            R.color.ns_status_success to colors.status.success.content,
            R.color.ns_status_success_container to colors.status.success.container,
            R.color.ns_status_warning to colors.status.warning.content,
            R.color.ns_status_warning_container to colors.status.warning.container,
            R.color.ns_status_danger to colors.status.danger.content,
            R.color.ns_status_danger_container to colors.status.danger.container,

            R.color.ns_state_ready to colors.state.ready.content,
            R.color.ns_state_ready_container to colors.state.ready.container,
            R.color.ns_state_recording to colors.state.recording.content,
            R.color.ns_state_recording_container to colors.state.recording.container,
            R.color.ns_state_paused to colors.state.paused.content,
            R.color.ns_state_paused_container to colors.state.paused.container,
            R.color.ns_state_attention to colors.state.attention.content,
            R.color.ns_state_attention_container to colors.state.attention.container,
        )
        val context = RuntimeEnvironment.getApplication()
        for ((res, expected) in roles) {
            assertXml(expected, res, context.resources.getResourceEntryName(res))
        }
    }

    private fun assertXml(expected: Color, colorRes: Int, name: String) {
        val actual = ContextCompat.getColor(RuntimeEnvironment.getApplication(), colorRes)
        assertEquals(
            "$name: XML ${hex(actual)} vs Compose ${hex(expected.toArgb())}",
            expected.toArgb(),
            actual
        )
    }

    private fun hex(argb: Int) = String.format("#%08X", argb)
}
