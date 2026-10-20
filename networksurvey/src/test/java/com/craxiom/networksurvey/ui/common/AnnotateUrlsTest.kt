package com.craxiom.networksurvey.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Web addresses written out in a translated message become links, and nothing else does; and
 * [annotateLink] links one known address and tells its caller when it is tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnnotateUrlsTest {

    private fun links(text: String): List<Pair<String, String>> {
        val annotated = annotateUrls(text, Color.Blue)
        return annotated.getLinkAnnotations(0, annotated.length).map {
            text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url
        }
    }

    @Test
    fun aFullAddress_linksToItself_withoutTheSentencesFullStop() {
        assertEquals(
            listOf("https://example.com/help" to "https://example.com/help"),
            links("See https://example.com/help for more info."),
        )
    }

    @Test
    fun aBareAddress_opensOverHttps() {
        assertEquals(listOf("example.com/help" to "https://example.com/help"), links("Read example.com/help first"))
    }

    @Test
    fun textWithoutAnAddress_hasNoLinks() {
        assertEquals(emptyList<Pair<String, String>>(), links("The device GNSS time is wrong."))
    }

    @Test
    fun annotateLink_linksOnlyTheFirstMatch_toTheGivenUrl() {
        val annotated = annotateLink("Go to example.com, then example.com/help", "example.com", "https://example.com", Color.Blue) {}
        val link = annotated.getLinkAnnotations(0, annotated.length).single()
        assertEquals(6 to 17, link.start to link.end)
        assertEquals("https://example.com", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun annotateLink_withoutTheLinkText_staysPlain() {
        val annotated = annotateLink("A translation that dropped the address", "example.com", "https://example.com", Color.Blue) {}
        assertEquals(0, annotated.getLinkAnnotations(0, annotated.length).size)
    }

    @Test
    fun annotateLink_tapCallsBackWithTheUrl() {
        val opened = mutableListOf<String>()
        val annotated = annotateLink("Go to example.com", "example.com", "https://example.com", Color.Blue) { opened += it }
        val url = annotated.getLinkAnnotations(0, annotated.length).single().item as LinkAnnotation.Url
        url.linkInteractionListener!!.onClick(url)
        assertEquals(listOf("https://example.com"), opened)
    }
}
