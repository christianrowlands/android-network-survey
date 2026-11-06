package com.craxiom.networksurvey.scout.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One record per item per window, the strongest observation, windows cut by capture time. */
class ScoutRecordAggregatorTest {

    private data class Obs(val key: String, val rssi: Int, val timeMs: Long)

    private data class Seen(val key: String, val rssi: Int, val timeMs: Long, val seen: Set<String>)

    private val aggregator = ScoutRecordAggregator<String, Obs>(
        keyOf = { it.key },
        strengthOf = { it.rssi },
        timeOf = { it.timeMs },
        graceMs = 1_000,
    )

    @Test
    fun `keys by item and keeps the strongest observation of the window`() {
        aggregator.add(Obs("a", -70, 1_000), WINDOW)
        aggregator.add(Obs("a", -50, 4_000), WINDOW)
        aggregator.add(Obs("a", -60, 6_000), WINDOW)
        aggregator.add(Obs("b", -80, 2_000), WINDOW)

        val windows = aggregator.drainClosed(11_000)

        assertEquals(1, windows.size)
        assertEquals(listOf(Obs("a", -50, 4_000), Obs("b", -80, 2_000)), windows[0].items)
        assertEquals(0L, windows[0].startMs)
        assertEquals(WINDOW, windows[0].endMs)
    }

    @Test
    fun `absorb folds the window's other observations into the strongest`() {
        // Each observation's "seen" holds what it saw; the record keeps the strongest plus the union
        val merging = ScoutRecordAggregator<String, Seen>(
            keyOf = { it.key },
            strengthOf = { it.rssi },
            timeOf = { it.timeMs },
            graceMs = 1_000,
            absorb = { kept, other -> kept.copy(seen = kept.seen + other.seen) },
        )
        merging.add(Seen("a", -70, 1_000, setOf("probe")), WINDOW)
        merging.add(Seen("a", -50, 4_000, setOf("data")), WINDOW)
        merging.add(Seen("a", -60, 6_000, setOf("null")), WINDOW)

        val item = merging.drainClosed(11_000).single().items.single()

        assertEquals(Seen("a", -50, 4_000, setOf("probe", "data", "null")), item)
    }

    @Test
    fun `a window is drained only once it ended and the grace passed`() {
        aggregator.add(Obs("a", -70, 1_000), WINDOW)

        assertTrue(aggregator.drainClosed(10_500).isEmpty())
        assertEquals(1, aggregator.drainClosed(11_000).size)
    }

    @Test
    fun `a backlog that arrives at once gives one record per item for every window it was heard in`() {
        // Heard over 30 s while out of range, delivered together after the reconnect
        for (time in 0L until 30_000L step 2_000L) aggregator.add(Obs("a", -60, time), WINDOW)

        val windows = aggregator.drainClosed(60_000)

        assertEquals(listOf(0L, 10_000L, 20_000L), windows.map { it.startMs })
        assertTrue(windows.all { it.items.size == 1 })
    }

    @Test
    fun `a late observation of an emitted window is dropped rather than duplicated`() {
        aggregator.add(Obs("a", -70, 1_000), WINDOW)
        aggregator.drainClosed(11_000)

        aggregator.add(Obs("a", -40, 9_000), WINDOW)
        aggregator.add(Obs("b", -40, 9_000), WINDOW)

        val late = aggregator.drainClosed(12_000)
        assertEquals(listOf(Obs("b", -40, 9_000)), late.single().items)
    }

    @Test
    fun `drainAll empties everything, for a pause or a new scan rate`() {
        aggregator.add(Obs("a", -70, 1_000), WINDOW)
        aggregator.add(Obs("a", -70, 15_000), WINDOW)

        assertEquals(2, aggregator.drainAll().size)
        assertTrue(aggregator.drainAll().isEmpty())
    }

    private companion object {
        const val WINDOW = 10_000L
    }
}
