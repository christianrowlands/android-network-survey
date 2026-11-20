package com.craxiom.networksurvey.logging.db

import android.content.Context
import com.craxiom.networksurvey.logging.db.dao.NewTowerFindDao
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.ui.activesurvey.TowerLookup
import timber.log.Timber

/**
 * The saved towers found not in the tower database, for the new-tower detector and the New towers
 * page. It wraps [NewTowerFindDao] so a database failure is logged instead of thrown into the
 * survey service (the CDR build has no crash reporting to catch it). Every call blocks; run them
 * off the main thread.
 */
class NewTowerFindStore(private val dao: NewTowerFindDao) {

    /** @return The saved find for [key], or null when there is none (or the read failed). */
    fun find(key: TowerKey): NewTowerFindEntity? = guard("read a new tower find", null) {
        dao.find(key.mcc, key.mnc, key.area, key.cellId, key.radio)
    }

    /**
     * Saves [find] as a first find, trimming the list to [MAX_ROWS].
     *
     * @return True when it was saved as a new row; false when the tower already had one (its last
     * sighting is moved forward instead) or the write failed.
     */
    fun recordFind(find: NewTowerFindEntity): Boolean = guard("save a new tower find", false) {
        dao.recordFind(find, MAX_ROWS)
    }

    /** Moves the find's last sighting forward to [seenAt]. */
    fun touch(id: Long, seenAt: Long) {
        guard("update a new tower find", Unit) { dao.updateLastSeen(id, seenAt) }
    }

    /**
     * Records what the tower service said about the find [id] at [checkedAt]. A failed lookup
     * leaves the row as it was, so it is checked again later.
     *
     * @return True when the tower is now known to be in the tower database.
     */
    fun recordLookup(id: Long, lookup: TowerLookup, checkedAt: Long): Boolean = guard("record a tower check", false) {
        when (lookup) {
            is TowerLookup.InDatabase -> {
                dao.markChecked(id, true, checkedAt, lookup.latitude, lookup.longitude)
                true
            }

            TowerLookup.NotInDatabase -> {
                dao.markChecked(id, false, checkedAt, null, null)
                false
            }

            TowerLookup.Failed -> false
        }
    }

    /** @return How many towers the upload session that started at [sessionStartedAt] found. */
    fun countForSession(sessionStartedAt: Long): Int = guard("count new tower finds", 0) {
        dao.countForSession(sessionStartedAt)
    }

    /** @return The [limit] newest finds of the upload session that started at [sessionStartedAt]. */
    fun newestForSession(sessionStartedAt: Long, limit: Int): List<NewTowerFindEntity> =
        guard("read new tower finds", emptyList()) { dao.newestForSession(sessionStartedAt, limit) }

    /**
     * @return Up to [limit] finds, longest unchecked first, that were not in the tower database
     * when last checked and are due a re-check: last checked more than [RECHECK_AGE_MS] before
     * [now].
     */
    fun needingRecheck(now: Long, limit: Int): List<NewTowerFindEntity> =
        guard("read new tower finds to re-check", emptyList()) { dao.needingRecheck(now - RECHECK_AGE_MS, limit) }

    /** Clears the list, so its towers can alert again. */
    fun clearAll() {
        guard("clear the new tower finds", Unit) { dao.clearAll() }
    }

    private inline fun <T> guard(action: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        Timber.e(e, "Failed to %s", action)
        fallback
    }

    companion object {
        /** The most finds the list keeps; past it the oldest first finds are deleted. */
        const val MAX_ROWS = 1000

        /**
         * How old a "not in the tower database" answer gets before the tower is checked again:
         * 12 hours. Shared by the detector and the New towers page.
         */
        const val RECHECK_AGE_MS = 12 * 60 * 60 * 1000L

        /** @return A store over the app's survey database. */
        @JvmStatic
        fun from(context: Context): NewTowerFindStore =
            NewTowerFindStore(SurveyDatabase.getInstance(context).newTowerFindDao())
    }
}
