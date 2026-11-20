package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import kotlinx.coroutines.flow.Flow

private const val KEY_MATCH = "mcc = :mcc AND mnc = :mnc AND area = :area AND cellId = :cellId AND radio = :radio"

/**
 * DAO for the towers found not in the tower database ([NewTowerFindEntity]).
 *
 * The write and lookup methods are blocking because the new-tower detector already runs them on
 * its own background dispatcher; the page observes the list through the [Flow] reads.
 */
@Dao
abstract class NewTowerFindDao {

    /** @return The new row's id, or -1 when the tower already has a row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertOrIgnore(find: NewTowerFindEntity): Long

    /** @return The tower's row, or null when it has none. */
    @Query("SELECT * FROM new_tower_find WHERE $KEY_MATCH LIMIT 1")
    abstract fun find(mcc: String, mnc: String, area: Int, cellId: Long, radio: String): NewTowerFindEntity?

    /** Moves the row's last sighting forward to [seenAt]; never moves it back. */
    @Query("UPDATE new_tower_find SET lastSeenAt = :seenAt WHERE id = :id AND lastSeenAt < :seenAt")
    abstract fun updateLastSeen(id: Long, seenAt: Long): Int

    /**
     * Inserts [find] as a first find, or when the tower already has a row, only moves that row's
     * last sighting to the new row's [NewTowerFindEntity.lastSeenAt], keeping its first find. Then
     * deletes the oldest finds past [maxRows].
     *
     * @return True when [find] was inserted as a new row.
     */
    @Transaction
    open fun recordFind(find: NewTowerFindEntity, maxRows: Int): Boolean {
        if (insertOrIgnore(find) == -1L) {
            find(find.mcc, find.mnc, find.area, find.cellId, find.radio)?.let { updateLastSeen(it.id, find.lastSeenAt) }
            return false
        }
        val excess = count() - maxRows
        if (excess > 0) deleteOldest(excess)
        return true
    }

    /** Every find, newest first. */
    @Query("SELECT * FROM new_tower_find ORDER BY firstSeenAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<NewTowerFindEntity>>

    /** How many towers the list holds, for the Community databases page. */
    @Query("SELECT COUNT(*) FROM new_tower_find")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM new_tower_find")
    abstract fun count(): Int

    /** How many towers the upload session that started at [sessionStartedAt] found. */
    @Query("SELECT COUNT(*) FROM new_tower_find WHERE sessionStartedAt = :sessionStartedAt")
    abstract fun countForSession(sessionStartedAt: Long): Int

    /** The [limit] newest towers the upload session that started at [sessionStartedAt] found. */
    @Query(
        "SELECT * FROM new_tower_find WHERE sessionStartedAt = :sessionStartedAt " +
                "ORDER BY firstSeenAt DESC, id DESC LIMIT :limit"
    )
    abstract fun newestForSession(sessionStartedAt: Long, limit: Int): List<NewTowerFindEntity>

    /**
     * Up to [limit] towers that were not in the tower database when last checked, and were last
     * checked before [checkedBefore]. The longest unchecked go first, so a run that stops at
     * [limit] reaches the rest on later runs; among towers checked at the same moment, the newest
     * find goes first.
     */
    @Query(
        "SELECT * FROM new_tower_find WHERE inDatabase = 0 AND lastCheckedAt < :checkedBefore " +
                "ORDER BY lastCheckedAt ASC, firstSeenAt DESC, id DESC LIMIT :limit"
    )
    abstract fun needingRecheck(checkedBefore: Long, limit: Int): List<NewTowerFindEntity>

    /**
     * Records a check against the tower service. [towerLatitude] and [towerLongitude] are the
     * tower's position when [inDatabase] and the service gave one, otherwise null.
     */
    @Query(
        "UPDATE new_tower_find SET inDatabase = :inDatabase, lastCheckedAt = :checkedAt, " +
                "towerLatitude = :towerLatitude, towerLongitude = :towerLongitude WHERE id = :id"
    )
    abstract fun markChecked(
        id: Long,
        inDatabase: Boolean,
        checkedAt: Long,
        towerLatitude: Double?,
        towerLongitude: Double?,
    ): Int

    /** Deletes the [limit] finds with the oldest first find. */
    @Query(
        "DELETE FROM new_tower_find WHERE id IN " +
                "(SELECT id FROM new_tower_find ORDER BY firstSeenAt ASC, id ASC LIMIT :limit)"
    )
    abstract fun deleteOldest(limit: Int): Int

    /**
     * Forgets the listed towers' cached check results, so clearing the list lets them alert again
     * instead of waiting out the tower cache. The cache has no radio column, so the match is on
     * MCC, MNC, area and cell ID.
     */
    @Query(
        "DELETE FROM tower_cache WHERE EXISTS (SELECT 1 FROM new_tower_find f WHERE f.mcc = tower_cache.mcc " +
                "AND f.mnc = tower_cache.mnc AND f.area = tower_cache.area AND f.cellId = tower_cache.cid)"
    )
    protected abstract fun deleteCachedChecks(): Int

    @Query("DELETE FROM new_tower_find")
    protected abstract fun deleteAll(): Int

    /** Clears the list, and the cached check results of the towers on it. */
    @Transaction
    open fun clearAll() {
        deleteCachedChecks()
        deleteAll()
    }
}
