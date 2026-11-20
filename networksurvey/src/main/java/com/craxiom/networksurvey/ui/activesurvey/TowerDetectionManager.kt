package com.craxiom.networksurvey.ui.activesurvey

import android.content.Context
import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.retrofit
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.dao.TowerCacheDao
import com.craxiom.networksurvey.logging.db.model.TowerCacheEntity
import com.craxiom.networksurvey.model.TowerKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val HTTP_OK = 200
private const val HTTP_NO_CONTENT = 204
private const val HTTP_NOT_FOUND = 404

/** How long a cached check result keeps a tower from being asked about again: 48 hours. */
private const val CACHE_RETENTION_MS = 48 * 60 * 60 * 1000L

/** What the tower service said about one tower. */
sealed interface TowerLookup {
    /** The tower service has the tower, at [latitude], [longitude] when it gave a position. */
    data class InDatabase(val latitude: Double?, val longitude: Double?) : TowerLookup

    /** The tower service does not have the tower. */
    data object NotInDatabase : TowerLookup

    /** No answer: a rate limit, a server error or no network. Nothing is known. */
    data object Failed : TowerLookup
}

/**
 * Asks the NS tower service whether it has a tower, and keeps a short-lived local cache of the
 * answers so a tower the phone keeps reconnecting to is not asked about again.
 *
 * Only real answers are cached (200, 204 and 404). A tower the service has (200) is cached as soon
 * as it answers. A tower it does not have (204 or 404) is cached only once the caller has saved
 * its find ([rememberChecked]), so a find that could not be saved is not hidden for
 * [CACHE_RETENTION_MS]. A rate limit, a server error or a network failure is not cached either.
 *
 * This class does not retry. The survey only checks a serving cell when it changes, so a tower
 * whose check failed is asked about again the next time the phone connects to it after being on
 * another cell, or in the next survey; that keeps a service outage from spending the shared API
 * quota on every scan.
 */
class TowerDetectionManager internal constructor(
    private val api: Api,
    private val towerCacheDao: () -> TowerCacheDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(
        retrofit.create(Api::class.java),
        { SurveyDatabase.getInstance(context).towerCacheDao() },
    )

    /**
     * Checks whether a tower is new: not seen in the last [CACHE_RETENTION_MS], and not in the
     * tower database. Never throws.
     *
     * @return True when the tower service does not have the tower; that answer is not cached
     * until [rememberChecked]. False when it does (cached now), when the tower was checked
     * recently, or when the check failed (to avoid false alerts).
     */
    suspend fun checkIfTowerIsNew(key: TowerKey): Boolean = withContext(Dispatchers.IO) {
        try {
            if (towerCacheDao().getTower(key.mcc, key.mnc, key.area, key.cellId) != null) {
                Timber.d("Tower found in cache (checked recently): %s", key)
                return@withContext false
            }

            val lookup = lookUp(key)
            if (lookup is TowerLookup.InDatabase) rememberChecked(key)
            return@withContext lookup == TowerLookup.NotInDatabase
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error checking tower status")
            return@withContext false
        }
    }

    /**
     * Caches [key] as checked, so it is not asked about again for [CACHE_RETENTION_MS]. Call it
     * for a tower the service does not have once its find is saved. Never throws.
     */
    suspend fun rememberChecked(key: TowerKey) = withContext(Dispatchers.IO) {
        try {
            cache(key)
            cleanupOldCacheEntries()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error caching the tower check for %s", key)
        }
    }

    /**
     * Asks the tower service about [key], without the cache. Never throws; a failure is
     * [TowerLookup.Failed].
     */
    suspend fun lookUp(key: TowerKey): TowerLookup = withContext(Dispatchers.IO) {
        try {
            Timber.d("Checking the tower service for %s", key)
            val response = api.checkSingleTower(key.mcc, key.mnc, key.area, key.cellId, key.radio)
            when (val code = response.code()) {
                HTTP_NO_CONTENT, HTTP_NOT_FOUND -> {
                    Timber.i("Tower not in the tower database: %s", key)
                    TowerLookup.NotInDatabase
                }

                HTTP_OK -> {
                    Timber.d("Tower in the tower database: %s", key)
                    val tower = response.body()
                    val located = tower != null && !(tower.lat == 0.0 && tower.lon == 0.0)
                    TowerLookup.InDatabase(tower?.lat?.takeIf { located }, tower?.lon?.takeIf { located })
                }

                else -> {
                    Timber.w("Unexpected response code from the tower check API: %d", code)
                    TowerLookup.Failed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error asking the tower service about %s", key)
            TowerLookup.Failed
        }
    }

    private fun cache(key: TowerKey) {
        val cacheEntry = TowerCacheEntity().apply {
            mcc = key.mcc
            mnc = key.mnc
            area = key.area
            cid = key.cellId
            timestamp = clock()
            radio = key.radio
        }
        towerCacheDao().insert(cacheEntry)
    }

    /** Deletes cached answers older than [CACHE_RETENTION_MS] so the table stays small. */
    private fun cleanupOldCacheEntries() {
        try {
            val deletedCount = towerCacheDao().deleteOldEntries(clock() - CACHE_RETENTION_MS)
            if (deletedCount > 0) {
                Timber.d("Cleaned up %d old tower cache entries", deletedCount)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up tower cache")
        }
    }
}
