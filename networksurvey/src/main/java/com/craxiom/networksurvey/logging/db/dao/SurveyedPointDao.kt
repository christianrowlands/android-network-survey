package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.craxiom.networksurvey.logging.db.model.SurveyedPointCategoryCell
import com.craxiom.networksurvey.logging.db.model.SurveyedPointCell
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.logging.db.model.SurveyedPointKindCount
import kotlinx.coroutines.flow.Flow

private const val BBOX = "latitude BETWEEN :south AND :north AND longitude BETWEEN :west AND :east"
private const val FILTER = "(observedMask & :kinds) != 0 AND time >= :since " +
        "AND (:missionId IS NULL OR missionId = :missionId) AND (source & :sources) != 0"
private const val LATTICE = "CAST((latitude + 90.0) / :step AS INTEGER) AS latKey, " +
        "CAST((longitude + 180.0) / :step AS INTEGER) AS lonKey"

/**
 * DAO for the surveyed places table that backs the "My surveyed places" map layer.
 *
 * The write methods are blocking because the pipelines that call them already run on their own
 * single-thread executors. Every read takes the same kind, time, mission, and source filters
 * (see [SurveyedPointFilter]).
 */
@Dao
interface SurveyedPointDao {
    @Insert
    fun insert(point: SurveyedPointEntity): Long

    /** Every point inside the box with no filters; used by tests and the trim helpers. */
    @Query("SELECT * FROM surveyed_point WHERE $BBOX")
    fun inBounds(
        south: Double,
        west: Double,
        north: Double,
        east: Double
    ): List<SurveyedPointEntity>

    /**
     * Every point inside the box that passes the filters, for the zoomed-in view. The composite
     * (latitude, longitude) index turns this into a latitude range scan with the longitude filter
     * applied in the index.
     */
    @Query("SELECT * FROM surveyed_point WHERE $BBOX AND $FILTER")
    fun inBoundsWhere(
        south: Double, west: Double, north: Double, east: Double,
        kinds: Int, since: Long, missionId: String?, sources: Int,
    ): List<SurveyedPointEntity>

    /** The newest points inside the box, for the tap sheet of a coarse cell. */
    @Query("SELECT * FROM surveyed_point WHERE $BBOX AND $FILTER ORDER BY time DESC LIMIT :limit")
    fun inBoundsRecent(
        south: Double, west: Double, north: Double, east: Double,
        kinds: Int, since: Long, missionId: String?, sources: Int, limit: Int,
    ): List<SurveyedPointEntity>

    @Query("SELECT * FROM surveyed_point WHERE id IN (:ids)")
    fun byIds(ids: List<Long>): List<SurveyedPointEntity>

    /**
     * Points inside the box grouped into a lattice of [step] degrees, for the zoomed-out view.
     * Coordinates are offset to be non-negative before the integer cast so cells never straddle
     * the equator or prime meridian (CAST truncates toward zero). A cell is reported as uploaded
     * only when every row in it is uploaded, and its signal is the best seen.
     */
    @Query(
        "SELECT $LATTICE, AVG(latitude) AS latitude, AVG(longitude) AS longitude, " +
                "COUNT(*) AS count, MIN(uploadedMask) AS minUploadedMask, " +
                "MAX(signalBucket) AS bestBucket, MAX(time) AS lastTime " +
                "FROM surveyed_point WHERE $BBOX AND $FILTER GROUP BY latKey, lonKey"
    )
    fun coarse(
        step: Double, south: Double, west: Double, north: Double, east: Double,
        kinds: Int, since: Long, missionId: String?, sources: Int,
    ): List<SurveyedPointCell>

    /** The most common category value per lattice cell; build the query with [SurveyedPointQueries.dominant]. */
    @RawQuery
    fun coarseDominant(query: SupportSQLiteQuery): List<SurveyedPointCategoryCell>

    /**
     * ORs [bits] into the uploaded mask of every row from [source] observed at or before
     * [beforeTime] whose observed kind matches [observedFilter]. Rows that already carry all of
     * the bits are skipped so repeated uploads do not rewrite the whole table.
     *
     * @return the number of rows changed.
     */
    @Query(
        "UPDATE surveyed_point SET uploadedMask = uploadedMask | :bits " +
                "WHERE source = :source AND time <= :beforeTime " +
                "AND (uploadedMask & :bits) != :bits AND (observedMask & :observedFilter) != 0"
    )
    fun markUploaded(source: Int, beforeTime: Long, bits: Int, observedFilter: Int): Int

    @Query("SELECT COUNT(*) FROM surveyed_point")
    fun count(): Int

    /** Live row count for the layer legend. */
    @Query("SELECT COUNT(*) FROM surveyed_point")
    fun observeCount(): Flow<Int>

    /** Live row count per observed kind, so kinds with nothing to show can be disabled. */
    @Query("SELECT observedMask AS kind, COUNT(*) AS count FROM surveyed_point GROUP BY observedMask")
    fun observeCountByKind(): Flow<List<SurveyedPointKindCount>>

    /** The pipelines that have written rows, so the source filter only appears when it matters. */
    @Query("SELECT DISTINCT source FROM surveyed_point")
    fun observeSources(): Flow<List<Int>>

    /** The kinds observed since [since], for choosing a sensible default kind on the Survey Monitor. */
    @Query("SELECT DISTINCT observedMask FROM surveyed_point WHERE time >= :since")
    fun kindsSince(since: Long): List<Int>

    /**
     * The mission id behind "This survey". Only NS Analytics rolls a mission id; community upload
     * rows carry whatever id was last current, so they never define it.
     */
    @Query(
        "SELECT missionId FROM surveyed_point WHERE source = ${SurveyedPointEntity.SOURCE_NS_ANALYTICS} " +
                "AND missionId IS NOT NULL ORDER BY id DESC LIMIT 1"
    )
    fun observeLatestNsAnalyticsMissionId(): Flow<String?>

    /**
     * Cheap change signal for the map: the max id moves on insert, and Room re-emits on any
     * write to the table including marking, so the layer refreshes without polling.
     */
    @Query("SELECT MAX(id) FROM surveyed_point")
    fun observeMaxId(): Flow<Long?>

    /** Deletes the [limit] oldest rows that have been uploaded somewhere. */
    @Query(
        "DELETE FROM surveyed_point WHERE id IN (SELECT id FROM surveyed_point " +
                "WHERE uploadedMask != 0 ORDER BY id LIMIT :limit)"
    )
    fun trimUploadedOldest(limit: Int): Int

    /** Deletes the [limit] oldest rows regardless of upload state. */
    @Query("DELETE FROM surveyed_point WHERE id IN (SELECT id FROM surveyed_point ORDER BY id LIMIT :limit)")
    fun trimOldest(limit: Int): Int

    @Query("DELETE FROM surveyed_point")
    fun clear()
}
