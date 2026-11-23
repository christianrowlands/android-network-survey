package com.craxiom.networksurvey.logging.db

import android.content.Context
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.dao.CommunityQueuePlaceDao
import com.craxiom.networksurvey.logging.db.dao.QueuedPlace
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneGate
import com.craxiom.networksurvey.privacy.PrivacyZonePolicy
import com.craxiom.networksurvey.privacy.PrivacyZoneRules
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos

/**
 * Deletes the community upload queue's rows (OpenCelliD and BeaconDB) recorded inside a privacy
 * zone, so a zone also protects what was queued before it existed, along with the unsent community
 * survey points there (so "My survey points" never shows them as sent). The capture gate keeps new rows
 * from being queued inside a zone; this covers the backlog. Rows are deleted, not skipped: a skipped
 * row would be read again by every upload and never cleaned up.
 *
 * The check is the zone rule with the row's own place: distance to the center minus the row's
 * accuracy within the radius ([PrivacyZoneRules.contains]). A box query per table finds the
 * candidates, so only rows near a zone are checked exactly.
 *
 * Runs before every community upload (`NsUploaderWorker`) and whenever the zones change
 * ([watchZoneChanges]). Blocking; call it off the main thread.
 */
class CommunityQueueScrubber(private val database: SurveyDatabase) {
    private val dao: CommunityQueuePlaceDao get() = database.communityQueuePlaceDao()

    /**
     * Deletes every queued row inside any of [zones], sent or not, and the community survey points
     * inside them that are not marked as sent yet. A successful upload marks every waiting
     * community point as sent by time, so a point whose rows were deleted here would otherwise read
     * as sent to OpenCelliD or BeaconDB on the map.
     *
     * @return How many queued rows were deleted (survey points not counted).
     */
    fun scrub(zones: List<PrivacyZone>): Int {
        if (zones.isEmpty()) return 0
        var deleted = 0
        var points = 0
        database.runInTransaction {
            for (table in tables()) {
                val ids = zones.flatMapTo(mutableSetOf()) { zone ->
                    table.near(zone, onlyWaiting = false, ocid = false, beaconDb = false)
                        .filter { inside(zone, it) }
                        .map { it.id }
                }
                ids.chunked(MAX_IDS_PER_DELETE).forEach { deleted += table.delete(it) }
            }
            val pointIds = zones.flatMapTo(mutableSetOf()) { zone ->
                val box = Box.around(zone, 0)
                dao.pendingCommunityPointsNear(box.minLat, box.maxLat, box.minLon, box.maxLon)
                    .filter { inside(zone, it) }
                    .map { it.id }
            }
            pointIds.chunked(MAX_IDS_PER_DELETE).forEach { points += dao.deleteSurveyedPoints(it) }
        }
        if (deleted > 0) Timber.i("Deleted %d queued community records inside a privacy zone", deleted)
        if (points > 0) Timber.i("Deleted %d unsent community survey points inside a privacy zone", points)
        return deleted
    }

    /**
     * @param openCelliD True if OpenCelliD is a selected target.
     * @param beaconDb True if BeaconDB is a selected target.
     * @return How many rows still waiting for a selected target are inside [zone]: the rows saving
     * the zone would keep from being uploaded. Counted the way the Community page counts waiting
     * records.
     */
    fun countWaitingInside(zone: PrivacyZone, openCelliD: Boolean, beaconDb: Boolean): Int {
        if (!openCelliD && !beaconDb) return 0
        return tables().sumOf { table ->
            table.near(zone, onlyWaiting = true, ocid = openCelliD, beaconDb = beaconDb).count { inside(zone, it) }
        }
    }

    private fun inside(zone: PrivacyZone, place: QueuedPlace): Boolean =
        PrivacyZoneRules.contains(zone, place.latitude, place.longitude, place.accuracy.toFloat())

    /** How one queue table is read by place and deleted by id. */
    private class Table(
        val read: (Box, Boolean, Boolean, Boolean) -> List<QueuedPlace>,
        val delete: (List<Long>) -> Int,
    ) {
        fun near(zone: PrivacyZone, onlyWaiting: Boolean, ocid: Boolean, beaconDb: Boolean): List<QueuedPlace> =
            read(Box.around(zone, SLACK_METERS), onlyWaiting, ocid, beaconDb)
    }

    private fun tables(): List<Table> = listOf(
        Table({ b, w, o, d -> dao.gsmNear(b.minLat, b.maxLat, b.minLon, b.maxLon, SLACK_METERS, w, o, d) }, dao::deleteGsm),
        Table({ b, w, o, d -> dao.cdmaNear(b.minLat, b.maxLat, b.minLon, b.maxLon, SLACK_METERS, w, o, d) }, dao::deleteCdma),
        Table({ b, w, o, d -> dao.umtsNear(b.minLat, b.maxLat, b.minLon, b.maxLon, SLACK_METERS, w, o, d) }, dao::deleteUmts),
        Table({ b, w, o, d -> dao.lteNear(b.minLat, b.maxLat, b.minLon, b.maxLon, SLACK_METERS, w, o, d) }, dao::deleteLte),
        Table({ b, w, o, d -> dao.nrNear(b.minLat, b.maxLat, b.minLon, b.maxLon, SLACK_METERS, w, o, d) }, dao::deleteNr),
        // Only BeaconDB takes Wi-Fi
        Table({ b, w, _, d -> dao.wifiNear(b.minLat, b.maxLat, b.minLon, b.maxLon, SLACK_METERS, w, d) }, dao::deleteWifi),
    )

    /**
     * A latitude and longitude box that holds every point within a zone's radius plus [SLACK_METERS]
     * of its center, a little larger than needed. Longitude is left open near a pole or across the
     * antimeridian, where a box would not hold the circle.
     */
    internal data class Box(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
        companion object {
            /** Rounds the box outward a little, so floating point never cuts the circle's edge. */
            private const val MARGIN = 1.01

            /** Past this latitude the longitude span is unbounded for practical purposes. */
            private const val MAX_BOUNDED_LATITUDE = 89.0

            private val METERS_PER_DEGREE = PrivacyZoneRules.distanceMeters(0.0, 0.0, 1.0, 0.0)

            fun around(zone: PrivacyZone, slackMeters: Int): Box {
                val dLat = (zone.radiusMeters + slackMeters) * MARGIN / METERS_PER_DEGREE
                val minLat = zone.latitude - dLat
                val maxLat = zone.latitude + dLat
                val farthestLatitude = abs(zone.latitude) + dLat
                if (farthestLatitude >= MAX_BOUNDED_LATITUDE) return Box(minLat, maxLat, -180.0, 180.0)
                val dLon = dLat / cos(Math.toRadians(farthestLatitude))
                val minLon = zone.longitude - dLon
                val maxLon = zone.longitude + dLon
                if (minLon < -180.0 || maxLon > 180.0) return Box(minLat, maxLat, -180.0, 180.0)
                return Box(minLat, maxLat, minLon, maxLon)
            }
        }
    }

    companion object {
        /**
         * The accuracy the box allows for. The community queue only takes rows of 100 m or better
         * ([DbUploadStore.ACCURACY_THRESHOLD_METERS]); a coarser row is always checked exactly.
         */
        private const val SLACK_METERS = DbUploadStore.ACCURACY_THRESHOLD_METERS

        /** Below SQLite's oldest limit of 999 bound parameters. */
        private const val MAX_IDS_PER_DELETE = 500

        private val zoneWatchStarted = AtomicBoolean(false)

        /**
         * Deletes the queued rows inside the zones in force (none when the organization turned zones
         * off). Called before every community upload. Blocking.
         *
         * @return How many rows were deleted.
         */
        @JvmStatic
        fun scrubActiveZones(context: Context): Int {
            val zones = PrivacyZoneGate.getInstance(context).activeZones()
            if (zones.isEmpty()) return 0
            return CommunityQueueScrubber(SurveyDatabase.getInstance(context)).scrub(zones)
        }

        /**
         * For the zone editor's live line: how many records waiting to upload to the selected
         * community databases are inside [zone] (a zone being drawn, saved or not), so would not be
         * uploaded once it is saved. Runs on the IO dispatcher.
         */
        suspend fun countQueuedInside(context: Context, zone: PrivacyZone): Int = withContext(Dispatchers.IO) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val openCelliD = preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
            )
            val beaconDb = preferences.getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
            )
            CommunityQueueScrubber(SurveyDatabase.getInstance(context)).countWaitingInside(zone, openCelliD, beaconDb)
        }

        /**
         * Scrubs the queue whenever the saved zones change (a zone added, moved or grown), for as
         * long as the process lives. Call once at app start; later calls do nothing.
         */
        @JvmStatic
        fun watchZoneChanges(context: Context) {
            if (!zoneWatchStarted.compareAndSet(false, true)) return
            val appContext = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Every upload scrubs the zones in force first, so only a change needs a pass right away
            PrivacyZoneStore.getInstance(appContext).zones.drop(1).onEach { zones ->
                try {
                    if (PrivacyZonePolicy.isAllowed(appContext)) {
                        CommunityQueueScrubber(SurveyDatabase.getInstance(appContext)).scrub(zones)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Could not remove queued community records inside a privacy zone")
                }
            }.launchIn(scope)
        }
    }
}
