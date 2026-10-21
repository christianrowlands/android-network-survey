package com.craxiom.networksurvey.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** The accent preference values and their fallback. */
class NsAccentTest {

    @Test
    fun everyAccent_roundTripsThroughItsPrefValue() {
        NsAccent.entries.forEach { assertEquals(it, NsAccent.fromPrefValue(it.prefValue)) }
    }

    @Test
    fun aMissingOrUnknownValue_fallsBackToTheDefault() {
        assertEquals(NsAccent.DEFAULT, NsAccent.fromPrefValue(null))
        assertEquals(NsAccent.DEFAULT, NsAccent.fromPrefValue("magenta"))
    }

    @Test
    fun navy_isTheDefault_andFirstInThePicker() {
        assertEquals(NsAccent.NAVY, NsAccent.DEFAULT)
        assertEquals(NsAccent.NAVY, NsAccent.entries.first())
    }
}
