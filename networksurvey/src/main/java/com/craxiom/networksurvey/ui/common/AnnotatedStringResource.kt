package com.craxiom.networksurvey.ui.common

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Reads a string resource that may contain styling markup (such as &lt;b&gt;, &lt;i&gt;, &lt;u&gt;,
 * or &lt;a href&gt;) and converts it to a Compose [AnnotatedString], preserving bold/italic/underline
 * runs and turning links into clickable [LinkAnnotation.Url]s.
 *
 * Use this instead of stringResource when a dialog message needs to keep its emphasis or links.
 */
@Composable
fun annotatedStringResource(@StringRes id: Int): AnnotatedString {
    val styled = LocalResources.current.getText(id)
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(id, styled, linkColor) {
        styledCharSequenceToAnnotatedString(styled, linkColor)
    }
}

/**
 * Converts a (possibly [Spanned]) [CharSequence] into a Compose [AnnotatedString], preserving the
 * common inline styles. Useful when the styled text was already resolved outside of composition
 * (for example selected by a `when` branch in Java code) and passed into a dialog.
 */
fun styledCharSequenceToAnnotatedString(text: CharSequence, linkColor: Color): AnnotatedString {
    val spanned = text as? Spanned ?: return AnnotatedString(text.toString())

    return buildAnnotatedString {
        append(spanned.toString())
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (start < 0 || end < 0 || start >= end) continue

            when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)

                    Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

                    Typeface.BOLD_ITALIC ->
                        addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            ),
                            start,
                            end,
                        )
                }

                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)

                is ForegroundColorSpan ->
                    addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)

                is URLSpan -> addLink(link(span.url, linkColor), start, end)
            }
        }
    }
}

/**
 * Turns the web addresses written out in plain [text] (for example "https://example.com/help" in a
 * translated message) into clickable links.
 */
fun annotateUrls(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val url = matcher.group()
        val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        addLink(link(target, linkColor), matcher.start(), matcher.end())
    }
}

/**
 * Makes the first [linkText] in [text] a link to [url] that calls [onClick] when tapped, so the
 * caller decides how to open it. [text] stays plain when [linkText] is not in it, for example in a
 * translation that dropped the placeholder.
 */
fun annotateLink(text: String, linkText: String, url: String, linkColor: Color, onClick: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(text)
        val start = text.indexOf(linkText)
        if (start >= 0) addLink(link(url, linkColor) { onClick(url) }, start, start + linkText.length)
    }

private fun link(url: String, linkColor: Color, onClick: (() -> Unit)? = null) = LinkAnnotation.Url(
    url = url,
    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
    linkInteractionListener = onClick?.let { { _ -> it() } },
)
