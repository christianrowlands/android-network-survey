package com.craxiom.networksurvey.survey.engine

/**
 * What the most recent survey session amounted to, captured the moment it ended (before its
 * counts are reset). The Survey screen shows it as "Last survey 42 min · 3,118 records" and in the
 * "Survey stopped" message.
 *
 * @property durationMs   How long the session ran.
 * @property totalRecords The records counted across every record type.
 * @property endedAtMs    When it ended, in epoch milliseconds.
 * @property missionId    The session's last Mission ID, so it can still be copied after Stop, or null
 *                        when only the community databases ran (they never roll one).
 */
data class LastSession(val durationMs: Long, val totalRecords: Long, val endedAtMs: Long, val missionId: String? = null)
