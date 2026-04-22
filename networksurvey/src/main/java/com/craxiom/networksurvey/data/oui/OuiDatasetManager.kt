package com.craxiom.networksurvey.data.oui

import android.content.SharedPreferences
import com.craxiom.networksurvey.data.api.Api
import com.craxiom.networksurvey.util.NsCrashReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import androidx.core.content.edit

/**
 * Owns the `GET /v2/oui/dataset` call and the client-side dataset-version bookkeeping.
 *
 * Called exactly once per process (via [ensureReady]). The first caller runs the check; all
 * subsequent callers (and concurrent ones) await the same [CompletableDeferred] so no batch
 * dispatches to the server until we know whether the local cache is current.
 *
 * On version mismatch: evicts the entire persistent cache and records the new version.
 *
 * On failure (network / 5xx / registry-not-loaded): logs + swallows. Feature degrades to
 * whatever cached data exists. Retried on the next cold start.
 */
class OuiDatasetManager(
    private val api: Api,
    private val cache: OuiCache,
    private val prefs: SharedPreferences
) {

    private val mutex = Mutex()
    private val readySignal = CompletableDeferred<Unit>()

    @Volatile
    private var ran = false

    /**
     * Blocks the first caller through the dataset check, then lets it (and every subsequent
     * caller) proceed. Never throws, failures are swallowed.
     */
    suspend fun ensureReady() {
        if (readySignal.isCompleted) return
        mutex.withLock {
            if (readySignal.isCompleted) return
            if (ran) {
                readySignal.complete(Unit)
                return
            }
            ran = true
            // Complete the signal regardless of how runCheck() unwinds so cancellation of the
            // first caller doesn't leave later callers waiting forever.
            try {
                runCheck()
            } finally {
                readySignal.complete(Unit)
            }
        }
    }

    private suspend fun runCheck() {
        val response = try {
            api.getOuiDataset()
        } catch (t: Throwable) {
            Timber.w(t, "OUI dataset check failed; proceeding with existing cache")
            return
        }

        if (!response.isSuccessful) {
            Timber.w("OUI dataset check returned HTTP %d", response.code())
            return
        }

        val body = response.body() ?: return
        if (!body.loaded) {
            Timber.w("OUI dataset not loaded on server; skipping eviction")
            return
        }

        val storedVersion = prefs.getString(PREF_DATASET_VERSION, null)
        val currentVersion = body.datasetVersion
        if (currentVersion.isNullOrBlank()) {
            Timber.w("OUI dataset response missing version; skipping eviction")
            return
        }
        NsCrashReporter.setCustomKey(CRASH_KEY_DATASET, currentVersion)

        if (storedVersion == null) {
            prefs.edit { putString(PREF_DATASET_VERSION, currentVersion) }
            return
        }

        if (storedVersion != currentVersion) {
            Timber.i(
                "OUI dataset changed (%s -> %s), evicting cache",
                storedVersion,
                currentVersion
            )
            try {
                cache.evictAll()
                prefs.edit { putString(PREF_DATASET_VERSION, currentVersion) }
            } catch (t: Throwable) {
                Timber.w(t, "Cache eviction failed")
            }
        }
    }

    /**
     * Records a dataset_version observed on a lookup response. Used so the cache is stamped
     * with the authoritative version even if the dataset endpoint itself hasn't been checked
     * yet this session.
     */
    fun rememberVersion(version: String) {
        val stored = prefs.getString(PREF_DATASET_VERSION, null)
        if (stored != version) {
            prefs.edit { putString(PREF_DATASET_VERSION, version) }
        }
    }

    fun currentVersion(): String? = prefs.getString(PREF_DATASET_VERSION, null)

    companion object {
        const val PREF_DATASET_VERSION = "oui_dataset_version"
        private const val CRASH_KEY_DATASET = "oui_dataset_version"
    }
}
