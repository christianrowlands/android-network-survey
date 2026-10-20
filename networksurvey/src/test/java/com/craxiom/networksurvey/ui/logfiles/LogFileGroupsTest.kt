package com.craxiom.networksurvey.ui.logfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Reading the start time from log file names and grouping the files by the survey that wrote them. */
class LogFileGroupsTest {

    @Test
    fun `the start time comes from the name, with or without the collision counter`() {
        val expected = LocalDateTime.of(2026, 10, 6, 9, 12, 4).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, LogFileGroups.startedMs("craxiom-lte-20261006-091204.csv", ZoneOffset.UTC))
        assertEquals(expected, LogFileGroups.startedMs("craxiom-lte-20261006-091204-1.csv", ZoneOffset.UTC))
    }

    @Test
    fun `a name without a time has no start time, so the file falls back to when it was written`() {
        assertNull(LogFileGroups.startedMs("notes.csv", ZoneOffset.UTC))
        assertNull(LogFileGroups.startedMs("craxiom-lte-20261399-999999.csv", ZoneOffset.UTC))
        assertEquals(5_000L, LogFile(File("notes.csv"), "notes.csv", 1L, modifiedMs = 5_000L).startedMs)
    }

    @Test
    fun `files whose writing overlaps are one survey, newest survey first, with sizes`() {
        val lte = file("craxiom-lte-a.csv", started = 100 * S, written = 900 * S, size = 3)
        val wifi = file("craxiom-wifi-a.csv", started = 101 * S, written = 900 * S, size = 4)
        val gnss = file("craxiom-gnss-a.csv", started = 150 * S, written = 900 * S, size = 5)
        val older = file("craxiom-lte-b.csv", started = 10 * S, written = 30 * S, size = 1)
        val next = file("craxiom-lte-c.csv", started = 2_000 * S, written = 2_100 * S, size = 2)

        val groups = LogFileGroups.group(listOf(older, wifi, next, lte, gnss))

        assertEquals(listOf(2_000 * S, 100 * S, 10 * S), groups.map { it.startedMs })
        assertEquals(listOf("craxiom-gnss-a.csv", "craxiom-lte-a.csv", "craxiom-wifi-a.csv"), groups[1].files.map { it.name })
        assertEquals(12L, groups[1].totalBytes)
    }

    @Test
    fun `a file created minutes into a survey, or by a rollover, joins that survey`() {
        val wifi = file("craxiom-wifi.csv", started = 0, written = 1_800 * S)
        val nr = file("craxiom-nr.csv", started = 300 * S, written = 600 * S)
        val rollover = file("craxiom-wifi-2.csv", started = 1_800 * S, written = 3_000 * S)
        val phoneState = file("craxiom-phonestate.csv", started = 2_900 * S, written = 2_900 * S)

        val groups = LogFileGroups.group(listOf(wifi, nr, rollover, phoneState))

        assertEquals(1, groups.size)
        assertEquals(0L, groups[0].startedMs)
    }

    @Test
    fun `a file started more than a minute after the last write is a new survey`() {
        val first = file("a.csv", started = 0, written = 100 * S)
        val second = file("b.csv", started = 161 * S, written = 200 * S)
        assertEquals(2, LogFileGroups.group(listOf(first, second)).size)
        val close = file("c.csv", started = 160 * S, written = 200 * S)
        assertEquals(1, LogFileGroups.group(listOf(first, close)).size)
    }

    private fun file(name: String, started: Long, written: Long, size: Long = 1L) = LogFile(File(name), name, size, written, started)

    private companion object {
        const val S = 1_000L
    }
}
