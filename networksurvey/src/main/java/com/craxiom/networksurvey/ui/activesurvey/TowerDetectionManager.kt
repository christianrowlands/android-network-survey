package com.craxiom.networksurvey.ui.activesurvey

import android.content.Context
import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.retrofit
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.dao.TowerCacheDao
import com.craxiom.networksurvey.logging.db.model.TowerCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages tower detection by checking the backend API and maintaining a local cache.
 */
class TowerDetectionManager(
    private val context: Context
) {
    private val api: Api = retrofit.create(Api::class.java)
    private val towerCacheDao: TowerCacheDao by lazy {
        SurveyDatabase.getInstance(context).towerCacheDao()
    }

    // Cache cleanup interval - 48 hours
    private val CACHE_RETENTION_MS = 48 * 60 * 60 * 1000L

    /**
     * Check if a tower is new (not seen before by us AND not in the backend database).
     *
     * @param mcc Mobile Country Code
     * @param mnc Mobile Network Code
     * @param area TAC/LAC
     * @param cid Cell ID
     * @param radio Radio technology (LTE, NR, GSM, UMTS)
     * @return true if the tower is new (not seen before AND not in backend), false otherwise
     */
    suspend fun checkIfTowerIsNew(
        mcc: Int,
        mnc: Int,
        area: Int,
        cid: Long,
        radio: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // If tower is in cache, we've seen it before - no alert needed
            val cachedTower = towerCacheDao.getTower(mcc, mnc, area, cid)
            if (cachedTower != null) {
                Timber.d("Tower found in cache (seen before): MCC=$mcc, MNC=$mnc, Area=$area, CID=$cid")
                return@withContext false
            }

            // Not in cache - this is our first time seeing this tower
            // Check backend API to see if it's a new discovery
            Timber.d("Tower not in cache, checking backend: MCC=$mcc, MNC=$mnc, Area=$area, CID=$cid, Radio=$radio")
            val response = api.checkSingleTower(mcc, mnc, area, cid, radio)

            val isNew = when (response.code()) {
                204, 404 -> {
                    // Tower not found in backend - it's a new discovery!
                    Timber.i("New tower discovered: MCC=$mcc, MNC=$mnc, Area=$area, CID=$cid, Radio=$radio")
                    true
                }

                200 -> {
                    // Tower exists in backend - not a new discovery
                    Timber.d("Tower exists in backend: MCC=$mcc, MNC=$mnc, Area=$area, CID=$cid, Radio=$radio")
                    false
                }

                else -> {
                    Timber.w("Unexpected response code from tower check API: ${response.code()}")
                    false // Assume known on error to avoid false positives
                }
            }

            // Cache the tower (regardless of whether it's new or not - we've now seen it)
            val cacheEntry = TowerCacheEntity().apply {
                this.mcc = mcc
                this.mnc = mnc
                this.area = area
                this.cid = cid
                this.timestamp = System.currentTimeMillis()
                this.radio = radio
            }
            towerCacheDao.insert(cacheEntry)

            // Cleanup old cache entries
            cleanupOldCacheEntries()

            return@withContext isNew

        } catch (e: Exception) {
            Timber.e(e, "Error checking tower status")
            // On error, assume tower is known to avoid false positives
            return@withContext false
        }
    }

    /**
     * Load towers for a specific area into the cache.
     * This can be used to pre-populate the cache to reduce API calls.
     */
    suspend fun preloadAreaTowers(
        mcc: Int,
        mnc: Int,
        area: Int,
        bbox: String,
        radio: String
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.d("Preloading towers for area: MCC=$mcc, MNC=$mnc, Area=$area")
            val response = api.getTowers(bbox, radio, mcc, mnc, "ns")

            if (response.isSuccessful) {
                response.body()?.cells?.forEach { tower ->
                    if (tower.area == area) {
                        val cacheEntry = TowerCacheEntity().apply {
                            this.mcc = tower.mcc
                            this.mnc = tower.mnc
                            this.area = tower.area
                            this.cid = tower.cid
                            this.timestamp = System.currentTimeMillis()
                            this.radio = tower.radio
                        }
                        towerCacheDao.insert(cacheEntry)
                    }
                }
                Timber.d("Preloaded ${response.body()?.cells?.size ?: 0} towers for area")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error preloading area towers")
        }
    }

    /**
     * Clean up old cache entries to prevent database growth.
     */
    private suspend fun cleanupOldCacheEntries() {
        try {
            val cutoffTime = System.currentTimeMillis() - CACHE_RETENTION_MS
            val deletedCount = towerCacheDao.deleteOldEntries(cutoffTime)
            if (deletedCount > 0) {
                Timber.d("Cleaned up $deletedCount old tower cache entries")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up tower cache")
        }
    }

    /**
     * Clear all cache entries.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            towerCacheDao.clearCache()
            Timber.d("Tower cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing tower cache")
        }
    }
}