package com.craxiom.networksurvey.ui.logfiles

import com.craxiom.networksurvey.services.SurveyRecordProcessor
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * The files one survey wrote. Each logger creates its file when its first record arrives (a
 * neighbor cell or a phone state change can come minutes in) and a rollover starts a new one
 * mid-survey, so a survey is the files whose writing overlaps, not the files with the same name time.
 *
 * @property startedMs  When the first file of the group started.
 * @property files      The group's files, by name (so by record type).
 * @property totalBytes The size of every file in the group.
 */
data class LogFileGroup(val startedMs: Long, val files: List<LogFile>) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/** Reads the start time out of log file names and groups the files by survey. */
object LogFileGroups {

    /** A file started at most this long after the survey's last write still belongs to it. */
    private val SAME_SURVEY_MS = TimeUnit.SECONDS.toMillis(60)

    /** "craxiom-lte-20261006-091204.csv", or "...-091204-1.csv" when the name was taken. */
    private val NAME_TIME = Regex("""(\d{8}-\d{6})(?:-\d+)?\.csv$""")

    /**
     * @return When the file named [name] was started, from the time in its name (written in the
     * phone's zone by the CSV loggers), or null if the name has no time.
     */
    fun startedMs(name: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val time = NAME_TIME.find(name)?.groupValues?.get(1) ?: return null
        return try {
            LocalDateTime.parse(time, SurveyRecordProcessor.DATE_TIME_FORMATTER).atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * @return [files] grouped by survey, newest survey first: in start order, a file joins the
     * current survey when it started before (or within [SAME_SURVEY_MS] after) that survey's last
     * write.
     */
    fun group(files: List<LogFile>): List<LogFileGroup> {
        val groups = mutableListOf<MutableList<LogFile>>()
        var lastWriteMs = Long.MIN_VALUE
        files.sortedBy { it.startedMs }.forEach { file ->
            val current = groups.lastOrNull()
            if (current != null && file.startedMs <= lastWriteMs + SAME_SURVEY_MS) {
                current += file
                lastWriteMs = maxOf(lastWriteMs, file.modifiedMs)
            } else {
                groups += mutableListOf(file)
                lastWriteMs = maxOf(file.startedMs, file.modifiedMs)
            }
        }
        return groups.reversed().map { group -> LogFileGroup(group.first().startedMs, group.sortedBy { it.name }) }
    }
}
