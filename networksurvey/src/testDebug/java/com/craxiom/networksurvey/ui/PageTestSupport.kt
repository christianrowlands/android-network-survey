package com.craxiom.networksurvey.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.robolectric.RuntimeEnvironment

/** The font scale the large-text checks use, the size people with low vision often pick. */
const val LARGE_FONT_SCALE = 1.3f

/**
 * Shows [content] in the light theme. [largeFont] uses [LARGE_FONT_SCALE]; [fontScale], when set,
 * uses that scale instead.
 */
fun ComposeContentTestRule.showPage(largeFont: Boolean = false, fontScale: Float? = null, content: @Composable () -> Unit) {
    setContent { PageTheme(largeFont, fontScale, content) }
}

/** The theme and font scale [showPage] uses, for tests that set their own content. */
@Composable
fun PageTheme(largeFont: Boolean = false, fontScale: Float? = null, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = fontScale ?: if (largeFont) LARGE_FONT_SCALE else density.fontScale
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
        NsTheme(darkTheme = false) { content() }
    }
}

/**
 * @return The laid out text of the node showing [text], to check its line count and ellipsis.
 * Needs `@GraphicsMode(NATIVE)` for real text metrics.
 */
fun ComposeContentTestRule.textLayout(text: String, substring: Boolean = false): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.single()
}

/**
 * The text of the string resource [id] formatted with [args], so a test follows a wording change
 * (or fails to compile on a rename) instead of copying the English.
 */
fun string(@StringRes id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

/** The text of the plurals resource [id] for [count], formatted with [args]. */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    RuntimeEnvironment.getApplication().resources.getQuantityString(id, count, *args)
