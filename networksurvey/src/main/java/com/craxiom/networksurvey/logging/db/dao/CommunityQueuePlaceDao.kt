package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity

/**
 * A queued community row reduced to where it was recorded, for the privacy zone checks.
 *
 * @property accuracy The fix's accuracy in whole meters; 0 when unknown.
 */
data class QueuedPlace(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Int,
)

/**
 * The rows near a box, or with an accuracy too coarse for the box to rule them out: a cheap
 * prefilter, so only those rows get the exact privacy zone check in Kotlin.
 */
private const val NEAR = "(accuracy > :slackMeters OR (latitude BETWEEN :minLat AND :maxLat " +
    "AND longitude BETWEEN :minLon AND :maxLon))"

/** Only rows still waiting for a target in play, when [onlyWaiting] is set (see [SurveyRecordDao]). */
private const val CELL_WAITING = "(NOT :onlyWaiting OR (:ocid AND ocidUploaded = 0) OR (:beaconDb AND beaconDbUploaded = 0))"

/** The Wi-Fi version of [CELL_WAITING]: only BeaconDB takes Wi-Fi. */
private const val WIFI_WAITING = "(NOT :onlyWaiting OR (:beaconDb AND beaconDbUploaded = 0))"

private const val PLACE = "SELECT id, latitude, longitude, accuracy FROM"

/**
 * Finds and deletes the community upload queue's rows (OpenCelliD and BeaconDB) by place, and the
 * community survey points still waiting with them, for the privacy zone scrub
 * ([com.craxiom.networksurvey.logging.db.CommunityQueueScrubber]). Reads only the place columns,
 * so a large queue stays cheap to check. No schema of its own.
 *
 * Every read takes a latitude and longitude box plus a [slackMeters]: a row whose accuracy is above
 * the slack is always returned, because its accuracy may reach the zone from outside the box.
 */
@Dao
interface CommunityQueuePlaceDao {
    @Query("$PLACE gsm_survey_records WHERE $NEAR AND $CELL_WAITING")
    fun gsmNear(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, slackMeters: Int,
        onlyWaiting: Boolean, ocid: Boolean, beaconDb: Boolean,
    ): List<QueuedPlace>

    @Query("$PLACE cdma_survey_records WHERE $NEAR AND $CELL_WAITING")
    fun cdmaNear(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, slackMeters: Int,
        onlyWaiting: Boolean, ocid: Boolean, beaconDb: Boolean,
    ): List<QueuedPlace>

    @Query("$PLACE umts_survey_records WHERE $NEAR AND $CELL_WAITING")
    fun umtsNear(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, slackMeters: Int,
        onlyWaiting: Boolean, ocid: Boolean, beaconDb: Boolean,
    ): List<QueuedPlace>

    @Query("$PLACE lte_survey_records WHERE $NEAR AND $CELL_WAITING")
    fun lteNear(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, slackMeters: Int,
        onlyWaiting: Boolean, ocid: Boolean, beaconDb: Boolean,
    ): List<QueuedPlace>

    @Query("$PLACE nr_survey_records WHERE $NEAR AND $CELL_WAITING")
    fun nrNear(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, slackMeters: Int,
        onlyWaiting: Boolean, ocid: Boolean, beaconDb: Boolean,
    ): List<QueuedPlace>

    @Query("$PLACE wifi_survey_records WHERE $NEAR AND $WIFI_WAITING")
    fun wifiNear(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, slackMeters: Int,
        onlyWaiting: Boolean, beaconDb: Boolean,
    ): List<QueuedPlace>

    /**
     * The community survey points ("My survey points") not yet marked as sent anywhere, inside a
     * box. A point has no accuracy of its own, so it is read as 0.
     */
    @Query(
        "SELECT id, latitude, longitude, 0 AS accuracy FROM surveyed_point " +
            "WHERE source = ${SurveyedPointEntity.SOURCE_COMMUNITY} AND uploadedMask = 0 " +
            "AND latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon"
    )
    fun pendingCommunityPointsNear(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<QueuedPlace>

    @Query("DELETE FROM surveyed_point WHERE id IN (:ids)")
    fun deleteSurveyedPoints(ids: List<Long>): Int

    @Query("DELETE FROM gsm_survey_records WHERE id IN (:ids)")
    fun deleteGsm(ids: List<Long>): Int

    @Query("DELETE FROM cdma_survey_records WHERE id IN (:ids)")
    fun deleteCdma(ids: List<Long>): Int

    @Query("DELETE FROM umts_survey_records WHERE id IN (:ids)")
    fun deleteUmts(ids: List<Long>): Int

    @Query("DELETE FROM lte_survey_records WHERE id IN (:ids)")
    fun deleteLte(ids: List<Long>): Int

    @Query("DELETE FROM nr_survey_records WHERE id IN (:ids)")
    fun deleteNr(ids: List<Long>): Int

    @Query("DELETE FROM wifi_survey_records WHERE id IN (:ids)")
    fun deleteWifi(ids: List<Long>): Int
}
