package com.craxiom.networksurvey.ui.logfiles

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Listing the CSV log files for the Log files page: every file, grouped, and a missing folder. */
class LogFilesRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a folder that does not exist yet is empty, not unreadable`() {
        val listing = LogFilesRepository(File(folder.root, "missing")).list()
        assertEquals(LogFilesListing.Files(emptyList()), listing)
    }

    @Test
    fun `every file is listed, newest survey first, with sizes, and folders are skipped`() {
        val old = file("craxiom-lte-20261006-091204.csv", "a,b\n")
        val new = file("craxiom-wifi-20261007-101500.csv", "a,b,c\n")
        repeat(40) { file("craxiom-gnss-20261001-0800${"%02d".format(it)}.csv", "x") }
        File(folder.root, "subfolder").mkdir()

        val listing = LogFilesRepository(folder.root).list() as LogFilesListing.Files

        assertEquals(42, listing.files.size)
        assertEquals(listOf(new.name), listing.groups[0].files.map { it.name })
        assertEquals(new.length(), listing.groups[0].files[0].sizeBytes)
        assertEquals(listOf(old.name), listing.groups[1].files.map { it.name })
        assertEquals(40, listing.groups[2].files.size)
    }

    /** Written for a second from the time in its name, as a logger would. */
    private fun file(name: String, content: String): File = File(folder.root, name).apply {
        writeText(content)
        setLastModified(LogFileGroups.startedMs(name)!! + 1_000L)
    }
}
