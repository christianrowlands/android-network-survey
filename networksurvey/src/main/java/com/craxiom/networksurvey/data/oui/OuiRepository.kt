package com.craxiom.networksurvey.data.oui

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.util.LruCache
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.data.api.retrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * Public entry point for OUI → manufacturer resolution.
 *
 * Concurrency model: [lookup] is a regular `suspend` function. Callers dispatch it inside a
 * `produceState { ... }` (Compose) or any `viewModelScope.launch`. Cancellation of the caller
 * cancels the outstanding lookup correctly through structured concurrency.
 *
 * Cache layering:
 *  - L1 in-memory LRU keyed on the 24-bit OUI prefix. Terminal results live until evicted;
 *    transient results (`OFFLINE`, `TRANSIENT_FAILURE`) carry a 60 s TTL and are cleared on
 *    connectivity restore via [OuiConnectivityMonitor].
 *  - L2 persistent via [OuiCache] (standalone Room DB).
 *
 * The `oui_lookup_enabled` preference is read at every call, flipping the toggle takes effect
 * immediately for new lookups. In-flight requests complete naturally (tiny, < 1 s).
 */
class OuiRepository(
    context: Context,
    private val prefs: SharedPreferences,
    private val cache: OuiCache,
    private val datasetManager: OuiDatasetManager,
    private val coordinator: OuiLookupCoordinator
) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val l1: LruCache<Long, L1Entry> = LruCache(L1_MAX_ENTRIES)

    /**
     * Resolves the manufacturer for a MAC address. Never throws.
     *
     * Ordering:
     *  1. Toggle off → `LOOKUP_DISABLED` (no I/O).
     *  2. Invalid MAC → `UNKNOWN`.
     *  3. Locally administered bit set → `LAA` (no network call - client-side detection).
     *  4. L1 cache hit (non-expired) → return.
     *  5. L2 cache hit → hydrate L1 and return.
     *  6. Metered + Data-Saver → `OFFLINE`.
     *  7. Dataset-version ready → network dispatch → populate caches → return.
     */
    suspend fun lookup(mac: String): OuiResult {
        if (!isEnabled()) return OuiResult.LOOKUP_DISABLED

        val parsed = MacPrefix.parse(mac) ?: return OuiResult.UNKNOWN

        if (MacPrefix.isLocallyAdministered(parsed)) {
            return OuiResult.LAA
        }

        val prefix24 = MacPrefix.maskToPrefix(parsed, MacPrefix.MA_L)

        // L1
        l1.get(prefix24)?.let { entry ->
            if (!entry.isExpired()) return entry.result
            l1.remove(prefix24)
        }

        // L2
        try {
            cache.lookup(parsed)?.let { stored ->
                l1.put(prefix24, L1Entry(stored))
                return stored
            }
        } catch (t: Throwable) {
            Timber.w(t, "OUI L2 cache lookup failed")
        }

        // Metered + Data-Saver: back off without a request
        if (isMeteredUnderDataSaver()) {
            val entry = L1Entry(OuiResult.OFFLINE, System.currentTimeMillis() + TRANSIENT_TTL_MS)
            l1.put(prefix24, entry)
            return OuiResult.OFFLINE
        }

        // Ensure the dataset version check has run at least once this session
        datasetManager.ensureReady()

        val prefixString = MacPrefix.prefix24String(parsed)
        val result = coordinator.lookupHigh(prefixString)

        when (result.status) {
            OuiStatus.OFFLINE, OuiStatus.TRANSIENT_FAILURE -> {
                l1.put(prefix24, L1Entry(result, System.currentTimeMillis() + TRANSIENT_TTL_MS))
            }

            OuiStatus.RESOLVED,
            OuiStatus.PRIVATE,
            OuiStatus.UNKNOWN,
            OuiStatus.SHARED_VENDOR_BLOCK -> {
                l1.put(prefix24, L1Entry(result))
                val version = datasetManager.currentVersion()
                if (version != null) {
                    try {
                        cache.put(prefix24, MacPrefix.MA_L, result, version)
                    } catch (t: Throwable) {
                        Timber.w(t, "OUI L2 cache write failed")
                    }
                }
            }

            OuiStatus.LAA,
            OuiStatus.LOOKUP_DISABLED,
            OuiStatus.LOADING -> {
                // Not expected from the coordinator, but harmless if encountered.
            }
        }

        return result
    }

    /**
     * Clears transient L1 entries (OFFLINE / TRANSIENT_FAILURE) so the next caller re-tries.
     * Invoked by [OuiConnectivityMonitor] on network restoration.
     */
    fun onNetworkAvailable() {
        val snapshot = l1.snapshot()
        for ((key, entry) in snapshot) {
            if (entry.result.status == OuiStatus.OFFLINE ||
                entry.result.status == OuiStatus.TRANSIENT_FAILURE
            ) {
                l1.remove(key)
            }
        }
    }

    private fun isEnabled(): Boolean = prefs.getBoolean(
        NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED,
        NetworkSurveyConstants.DEFAULT_OUI_LOOKUP_ENABLED
    )

    private fun isMeteredUnderDataSaver(): Boolean {
        return try {
            connectivityManager.isActiveNetworkMetered &&
                    connectivityManager.restrictBackgroundStatus ==
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } catch (t: Throwable) {
            false
        }
    }

    private data class L1Entry(
        val result: OuiResult,
        val expiresAtMs: Long = Long.MAX_VALUE
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAtMs
    }

    companion object {
        private const val L1_MAX_ENTRIES = 2_000
        private const val TRANSIENT_TTL_MS = 60_000L

        @Volatile
        private var INSTANCE: OuiRepository? = null

        /**
         * Returns the process-wide [OuiRepository] singleton, building it on first access.
         *
         * Safe to call from any thread. Follows the same double-checked pattern used elsewhere
         * in the app (see [com.craxiom.networksurvey.data.BluetoothCompanyNameProvider] and
         * [OuiCacheDatabase.getInstance]).
         *
         * The [OuiConnectivityMonitor] is registered on first construction so the feature pays
         * no `NetworkCallback` cost until the first WiFi or Bluetooth details screen opens.
         * [INSTANCE] is published *before* the monitor registers its callback so any
         * synchronous-delivery edge case (some OEMs fire `onAvailable` during
         * `registerNetworkCallback`) sees a fully-published singleton.
         *
         * The internal [CoroutineScope] is intentionally process-lifetime, never cancelled.
         */
        @JvmStatic
        fun getInstance(context: Context): OuiRepository {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { repo ->
                    INSTANCE = repo
                    OuiConnectivityMonitor(context.applicationContext) {
                        repo.onNetworkAvailable()
                    }.start()
                }
            }
        }

        private fun build(app: Context): OuiRepository {
            val prefs = PreferenceManager.getDefaultSharedPreferences(app)
            val api = retrofit.create(Api::class.java)
            val cache: OuiCache = OuiCacheRoomImpl(OuiCacheDatabase.getInstance(app).ouiCacheDao())
            val datasetManager = OuiDatasetManager(api, cache, prefs)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val coordinator = OuiLookupCoordinator(api, datasetManager, scope)
            return OuiRepository(app, prefs, cache, datasetManager, coordinator)
        }
    }
}
