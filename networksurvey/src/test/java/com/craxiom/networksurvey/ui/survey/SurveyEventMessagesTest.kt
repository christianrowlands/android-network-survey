package com.craxiom.networksurvey.ui.survey

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The list joining the Survey sentence and snackbars use: a space follows each comma (an unquoted
 * resource loses its trailing space), and the last two names are joined with "and".
 */
@RunWith(RobolectricTestRunner::class)
class SurveyEventMessagesTest {

    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun join_putsASpaceAfterEachComma() {
        assertEquals("Cellular, Phone state and Wi-Fi", SurveyEventMessages.join(resources, listOf("Cellular", "Phone state", "Wi-Fi")))
    }

    @Test
    fun join_twoNames_usesAnd() {
        assertEquals("Cellular and Wi-Fi", SurveyEventMessages.join(resources, listOf("Cellular", "Wi-Fi")))
    }

    @Test
    fun join_oneOrNoName() {
        assertEquals("Cellular", SurveyEventMessages.join(resources, listOf("Cellular")))
        assertEquals("", SurveyEventMessages.join(resources, emptyList()))
    }
}
