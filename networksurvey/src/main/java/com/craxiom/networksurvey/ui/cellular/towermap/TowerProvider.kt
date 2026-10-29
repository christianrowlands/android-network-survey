package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.plmn.PlmnRepository
import com.craxiom.networksurvey.data.plmn.PlmnResult
import com.craxiom.networksurvey.data.plmn.displayName

/**
 * Who runs a tower's network, as the tower sheet shows it: looked up from the tower's MCC-MNC
 * with the same PLMN data the PLMN lookup screen uses.
 */
@Immutable
sealed interface TowerProvider {

    /** The lookup has not answered yet. */
    data object Loading : TowerProvider

    /**
     * The PLMN is in the dataset. [records] has more than one entry for a PLMN shared by several
     * operators or countries, in the server's order, so the first one names the provider.
     */
    data class Known(val records: List<PlmnRecord>) : TowerProvider {
        private val names = records.mapNotNull { it.displayName()?.trim() }.distinctBy { it.lowercase() }

        /** The provider's brand (or operator) name, or null when no record has either. */
        val name: String? = names.firstOrNull()

        /** How many other provider names share this PLMN, for the "+1" badge. */
        val otherNameCount: Int = (names.size - 1).coerceAtLeast(0)

        /** How many countries share this PLMN; more than one reads as "Shared by N countries". */
        val countryCount: Int = records.mapNotNull { it.countryKey() }.distinct().size

        /** True when the sheet can expand to list names or countries the summary leaves out. */
        val hasMore: Boolean = otherNameCount > 0 || countryCount > 1
    }

    /** The PLMN is not in the dataset, or the tower's MCC-MNC is not a valid one. */
    data object NotListed : TowerProvider

    /** The lookup could not answer, so the sheet offers to try again. */
    data class Unavailable(val offline: Boolean) : TowerProvider
}

/**
 * Looks up the provider for a tower's [mcc]-[mnc] with [resolve]. The server matches the MNC at
 * any width (a tower stored as 440-010 finds NTT docomo 440-10), so one request answers.
 */
internal suspend fun resolveTowerProvider(
    mcc: String,
    mnc: String,
    resolve: suspend (String, String) -> PlmnResult
): TowerProvider = lookUpTowerProvider(mcc, mnc) { m, n -> resolve(m, n) } ?: TowerProvider.Unavailable(offline = false)

/**
 * @return The provider [cached] already knows for [mcc]-[mnc], or null when finding out needs a
 * request.
 */
internal fun cachedTowerProvider(
    mcc: String,
    mnc: String,
    cached: (String, String) -> PlmnResult?
): TowerProvider? = lookUpTowerProvider(mcc, mnc, cached)

/**
 * The provider for a tower's [mcc]-[mnc]: the cached answer when there is one, else the loading
 * state until the lookup answers. Changing [attempt] looks it up again after a failure.
 */
@Composable
internal fun rememberTowerProvider(mcc: String, mnc: String, attempt: Int = 0): TowerProvider {
    val repository = remember { PlmnRepository.getInstance() }
    val cached = remember(mcc, mnc, attempt) { cachedTowerProvider(mcc, mnc, repository::cachedResolve) }
    val provider by produceState(cached ?: TowerProvider.Loading, mcc, mnc, attempt) {
        value = cached ?: TowerProvider.Loading
        if (cached == null) value = resolveTowerProvider(mcc, mnc, repository::resolve)
    }
    return provider
}

/**
 * Shared by the network and cache lookups: an inline [resolve] may suspend when called from
 * [resolveTowerProvider] and returns null from [cachedTowerProvider] when the cache has no answer.
 */
private inline fun lookUpTowerProvider(
    mcc: String,
    mnc: String,
    resolve: (String, String) -> PlmnResult?
): TowerProvider? {
    if (!isValidPlmn(mcc, mnc)) return TowerProvider.NotListed
    return resolve(mcc, mnc)?.toTowerProvider()
}

private fun isValidPlmn(mcc: String, mnc: String): Boolean =
    mcc.length == MCC_LENGTH && mcc.all(Char::isDigit) && mcc != UNSET_MCC &&
            mnc.length in 1..MAX_MNC_LENGTH && mnc.all(Char::isDigit)

private fun PlmnResult.toTowerProvider(): TowerProvider = when (this) {
    is PlmnResult.Loaded -> if (records.isEmpty()) TowerProvider.NotListed else TowerProvider.Known(records)
    PlmnResult.Offline -> TowerProvider.Unavailable(offline = true)
    is PlmnResult.TransientFailure -> TowerProvider.Unavailable(offline = false)
    PlmnResult.Idle, PlmnResult.Loading -> TowerProvider.Loading
}

/** The country a record belongs to, so records in the same country count once. */
private fun PlmnRecord.countryKey(): String? =
    iso?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: country?.trim()?.takeIf { it.isNotEmpty() }

private const val MCC_LENGTH = 3
private const val MAX_MNC_LENGTH = 3
private const val UNSET_MCC = "000"
