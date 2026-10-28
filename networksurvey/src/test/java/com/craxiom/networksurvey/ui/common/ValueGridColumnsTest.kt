package com.craxiom.networksurvey.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The value grid's column plan: even columns when every value fits, a wide column for a long value
 * with the rest kept even, a column dropped when the values cannot all fit, and even wrapping
 * columns only when nothing fits at the fewest columns.
 */
class ValueGridColumnsTest {

    @Test
    fun valuesThatFitAnEvenShare_getEvenColumns() {
        val plan = planValueGridColumns(needs = listOf(60, 64, 70, 80), available = 412, gap = 4, startColumns = 4, minColumns = 2)

        assertEquals(listOf(100, 100, 100, 100), plan.widths)
    }

    @Test
    fun aLongValue_widensItsColumn_andTheOthersStayEven() {
        // The screenshot's NR row: the frequency (column 3) needs more than an even share.
        val needs = listOf(60, 64, 92, 87, 55, 30, 109, 10)

        val plan = planValueGridColumns(needs, available = 379, gap = 4, startColumns = 4, minColumns = 2)

        assertEquals(listOf(86, 85, 109, 87), plan.widths)
        assertEquals(379 - 3 * 4, plan.widths.sum())
    }

    @Test
    fun valuesThatCannotAllFit_dropAColumn() {
        val plan = planValueGridColumns(needs = listOf(120, 120, 120, 120), available = 379, gap = 4, startColumns = 4, minColumns = 2)

        assertEquals(3, plan.widths.size)
        assertEquals(listOf(124, 124, 123), plan.widths)
    }

    @Test
    fun nothingFitsAtTheFewestColumns_givesEvenColumnsThatWrap() {
        val plan = planValueGridColumns(needs = listOf(300, 300, 300), available = 404, gap = 4, startColumns = 3, minColumns = 2)

        assertEquals(listOf(200, 200), plan.widths)
    }

    @Test
    fun aWideColumn_mayLeaveTheOtherWiderThanItNeeds() {
        val plan = planValueGridColumns(needs = listOf(100, 104), available = 210, gap = 4, startColumns = 2, minColumns = 2)

        assertEquals(listOf(102, 104), plan.widths)
    }
}
