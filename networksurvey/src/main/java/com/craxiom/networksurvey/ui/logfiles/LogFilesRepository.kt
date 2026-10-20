package com.craxiom.networksurvey.ui.logfiles

import android.os.Environment
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import timber.log.Timber
import java.io.File

/**
 * One CSV log file on the phone.
 *
 * @property modifiedMs When it was last written.
 * @property startedMs  When it was started, from the time in its name, or [modifiedMs] without one.
 */
data class LogFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val startedMs: Long = LogFileGroups.startedMs(name) ?: modifiedMs,
)

/** What the log files folder holds, as far as the app can see it. */
sealed interface LogFilesListing {
    /** Every file, grouped by the survey that started it, newest survey first. */
    data class Files(val groups: List<LogFileGroup>) : LogFilesListing {
        /** Every file in every group. */
        val files: List<LogFile> get() = groups.flatMap { it.files }
    }

    /** The folder could not be read (storage unavailable or not permitted). */
    data object Unreadable : LogFilesListing
}

/**
 * Lists the CSV log files every logger writes, grouped by survey. Android only shows an app the files
 * it created, so files from before a reinstall are not listed. Reads storage, so call it off the
 * main thread.
 */
class LogFilesRepository(private val directory: File) {

    /** @return Every file, or [LogFilesListing.Unreadable]. A missing folder is empty. */
    fun list(): LogFilesListing = try {
        if (!directory.exists()) {
            LogFilesListing.Files(emptyList())
        } else {
            val files = directory.listFiles { file -> file.isFile }
            if (files == null) {
                LogFilesListing.Unreadable
            } else {
                LogFilesListing.Files(LogFileGroups.group(files.map { LogFile(it, it.name, it.length(), it.lastModified()) }))
            }
        }
    } catch (e: SecurityException) {
        Timber.w(e, "Could not list the log files")
        LogFilesListing.Unreadable
    }

    /** @return Free space on the volume that holds the folder, or null if unknown. */
    fun freeSpaceBytes(): Long? = try {
        (if (directory.exists()) directory else directory.parentFile ?: directory).usableSpace.takeIf { it > 0 }
    } catch (e: SecurityException) {
        Timber.w(e, "Could not read the free space for log files")
        null
    }

    companion object {
        /** The path people see, relative to the phone's storage. */
        val DISPLAY_PATH = "${Environment.DIRECTORY_DOWNLOADS}/${NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME}"

        /** @return The repository over the folder the CSV loggers write to. */
        fun forLogFiles(): LogFilesRepository = LogFilesRepository(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
            )
        )
    }
}
