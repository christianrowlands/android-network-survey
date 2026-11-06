package com.craxiom.networksurvey.logging

import android.annotation.SuppressLint
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.scout.raw.Pcapng
import com.craxiom.networksurvey.scout.raw.Radiotap
import com.craxiom.networksurvey.scout.raw.StampedRawFrame
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Writes raw frames to `craxiom-wifi-raw-<time>.pcapng` files in the log files folder, next to the
 * CSV files and named the same way, so the Log files page lists them with the survey's other files.
 *
 * Each file starts with a Section Header, and gets one Interface Description per Scout (named by
 * its Scout ID) before that Scout's first packet; a rollover (the log rollover size, as for the CSV
 * files) starts a new file with its own Section Header and Interface Descriptions, so every file
 * opens on its own in Wireshark. Files are created when the first frame arrives.
 *
 * Frames are written only between [startSession] and [close], so a late batch can never open a
 * file after a stop. Saving stops cleanly, keeping what was written, when the phone's free space
 * falls under [minFreeBytes] or a write fails; [progress] then says why. Not thread safe: one
 * writer thread owns it, never the Scout thread or the service's executor.
 *
 * @param fileTime The time part of a new file's name, in the CSV loggers' format.
 * @param rolloverLimitBytes The size at which a file rolls over to a new one (0 = never); its owner
 * may change it at any time.
 * @param openStream Opens a new file for writing.
 */
// The files go to the shared Downloads folder, where StorageManager.getAllocatableBytes (which
// counts cache the system could clear for this app) does not apply; free space is what is left.
@SuppressLint("UsableSpace")
class PcapngFileWriter(
    private val directory: File,
    private val application: String,
    private val fileTime: () -> String,
    private val freeSpaceBytes: (File) -> Long = { it.usableSpace },
    private val minFreeBytes: Long = MIN_FREE_BYTES,
    private val clockMs: () -> Long = System::currentTimeMillis,
    @Volatile var rolloverLimitBytes: Long = 0L,
    private val openStream: (File) -> OutputStream = ::FileOutputStream,
) {
    private var output: OutputStream? = null
    private var file: File? = null
    private var fileBytes = 0L
    private val interfaces = HashMap<String, Int>()
    private var nextSpaceCheckMs = 0L
    private var sessionOpen = false

    /** What has been saved this session, and whether saving goes on. */
    @Volatile
    var progress = RawFramesProgress()
        private set

    /** Starts a new session: counts from zero, and the next frame opens a new file. */
    fun startSession() {
        closeFile()
        sessionOpen = true
        progress = RawFramesProgress()
    }

    /** Writes [frames] to the current file, opening (or rolling over to) a file as needed. */
    fun write(frames: List<StampedRawFrame>) {
        if (!sessionOpen || frames.isEmpty() || progress.state != RawFramesProgress.State.SAVING) return
        if (output == null && !open()) return
        val batch = encode(frames)
        if (!hasRoomFor(batch.size)) {
            stop(RawFramesProgress.State.STORAGE_FULL)
            return
        }
        try {
            output?.write(batch)
            output?.flush()
        } catch (e: IOException) {
            Timber.e(e, "Could not write raw frames to %s", file?.name)
            stop(if (freeSpace() < minFreeBytes) RawFramesProgress.State.STORAGE_FULL else RawFramesProgress.State.FAILED)
            return
        }
        fileBytes += batch.size
        progress = progress.copy(framesSaved = progress.framesSaved + frames.size, bytesSaved = progress.bytesSaved + batch.size)
        val limit = rolloverLimitBytes
        if (limit > 0 && fileBytes >= limit) {
            Timber.i("Rolling over the raw frames file %s at %d bytes", file?.name, fileBytes)
            closeFile()
        }
    }

    /** Ends the session and closes the current file; nothing more is written until [startSession]. */
    fun close() {
        sessionOpen = false
        closeFile()
    }

    /** Closes the current file; a later frame of the same session opens a new one. */
    private fun closeFile() {
        try {
            output?.close()
        } catch (e: IOException) {
            Timber.w(e, "Could not close the raw frames file")
        }
        output = null
        file = null
        fileBytes = 0L
        interfaces.clear()
    }

    private fun open(): Boolean {
        if (freeSpace() < minFreeBytes) {
            stop(RawFramesProgress.State.STORAGE_FULL)
            return false
        }
        return try {
            directory.mkdirs()
            val target = uniqueFile()
            val stream = openStream(target)
            // Assigned before the header goes out, so the failure path below closes the stream
            output = stream
            val header = Pcapng.sectionHeader(application)
            stream.write(header)
            file = target
            fileBytes = header.size.toLong()
            progress = progress.copy(bytesSaved = progress.bytesSaved + header.size)
            Timber.i("Creating the raw frames file: %s", target.absolutePath)
            true
        } catch (e: IOException) {
            Timber.e(e, "Could not create a raw frames file in %s", directory)
            stop(if (freeSpace() < minFreeBytes) RawFramesProgress.State.STORAGE_FULL else RawFramesProgress.State.FAILED)
            false
        }
    }

    private fun encode(frames: List<StampedRawFrame>): ByteArray {
        val out = ByteArrayOutputStream()
        frames.forEach { stamped ->
            val name = stamped.scoutId.value
            val interfaceId = interfaces[name] ?: interfaces.size.also { id ->
                interfaces[name] = id
                out.write(Pcapng.interfaceDescription(name))
            }
            val radiotap = Radiotap.header(stamped.frame)
            val data = stamped.frame.data.toByteArray()
            out.write(
                Pcapng.enhancedPacket(
                    interfaceId = interfaceId,
                    timestampUs = stamped.timestampUs,
                    packet = radiotap + data,
                    originalLength = radiotap.size + stamped.frame.origLen,
                )
            )
        }
        return out.toByteArray()
    }

    /** Checks the free space at most once a second; between checks a batch is assumed to fit. */
    private fun hasRoomFor(bytes: Int): Boolean {
        val now = clockMs()
        if (now < nextSpaceCheckMs) return true
        nextSpaceCheckMs = now + SPACE_CHECK_INTERVAL_MS
        return freeSpace() - bytes >= minFreeBytes
    }

    private fun freeSpace(): Long = try {
        freeSpaceBytes(if (directory.exists()) directory else directory.parentFile ?: directory)
    } catch (e: SecurityException) {
        Timber.w(e, "Could not read the free space for raw frames")
        Long.MAX_VALUE
    }

    private fun stop(state: RawFramesProgress.State) {
        Timber.w("Raw frames stopped: %s, %d frames saved", state, progress.framesSaved)
        closeFile()
        progress = progress.copy(state = state)
    }

    private fun uniqueFile(): File {
        val time = fileTime()
        var candidate = File(directory, NetworkSurveyConstants.WIFI_RAW_FILE_NAME_PREFIX + time + PCAPNG_EXTENSION)
        var counter = 0
        while (candidate.exists()) {
            counter++
            candidate = File(directory, NetworkSurveyConstants.WIFI_RAW_FILE_NAME_PREFIX + time + "-" + counter + PCAPNG_EXTENSION)
        }
        return candidate
    }

    companion object {
        /** The extension of raw frames files. */
        const val PCAPNG_EXTENSION = ".pcapng"

        /** Saving stops while the phone has less free space than this, so the phone keeps room to work. */
        const val MIN_FREE_BYTES = 100L * 1024 * 1024

        private const val SPACE_CHECK_INTERVAL_MS = 1_000L
    }
}
