package com.craxiom.networksurvey.ui.oui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the [formatMacInput] formatter used by the OUI Lookup search field. */
class FormatMacInputTest {

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertEquals("", formatMacInput(""))
    }

    @Test
    fun singleHexCharIsUppercased() {
        assertEquals("3", formatMacInput("3"))
        assertEquals("A", formatMacInput("a"))
    }

    @Test
    fun fiveHexCharsGetGroupedAndUppercased() {
        assertEquals("3C:5A:B", formatMacInput("3c5ab"))
    }

    @Test
    fun sixHexCharsFormAFullOuiPrefix() {
        assertEquals("3C:5A:B4", formatMacInput("3c5ab4"))
    }

    @Test
    fun twelveHexCharsFormAFullMac() {
        assertEquals("3C:5A:B4:21:AA:BB", formatMacInput("3c5ab421aabb"))
    }

    @Test
    fun moreThanTwelveHexCharsIsCapped() {
        assertEquals("3C:5A:B4:21:AA:BB", formatMacInput("3c5ab421aabbCCDD"))
    }

    @Test
    fun nonHexSeparatorsAreStripped() {
        assertEquals("3C:5A:B4", formatMacInput("3c-5a-b4"))
        assertEquals("3C:5A:B4", formatMacInput("3c.5a.b4"))
        assertEquals("3C:5A:B4", formatMacInput("3c:5a:b4"))
        assertEquals("3C:5A:B4", formatMacInput("3c 5a b4"))
    }

    @Test
    fun nonHexLettersAreStripped() {
        // "G" is not hex; only the leading "3C" survives.
        assertEquals("3C", formatMacInput("3CGZ"))
    }

    @Test
    fun garbageInputProducesEmptyOutput() {
        // Only chars from G..Z and punctuation; nothing in [0-9A-F] remains.
        assertEquals("", formatMacInput("Hi Mom!"))
    }

    @Test
    fun mixedCaseIsNormalizedToUppercase() {
        assertEquals("AB:CD:EF", formatMacInput("aBcDeF"))
    }

    @Test
    fun reformattingExistingFormattedStringIsStable() {
        assertEquals("3C:5A:B4", formatMacInput("3C:5A:B4"))
    }

    // computeFormattedCursor: cursor must land after the same hex char the user just typed,
    // even when the formatter inserts a colon ahead of it. Locks the cursor-jump regression.
    @Test
    fun cursorMovesPastAutoInsertedColonOnThirdChar() {
        assertEquals("3C:5" to 4, computeFormattedCursor("3C5", 3))
    }

    @Test
    fun cursorPreservedWhenInsertingInMiddle() {
        assertEquals("3C:5A" to 2, computeFormattedCursor("3C5A", 2))
    }

    @Test
    fun backspaceAcrossColonComputesCursorCorrectly() {
        assertEquals("3C" to 2, computeFormattedCursor("3C:", 3))
    }

    @Test
    fun pasteOfFullMacPlacesCursorAtEnd() {
        assertEquals("AA:BB:CC:DD:EE:FF" to 17, computeFormattedCursor("aabbccddeeff", 12))
    }

    @Test
    fun cursorAtStartStaysAtStart() {
        assertEquals("3C:5" to 0, computeFormattedCursor("3C5", 0))
    }

    @Test
    fun emptyInputProducesEmptyAndZeroCursor() {
        assertEquals("" to 0, computeFormattedCursor("", 0))
    }

    @Test
    fun cursorBeyondTextLengthIsClamped() {
        assertEquals("3C" to 2, computeFormattedCursor("3C", 99))
    }

    @Test
    fun pasteOverflowsBeyondTwelveHexCaps() {
        assertEquals(
            "AA:BB:CC:DD:EE:FF" to 17,
            computeFormattedCursor("aabbccddeeff112233", 18),
        )
    }

    @Test
    fun nonHexInterleavedChrsAreStrippedAndCursorTracksHex() {
        assertEquals("3C:5" to 4, computeFormattedCursor("3 C 5", 5))
    }

    @Test
    fun mixedCasePasteWithCursorInMiddleLandsAfterUppercased() {
        assertEquals("AB:CD:EF" to 4, computeFormattedCursor("aBcDeF", 3))
    }

    @Test
    fun negativeCursorIsClampedToZero() {
        assertEquals("3C" to 0, computeFormattedCursor("3C", -5))
    }

    // The TextFieldValue overload must preserve the user's selection range when the formatter
    // does not change the text. Otherwise long-press select-and-drag collapses on every drag tick.
    @Test
    fun textFieldValueOverloadPreservesSelectionWhenFormatIsNoop() {
        val raw = TextFieldValue("3C:5A", TextRange(0, 5))
        val out = formatMacInput(raw)
        assertEquals("3C:5A", out.text)
        assertEquals(TextRange(0, 5), out.selection)
    }

    @Test
    fun textFieldValueOverloadCollapsesSelectionWhenFormatChangesText() {
        val raw = TextFieldValue("3c5", TextRange(0, 3))
        val out = formatMacInput(raw)
        assertEquals("3C:5", out.text)
        assertEquals(TextRange(4), out.selection)
    }
}
