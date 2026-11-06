package com.craxiom.networksurvey.logging

import com.craxiom.networksurvey.scout.raw.Pcapng
import com.craxiom.networksurvey.scout.raw.RawFrameTestData
import com.craxiom.networksurvey.scout.raw.RawFrameTestData.FRAMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * The raw frames file: one Section Header per file, an Interface Description per Scout before its
 * first packet, a new file (with its own headers) on rollover, and a clean stop when storage runs out.
 */
class PcapngFileWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var freeBytes = Long.MAX_VALUE
    private var nowMs = 0L

    private fun writer(directory: File = folder.root, rolloverBytes: Long = 0L) = PcapngFileWriter(
        directory = directory,
        application = "Network Survey Android test",
        rolloverLimitBytes = rolloverBytes,
        fileTime = { "20261102-180300" },
        freeSpaceBytes = { freeBytes },
        clockMs = { nowMs },
    )

    private fun files(): List<File> = folder.root.listFiles().orEmpty().sortedBy { it.name }

    @Test
    fun `nothing is created before the first frame`() {
        val writer = writer()
        writer.startSession()
        writer.write(emptyList())
        writer.close()
        assertTrue(files().isEmpty())
    }

    @Test
    fun `a file has a section header, one interface per Scout and a packet per frame`() {
        val writer = writer()
        writer.startSession()
        writer.write(FRAMES)
        writer.close()

        val file = files().single()
        assertEquals("craxiom-wifi-raw-20261102-180300.pcapng", file.name)
        val bytes = file.readBytes()
        val blocks = RawFrameTestData.blocks(bytes)
        assertEquals(
            listOf(Pcapng.SECTION_HEADER_BLOCK, 1, 6, 1, 6, 6),
            blocks.map { it.first },
        )
        blocks.forEach { (_, offset, length) ->
            assertEquals(0, length % 4)
            assertEquals(length, RawFrameTestData.u32(bytes, offset + length - 4))
        }
        assertEquals(bytes.size, blocks.last().second + blocks.last().third)
        // The third packet is on the first Scout's interface again
        assertEquals(0, RawFrameTestData.u32(bytes, blocks[5].second + 8))
        assertEquals(1, RawFrameTestData.u32(bytes, blocks[4].second + 8))
        assertEquals(RawFramesProgress(framesSaved = 3, bytesSaved = bytes.size.toLong()), writer.progress)
    }

    @Test
    fun `a rollover starts a new file with its own section header and interfaces`() {
        val writer = writer(rolloverBytes = 1)
        writer.startSession()
        writer.write(FRAMES.take(1))
        writer.write(FRAMES.drop(1))
        writer.close()

        assertEquals(2, files().size)
        val first = File(folder.root, "craxiom-wifi-raw-20261102-180300.pcapng")
        val second = File(folder.root, "craxiom-wifi-raw-20261102-180300-1.pcapng")
        assertEquals(listOf(Pcapng.SECTION_HEADER_BLOCK, 1, 6), RawFrameTestData.blocks(first.readBytes()).map { it.first })
        // Both Scouts are declared again in the new file
        assertEquals(listOf(Pcapng.SECTION_HEADER_BLOCK, 1, 6, 1, 6), RawFrameTestData.blocks(second.readBytes()).map { it.first })
        assertEquals(3, writer.progress.framesSaved)
        assertEquals(first.length() + second.length(), writer.progress.bytesSaved)
    }

    @Test
    fun `with storage nearly full no file is created and the state says so`() {
        freeBytes = PcapngFileWriter.MIN_FREE_BYTES - 1
        val writer = writer()
        writer.startSession()
        writer.write(FRAMES)
        assertTrue(files().isEmpty())
        assertEquals(RawFramesProgress.State.STORAGE_FULL, writer.progress.state)
    }

    @Test
    fun `storage running out mid survey stops saving and keeps the file so far`() {
        val writer = writer()
        writer.startSession()
        writer.write(FRAMES.take(1))
        val size = files().single().length()

        freeBytes = PcapngFileWriter.MIN_FREE_BYTES
        nowMs += 2_000
        writer.write(FRAMES.drop(1))
        assertEquals(RawFramesProgress.State.STORAGE_FULL, writer.progress.state)
        assertEquals(1, writer.progress.framesSaved)
        assertEquals(size, files().single().length())

        // Stays stopped, even with room again, until a new session
        freeBytes = Long.MAX_VALUE
        nowMs += 2_000
        writer.write(FRAMES)
        assertEquals(1, writer.progress.framesSaved)
        writer.startSession()
        assertEquals(RawFramesProgress(), writer.progress)
    }

    @Test
    fun `a folder that can't be created stops saving as failed`() {
        val blocker = folder.newFile("not-a-folder")
        val writer = writer(directory = File(blocker, "csv"))
        writer.startSession()
        writer.write(FRAMES)
        assertEquals(RawFramesProgress.State.FAILED, writer.progress.state)
        assertFalse(File(blocker, "csv").exists())
    }

    @Test
    fun `a batch that arrives after the close opens no new file`() {
        val writer = writer()
        writer.startSession()
        writer.write(FRAMES.take(1))
        writer.close()
        val saved = writer.progress.framesSaved
        val size = files().single().length()

        writer.write(FRAMES.drop(1))

        assertEquals(1, files().size)
        assertEquals(size, files().single().length())
        assertEquals(saved, writer.progress.framesSaved)

        // A new session writes again
        writer.startSession()
        writer.write(FRAMES)
        writer.close()
        assertEquals(2, files().size)
    }

    @Test
    fun `nothing is written before a session starts`() {
        writer().write(FRAMES)
        assertTrue(files().isEmpty())
    }

    @Test
    fun `a stream whose header can't be written is closed and saving stops as failed`() {
        var closed = false
        val failing = object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("disk error")
            }

            override fun close() {
                closed = true
            }
        }
        val writer = PcapngFileWriter(
            directory = folder.root,
            application = "Network Survey Android test",
            fileTime = { "20261102-180300" },
            freeSpaceBytes = { freeBytes },
            openStream = { failing },
        )
        writer.startSession()
        writer.write(FRAMES)

        assertTrue(closed)
        assertEquals(RawFramesProgress.State.FAILED, writer.progress.state)
    }

    /** Wireshark's own readers accept the file and decode the radiotap fields, as on iOS. */
    @Test
    fun `tshark and capinfos read the file`() {
        val tshark = RawFrameTestData.tool("tshark")
        val capinfos = RawFrameTestData.tool("capinfos")
        assumeTrue("Wireshark is not installed", tshark != null && capinfos != null)
        val writer = writer()
        writer.startSession()
        writer.write(FRAMES)
        writer.close()
        val path = files().single().absolutePath

        val info = RawFrameTestData.run(capinfos!!, "-M", path)
        assertTrue(info, info.contains("Number of packets:   3") || info.contains("Number of packets = 3"))
        assertTrue(info, info.contains("IEEE 802.11 plus radiotap radio header"))
        assertTrue(info, info.contains("Number of interfaces in file: 2"))

        val rows = RawFrameTestData.run(
            tshark!!, "-r", path, "-T", "fields", "-E", "separator=|",
            "-e", "frame.interface_name", "-e", "radiotap.dbm_antsignal", "-e", "radiotap.dbm_antnoise",
            "-e", "radiotap.channel.freq", "-e", "radiotap.datarate", "-e", "radiotap.mcs.index",
            "-e", "wlan.ssid", "-e", "wlan.bssid", "-e", "frame.time_epoch",
        ).trim().lines().map { it.split("|") }
        assertEquals(3, rows.size)
        assertEquals(listOf("NS-1A2B", "-52", "-95", "2437", "1", "", RawFrameTestData.SSID_HEX, "02:00:5e:00:00:01"), rows[0].take(8))
        assertTrue(rows[0][8], rows[0][8].startsWith("1792000987.123456"))
        assertEquals(listOf("NS-3C4D", "-60", "", "5180", "", "7", RawFrameTestData.SSID_HEX, "02:00:5e:00:00:01"), rows[1].take(8))
        assertEquals("NS-1A2B", rows[2][0])

        // No malformed packets or expert errors
        val expert = RawFrameTestData.run(tshark, "-r", path, "-Y", "_ws.malformed || _ws.expert.severity >= error")
        assertTrue(expert, expert.isBlank())
    }
}
