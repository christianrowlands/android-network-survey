package com.craxiom.networksurvey.scout.firmware

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.board.v1.BoardInfo
import timber.log.Timber
import java.util.Base64

/** How the Scout's firmware compares with the latest verified release, for the Scout page. */
sealed interface FirmwareReleaseStatus {
    /** Not known yet: no verified release cached, or none for this board. */
    data object Unknown : FirmwareReleaseStatus

    data object UpToDate : FirmwareReleaseStatus

    data class UpdateAvailable(val version: String) : FirmwareReleaseStatus

    /** The Scout runs a newer version than the latest release (a test build). */
    data object NewerThanRelease : FirmwareReleaseStatus
}

/**
 * The light background check behind "Up to date" and "Update available" on the Scout page: the
 * last verified manifest is kept (with its signature, and checked again on every read), and a new
 * one is fetched at most once a day ([FirmwareConstants.CHECK_INTERVAL_MS]), not on every screen
 * open, while a failed check is tried again on the next open. The firmware update screen always fetches a fresh one.
 */
class FirmwareReleaseCache(
    private val preferences: SharedPreferences,
    private val catalog: FirmwareCatalog,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /**
     * Fetches the manifest when the last successful fetch is more than a day old. Never throws; a
     * failed check doesn't count, so the next open of the Scout page tries again.
     */
    suspend fun refreshIfStale() {
        val checkedAt = preferences.getLong(FirmwareConstants.PREFERENCE_CHECKED_AT, 0L)
        if (nowMs() - checkedAt in 0 until FirmwareConstants.CHECK_INTERVAL_MS) return
        try {
            store(catalog.fetchLatest().second)
            preferences.edit { putLong(FirmwareConstants.PREFERENCE_CHECKED_AT, nowMs()) }
        } catch (e: FirmwareUpdateError) {
            Timber.i("Scout firmware check failed: %s", e)
        }
    }

    /** Keeps a manifest that was just verified. */
    fun store(signed: SignedManifest) = preferences.edit {
        putString(FirmwareConstants.PREFERENCE_CACHED_MANIFEST, Base64.getEncoder().encodeToString(signed.body))
        putString(FirmwareConstants.PREFERENCE_CACHED_SIGNATURE, Base64.getEncoder().encodeToString(signed.signature))
    }

    /** @return The cached release, verified again, or null when there is none or it no longer checks out. */
    fun cached(): VerifiedRelease? {
        val body = preferences.getString(FirmwareConstants.PREFERENCE_CACHED_MANIFEST, null) ?: return null
        val signature = preferences.getString(FirmwareConstants.PREFERENCE_CACHED_SIGNATURE, null) ?: return null
        return try {
            catalog.verify(SignedManifest(Base64.getDecoder().decode(body), Base64.getDecoder().decode(signature)), recordHistory = false)
        } catch (e: FirmwareUpdateError) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        /** @return How the board described by [info] compares with [release]. */
        @JvmStatic
        fun status(release: VerifiedRelease?, info: BoardInfo?): FirmwareReleaseStatus {
            if (release == null || info == null || release.manifest.imageFor(info.boardModel, info.chip) == null) {
                return FirmwareReleaseStatus.Unknown
            }
            return when (FirmwareComparison.of(release.manifest.version, info.firmwareVersion)) {
                FirmwareComparison.NEWER -> FirmwareReleaseStatus.UpdateAvailable(release.manifest.version)
                FirmwareComparison.SAME -> FirmwareReleaseStatus.UpToDate
                FirmwareComparison.OLDER -> FirmwareReleaseStatus.NewerThanRelease
                FirmwareComparison.UNKNOWN -> FirmwareReleaseStatus.Unknown
            }
        }

        /** @return The cache over the app's preferences and the official release manifest. */
        @JvmStatic
        fun create(context: Context): FirmwareReleaseCache = FirmwareReleaseCache(
            PreferenceManager.getDefaultSharedPreferences(context),
            FirmwareCatalogs.official(context),
        )
    }
}

/** The catalog the app uses: the official manifest, the release key and the phone's release history. */
object FirmwareCatalogs {
    /** @return The official catalog. */
    @JvmStatic
    fun official(context: Context): FirmwareCatalog = FirmwareCatalog(
        trust = FirmwareTrust.RELEASE,
        fetcher = OkHttpFirmwareFetcher(),
        history = PreferenceFirmwareReleaseHistory(PreferenceManager.getDefaultSharedPreferences(context)),
    )
}
