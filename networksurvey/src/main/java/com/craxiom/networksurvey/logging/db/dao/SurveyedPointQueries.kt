package com.craxiom.networksurvey.logging.db.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity

/**
 * The read-side filters every surveyed point query applies: which kinds to include (a bit mask
 * over the {@code OBSERVED_} constants), the oldest time to include, an optional mission, which
 * pipelines (a bit mask over the {@code SOURCE_} constants), and whether to keep only sent or only
 * unsent points. The defaults include everything.
 */
data class SurveyedPointFilter(
    val kinds: Int = SurveyedPointEntity.OBSERVED_ANY,
    val since: Long = 0,
    val missionId: String? = null,
    val sources: Int = SurveyedPointEntity.SOURCE_ANY,
    val uploadState: Int = UPLOAD_ANY,
) {
    companion object {
        /** [uploadState] value meaning "sent or not, keep both". */
        const val UPLOAD_ANY = -1
    }
}

/** The categorical column a coarse "dominant value per lattice cell" query groups by. */
enum class DominantCategory(internal val sql: String) {
    /** Technology rank as text, with a 5G NSA point counted as NR. */
    TECHNOLOGY("CAST(CASE WHEN nrScg = 1 THEN ${SurveyedPointEntity.PROTOCOL_NR} ELSE protocol END AS TEXT)"),
    PROVIDER("plmn"),
    CELL("cellId"),
    AREA("CASE WHEN plmn IS NULL THEN NULL ELSE plmn || '-' || area END"),
}

/**
 * Builds the raw SQL for the coarse queries that need the most common value of a category per
 * lattice cell. Room cannot parameterize the grouped column, and the app's minimum SQLite has no
 * window functions, so the query is a nested GROUP BY: the inner query counts each category in
 * each cell, the outer keeps the row with the highest count (SQLite's bare-column rule for a
 * single MAX() returns that row's other columns). Technology ties go to the newer generation by
 * folding the rank into the score.
 */
object SurveyedPointQueries {
    private const val LATTICE =
        "CAST((latitude + 90.0) / ? AS INTEGER) AS latKey, CAST((longitude + 180.0) / ? AS INTEGER) AS lonKey"
    private const val BBOX = "latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?"
    private const val FILTER =
        "(observedMask & ?) != 0 AND time >= ? AND (? IS NULL OR missionId = ?) AND (source & ?) != 0 " +
                "AND (? < 0 OR (CASE WHEN uploadedMask != 0 THEN 1 ELSE 0 END) = ?)"

    fun dominant(
        category: DominantCategory,
        step: Double,
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        filter: SurveyedPointFilter,
    ): SupportSQLiteQuery {
        val score =
            if (category == DominantCategory.TECHNOLOGY) "count * 10 + CAST(category AS INTEGER)" else "count"
        // SUM() is allowed beside the single MAX(): bare columns still come from the max row
        val sql =
            "SELECT latKey, lonKey, latitude, longitude, count, category, minUploadedMask, bestBucket, lastTime, " +
                    "SUM(count) AS cellCount, MAX($score) AS score " +
                    "FROM (SELECT $LATTICE, ${category.sql} AS category, AVG(latitude) AS latitude, AVG(longitude) AS longitude, " +
                    "COUNT(*) AS count, MIN(uploadedMask) AS minUploadedMask, MAX(signalBucket) AS bestBucket, MAX(time) AS lastTime " +
                    "FROM surveyed_point WHERE $BBOX AND $FILTER GROUP BY latKey, lonKey, category) " +
                    "GROUP BY latKey, lonKey"
        // Positional, and in the same order the placeholders appear in LATTICE, BBOX, then FILTER.
        // Anything inserted mid-clause shifts every argument after it, so append rather than insert.
        val args = arrayOf<Any?>(
            step, step, south, north, west, east,
            filter.kinds, filter.since, filter.missionId, filter.missionId, filter.sources,
            filter.uploadState, filter.uploadState,
        )
        return SimpleSQLiteQuery(sql, args)
    }
}
