package com.craxiom.networksurvey.ui.cellular.details.model

import android.telephony.TelephonyManager
import com.craxiom.networksurvey.util.TelephonyStateUtils
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode
import com.craxiom.networksurvey.util.TelephonyStateUtils.RegistrationRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The hero line, the pills and the carrier aggregation chips. */
class CellularHeroMapperTest {

    private val none = "None"

    // NetworkRegistrationInfo.DOMAIN_PS and AccessNetworkConstants.TRANSPORT_TYPE_WWAN.
    private val unregisteredRow = RegistrationRow(2, 1, false, TelephonyManager.NETWORK_TYPE_UNKNOWN, "")

    @Test
    fun standalone() {
        val hero = CellularHeroMapper.hero(DetailsRecords.technology(nrMode = NrMode.STANDALONE, baseDataRat = TelephonyManager.NETWORK_TYPE_NR))

        assertEquals(CellularHero(HeroKind.NR_STANDALONE), hero)
        assertEquals(HeroTone.LIVE, hero.tone)
    }

    @Test
    fun nonStandalone() {
        val hero = CellularHeroMapper.hero(DetailsRecords.technology(nrMode = NrMode.NON_STANDALONE))

        assertEquals(HeroKind.NR_NON_STANDALONE, hero.kind)
        assertEquals(HeroTone.LIVE, hero.tone)
    }

    @Test
    fun dataRat_namesTheTechnology() {
        val hero = CellularHeroMapper.hero(DetailsRecords.technology(baseDataRat = TelephonyManager.NETWORK_TYPE_LTE))

        assertEquals(CellularHero(HeroKind.DATA_RAT, "LTE"), hero)
    }

    @Test
    fun noService_isAWarning() {
        val hero = CellularHeroMapper.hero(
            DetailsRecords.technology(baseDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN, rows = listOf(unregisteredRow))
        )

        assertEquals(HeroKind.NO_SERVICE, hero.kind)
        assertEquals(HeroTone.WARNING, hero.tone)
    }

    @Test
    fun unknown_isMuted() {
        val hero = CellularHeroMapper.hero(DetailsRecords.technology(baseDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN))

        assertEquals(HeroKind.UNKNOWN, hero.kind)
        assertEquals(HeroTone.MUTED, hero.tone)
    }

    @Test
    fun pills_showAllThreeOnALiveHero_withBrandingNoneWithoutAnOverride() {
        val pills = CellularHeroMapper.pills(DetailsRecords.technology(), none)

        assertEquals(CellularPills("VoLTE", "LTE", null), pills)
    }

    @Test
    fun pills_brandingShowsTheOverride() {
        // TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
        val pills = CellularHeroMapper.pills(
            DetailsRecords.technology(nrMode = NrMode.NON_STANDALONE, overrideDisplay = "NR NSA", overrideNetworkType = 3),
            none,
        )

        assertEquals("NR NSA", pills!!.branding)
    }

    @Test
    fun pills_hideOnNoServiceWithOnlyNoise() {
        val info = DetailsRecords.technology(
            baseDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN, voice = none, data = "Unknown", rows = listOf(unregisteredRow),
        )

        assertNull(CellularHeroMapper.pills(info, none))
    }

    @Test
    fun pills_stayOnAnUnknownHeroWhenAPillHasAValue() {
        // Wi-Fi calling on Android 8 to 11: the hero is Unknown but the data type is IWLAN.
        val info = DetailsRecords.technology(baseDataRat = TelephonyManager.NETWORK_TYPE_UNKNOWN, voice = none, data = "IWLAN")

        assertEquals(CellularPills(none, "IWLAN", null), CellularHeroMapper.pills(info, none))
    }

    @Test
    fun pills_aMissingVoiceHidesTheRow() {
        assertNull(CellularHeroMapper.pills(DetailsRecords.technology(voice = null), none))
    }

    @Test
    fun carrierAggregation_listsValidCarriersWithTheTotal() {
        val ca = CellularHeroMapper.carrierAggregation(intArrayOf(20_000, 1_400, 0, Int.MAX_VALUE, 100_000))

        assertEquals(CarrierAggregation(listOf("20", "1.4", "100"), "121.4"), ca)
    }

    @Test
    fun carrierAggregation_needsTwoCarriers() {
        assertNull(CellularHeroMapper.carrierAggregation(intArrayOf(20_000)))
        assertNull(CellularHeroMapper.carrierAggregation(null))
    }

    @Test
    fun theDecisionsStayInTelephonyStateUtils() {
        // A guard that the mapper follows deriveHero for an NSA anchor on LTE-CA.
        val info = DetailsRecords.technology(nrMode = NrMode.NON_STANDALONE, baseDataRat = TelephonyStateUtils.NETWORK_TYPE_LTE_CA)

        assertEquals(HeroKind.NR_NON_STANDALONE, CellularHeroMapper.hero(info).kind)
    }
}
