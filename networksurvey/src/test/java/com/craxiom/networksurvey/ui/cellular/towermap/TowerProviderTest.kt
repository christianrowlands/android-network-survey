package com.craxiom.networksurvey.ui.cellular.towermap

import com.craxiom.networksurvey.data.api.PlmnRecord
import com.craxiom.networksurvey.data.plmn.PlmnResult
import com.craxiom.networksurvey.data.plmn.secondaryName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tower sheet's provider lookup: which requests it makes for a tower's MCC-MNC, and what a
 * PLMN shared by several names or countries summarizes to.
 */
class TowerProviderTest {

    private val calls = mutableListOf<String>()

    /** A lookup that answers from [known] PLMNs, or with [failure] for every request when set. */
    private fun lookup(vararg known: Pair<String, List<PlmnRecord>>, failure: PlmnResult? = null): suspend (String, String) -> PlmnResult {
        val byPlmn = known.toMap()
        return { mcc, mnc ->
            calls += "$mcc-$mnc"
            failure ?: PlmnResult.Loaded(byPlmn["$mcc-$mnc"].orEmpty(), datasetVersion = null)
        }
    }

    @Test
    fun anExactMatch_isTheProvider_inOneRequest() = runTest {
        val provider = resolveTowerProvider("310", "260", lookup("310-260" to listOf(TMOBILE)))

        assertEquals(TowerProvider.Known(listOf(TMOBILE)), provider)
        assertEquals(listOf("310-260"), calls)
    }

    @Test
    fun aPaddedMnc_isLookedUpAsStored_becauseTheServerMatchesAnyWidth() = runTest {
        val provider = resolveTowerProvider("440", "010", lookup("440-010" to listOf(DOCOMO)))

        assertEquals(TowerProvider.Known(listOf(DOCOMO)), provider)
        assertEquals(listOf("440-010"), calls)
    }

    @Test
    fun aMiss_isNotListed_inOneRequest() = runTest {
        assertEquals(TowerProvider.NotListed, resolveTowerProvider("440", "055", lookup()))
        assertEquals(listOf("440-055"), calls)
    }

    @Test
    fun anInvalidMccOrMnc_isNotListed_withoutARequest() = runTest {
        listOf("0" to "0", "000" to "01", "31" to "260", "310" to "", "310" to "2600", "31A" to "260").forEach { (mcc, mnc) ->
            assertEquals("$mcc-$mnc", TowerProvider.NotListed, resolveTowerProvider(mcc, mnc, lookup()))
        }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun aFailedLookup_isUnavailable_andSaysWhetherItWasTheConnection() = runTest {
        assertEquals(TowerProvider.Unavailable(offline = true), resolveTowerProvider("310", "260", lookup(failure = PlmnResult.Offline)))
        assertEquals(
            TowerProvider.Unavailable(offline = false),
            resolveTowerProvider("310", "260", lookup(failure = PlmnResult.TransientFailure(429)))
        )
    }

    @Test
    fun theCache_answersOnlyWhenItHoldsTheResult() {
        assertNull(cachedTowerProvider("440", "10") { _, _ -> null })
        assertEquals(
            TowerProvider.Known(listOf(DOCOMO)),
            cachedTowerProvider("440", "10") { _, _ -> PlmnResult.Loaded(listOf(DOCOMO), null) }
        )
        assertEquals(TowerProvider.NotListed, cachedTowerProvider("440", "10") { _, _ -> PlmnResult.Loaded(emptyList(), null) })
        assertEquals(TowerProvider.NotListed, cachedTowerProvider("0", "0") { _, _ -> null })
    }

    @Test
    fun aSingleRecord_hasNothingMoreToShow() {
        val provider = TowerProvider.Known(listOf(TMOBILE))

        assertEquals("T-Mobile", provider.name)
        assertEquals(0, provider.otherNameCount)
        assertEquals(1, provider.countryCount)
        assertFalse(provider.hasMore)
    }

    @Test
    fun aPlmnSharedByCountries_countsEachNameAndCountryOnce() {
        val provider = TowerProvider.Known(
            listOf(
                record("338", "050", "Digicel", "JM"),
                record("338", "050", "Digicel Bermuda", "BM"),
                record("338", "050", "digicel", "LC"),
                record("338", "050", "Digicel", "TC"),
            )
        )

        assertEquals("Digicel", provider.name)
        assertEquals(1, provider.otherNameCount)
        assertEquals(4, provider.countryCount)
        assertTrue(provider.hasMore)
    }

    @Test
    fun aPlmnSharedByCountriesUnderOneName_stillHasMoreToShow() {
        val provider = TowerProvider.Known(listOf(record("234", "50", "JT", "JE"), record("234", "50", "JT", "GG")))

        assertEquals(0, provider.otherNameCount)
        assertTrue(provider.hasMore)
    }

    @Test
    fun aRecordWithNoNames_hasNoName() {
        val provider = TowerProvider.Known(listOf(PlmnRecord(mcc = "999", mnc = "01", plmn = "99901", iso = "XX")))

        assertNull(provider.name)
        assertEquals(0, provider.otherNameCount)
    }

    @Test
    fun aFirstRecordWithoutAName_takesTheNameOfTheNextRecord() {
        val provider = TowerProvider.Known(listOf(PlmnRecord(mcc = "338", mnc = "050", plmn = "338050", iso = "JM"), record("338", "050", "Digicel", "BM")))

        assertEquals("Digicel", provider.name)
    }

    @Test
    fun theLegalName_isOnlyShownWhenItIsNotTheBrand() {
        assertEquals("T-Mobile USA", TMOBILE.secondaryName())
        assertNull(TMOBILE.copy(operator = " t-mobile ").secondaryName())
        assertNull(TMOBILE.copy(brand = null).secondaryName())
        assertNull(TMOBILE.copy(operator = "").secondaryName())
    }

    private companion object {
        val TMOBILE = PlmnRecord(
            mcc = "310", mnc = "260", plmn = "310260", country = "United States", iso = "US",
            operator = "T-Mobile USA", brand = "T-Mobile"
        )
        val DOCOMO = PlmnRecord(mcc = "440", mnc = "10", plmn = "44010", country = "Japan", iso = "JP", brand = "NTT docomo")

        fun record(mcc: String, mnc: String, brand: String, iso: String) =
            PlmnRecord(mcc = mcc, mnc = mnc, plmn = mcc + mnc, iso = iso, country = iso, brand = brand)
    }
}
