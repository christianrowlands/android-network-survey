package com.craxiom.networksurvey.logging.db.uploader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared response helpers: logged bodies stay short, and only an HTML page reads as one.
 */
class UploadResponsesTest {

    @Test
    fun logSnippetCapsLongBodies() {
        assertEquals(MAX_LOGGED_BODY_LENGTH, logSnippet("x".repeat(MAX_LOGGED_BODY_LENGTH * 10)).length)
        assertEquals("0,OK", logSnippet("0,OK"))
    }

    @Test
    fun onlyHtmlPagesLookLikeHtml() {
        assertTrue(looksLikeHtml("<html><body>403 Forbidden</body></html>"))
        assertTrue(looksLikeHtml("\r\n  <!DOCTYPE html>"))
        assertFalse(looksLikeHtml("""{"error":"Invalid API key"}"""))
        assertFalse(looksLikeHtml("Err: Invalid token"))
        assertFalse(looksLikeHtml(""))
    }
}
