package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import com.craxiom.networksurvey.util.TelephonyStateUtils.HeroResult;
import com.craxiom.networksurvey.util.TelephonyStateUtils.HeroState;
import com.craxiom.networksurvey.util.TelephonyStateUtils.NrMode;
import com.craxiom.networksurvey.util.TelephonyStateUtils.RegistrationRow;
import com.craxiom.networksurvey.util.TelephonyStateUtils.VoiceBearer;
import com.craxiom.networksurvey.util.TelephonyStateUtils.VoiceBearerResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Host-side unit tests for the pure cellular technology derivations. These reference only
 * compile-time-inlined telephony constants, so they run on the JVM without Robolectric.
 */
public class TelephonyStateUtilsTest
{
    private static RegistrationRow row(int domain, int transport, boolean registered, int rat)
    {
        return new RegistrationRow(domain, transport, registered, rat, "310260");
    }

    private static RegistrationRow cs(boolean registered, int rat)
    {
        return row(NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN, registered, rat);
    }

    private static RegistrationRow csPs(boolean registered, int rat)
    {
        return row(NetworkRegistrationInfo.DOMAIN_CS_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN, registered, rat);
    }

    private static RegistrationRow psWwan(boolean registered, int rat)
    {
        return row(NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN, registered, rat);
    }

    private static RegistrationRow psWlan(boolean registered, int rat)
    {
        return row(NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WLAN, registered, rat);
    }

    @Test
    public void voiceBearer_wifiCalling_capturedCase()
    {
        // The captured Pixel/T-Mobile state: voice CS unregistered, Wi-Fi calling active, data on NR.
        List<RegistrationRow> rows = Arrays.asList(
                psWlan(true, TelephonyManager.NETWORK_TYPE_IWLAN),
                cs(false, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWwan(true, TelephonyManager.NETWORK_TYPE_NR));

        assertEquals(VoiceBearer.WIFI_CALLING, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_vonr_saNoCsNoWifi()
    {
        List<RegistrationRow> rows = Arrays.asList(
                cs(false, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWwan(true, TelephonyManager.NETWORK_TYPE_NR));

        assertEquals(VoiceBearer.VONR, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_volte_lteNoCsNoWifi()
    {
        List<RegistrationRow> rows = Arrays.asList(
                cs(false, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWwan(true, TelephonyManager.NETWORK_TYPE_LTE));

        assertEquals(VoiceBearer.VOLTE, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_combinedAttach_registeredCsOnLte_isVolte()
    {
        // The headline bug: combined-attach devices report a registered CS row with rat=LTE.
        // LTE has no CS domain, so this is VoLTE, not circuit switched "LTE" voice.
        List<RegistrationRow> rows = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_LTE),
                psWwan(true, TelephonyManager.NETWORK_TYPE_LTE));

        assertEquals(VoiceBearer.VOLTE, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_combinedAttach_registeredCsOnNr_isVonr()
    {
        List<RegistrationRow> rows = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_NR),
                psWwan(true, TelephonyManager.NETWORK_TYPE_NR));

        assertEquals(VoiceBearer.VONR, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_registeredCsWithUnknownRat_fallsThroughToWifiCalling()
    {
        // A registered CS row that reports an UNKNOWN RAT must not be labeled circuit switched; it
        // falls through to the Wi-Fi calling / PS inference.
        List<RegistrationRow> rows = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWlan(true, TelephonyManager.NETWORK_TYPE_IWLAN),
                psWwan(true, TelephonyManager.NETWORK_TYPE_NR));

        assertEquals(VoiceBearer.WIFI_CALLING, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_volte_psWwanLteOnly_noCsRow()
    {
        List<RegistrationRow> rows = Collections.singletonList(
                psWwan(true, TelephonyManager.NETWORK_TYPE_LTE));

        assertEquals(VoiceBearer.VOLTE, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_volte_psWwanLteCa()
    {
        List<RegistrationRow> rows = Collections.singletonList(
                psWwan(true, TelephonyStateUtils.NETWORK_TYPE_LTE_CA));

        assertEquals(VoiceBearer.VOLTE, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_csfb_prefersRegisteredCircuitSwitched()
    {
        // Data on LTE, but a registered 2G/3G CS row means the call drops to circuit switched.
        List<RegistrationRow> rows = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_GSM),
                psWwan(true, TelephonyManager.NETWORK_TYPE_LTE));

        VoiceBearerResult result = TelephonyStateUtils.deriveVoiceBearer(rows);
        assertEquals(VoiceBearer.CIRCUIT_SWITCHED, result.type());
        assertEquals(TelephonyManager.NETWORK_TYPE_GSM, result.circuitSwitchedRat());
    }

    @Test
    public void voiceBearer_csfb_gsm_beatsWifiCalling()
    {
        // A genuine 2G/3G CS registration wins over an established ePDG tunnel.
        List<RegistrationRow> rows = Arrays.asList(
                psWlan(true, TelephonyManager.NETWORK_TYPE_IWLAN),
                cs(true, TelephonyManager.NETWORK_TYPE_GSM));

        VoiceBearerResult result = TelephonyStateUtils.deriveVoiceBearer(rows);
        assertEquals(VoiceBearer.CIRCUIT_SWITCHED, result.type());
        assertEquals(TelephonyManager.NETWORK_TYPE_GSM, result.circuitSwitchedRat());
    }

    @Test
    public void voiceBearer_wifiCalling_beatsRegisteredCsOnLte()
    {
        // A CS row on LTE is just the combined attach; an established ePDG tunnel wins over it.
        List<RegistrationRow> rows = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_LTE),
                psWlan(true, TelephonyManager.NETWORK_TYPE_IWLAN));

        assertEquals(VoiceBearer.WIFI_CALLING, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_wifiCalling_beatsPsWwanButNotRegisteredCs()
    {
        // WLAN registered wins over PS/WWAN, but a registered 2G/3G CS row still wins over WLAN.
        List<RegistrationRow> withCs = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_UMTS),
                psWlan(true, TelephonyManager.NETWORK_TYPE_IWLAN));
        assertEquals(VoiceBearer.CIRCUIT_SWITCHED, TelephonyStateUtils.deriveVoiceBearer(withCs).type());
    }

    @Test
    public void voiceBearer_psWwanOn3g_isCircuitSwitched()
    {
        // A PS registration on UMTS means the phone is camped on 3G; calls ride the CS side of
        // that network even when the CS row itself is missing or reports an unusable RAT.
        List<RegistrationRow> psOnly = Collections.singletonList(
                psWwan(true, TelephonyManager.NETWORK_TYPE_UMTS));
        VoiceBearerResult result = TelephonyStateUtils.deriveVoiceBearer(psOnly);
        assertEquals(VoiceBearer.CIRCUIT_SWITCHED, result.type());
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, result.circuitSwitchedRat());

        List<RegistrationRow> withUnknownCs = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWwan(true, TelephonyManager.NETWORK_TYPE_UMTS));
        assertEquals(VoiceBearer.CIRCUIT_SWITCHED, TelephonyStateUtils.deriveVoiceBearer(withUnknownCs).type());
    }

    @Test
    public void voiceBearer_csPsDomainRow_matchesBothLookups()
    {
        // A single DOMAIN_CS_PS row must match the CS and PS lookups via bitmask matching.
        List<RegistrationRow> lteRow = Collections.singletonList(
                csPs(true, TelephonyManager.NETWORK_TYPE_LTE));
        assertEquals(VoiceBearer.VOLTE, TelephonyStateUtils.deriveVoiceBearer(lteRow).type());

        List<RegistrationRow> umtsRow = Collections.singletonList(
                csPs(true, TelephonyManager.NETWORK_TYPE_UMTS));
        VoiceBearerResult result = TelephonyStateUtils.deriveVoiceBearer(umtsRow);
        assertEquals(VoiceBearer.CIRCUIT_SWITCHED, result.type());
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, result.circuitSwitchedRat());
    }

    @Test
    public void voiceBearer_none_outOfService()
    {
        List<RegistrationRow> rows = Arrays.asList(
                cs(false, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWwan(false, TelephonyManager.NETWORK_TYPE_UNKNOWN));
        assertEquals(VoiceBearer.NONE, TelephonyStateUtils.deriveVoiceBearer(rows).type());
    }

    @Test
    public void voiceBearer_none_forNullAndEmpty()
    {
        assertEquals(VoiceBearer.NONE, TelephonyStateUtils.deriveVoiceBearer(null).type());
        assertEquals(VoiceBearer.NONE, TelephonyStateUtils.deriveVoiceBearer(Collections.emptyList()).type());
    }

    @Test
    public void nrMode_standalone_whenBaseIsNr()
    {
        // NR_ADVANCED override on an NR base is still Standalone (the captured case).
        assertEquals(NrMode.STANDALONE, TelephonyStateUtils.deriveNrMode(
                TelephonyManager.NETWORK_TYPE_NR, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED));
        assertEquals(NrMode.STANDALONE, TelephonyStateUtils.deriveNrMode(
                TelephonyManager.NETWORK_TYPE_NR, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE));
    }

    @Test
    public void nrMode_nonStandalone_whenLteWithNrOverride()
    {
        assertEquals(NrMode.NON_STANDALONE, TelephonyStateUtils.deriveNrMode(
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA));
        assertEquals(NrMode.NON_STANDALONE, TelephonyStateUtils.deriveNrMode(
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED));
        assertEquals(NrMode.NON_STANDALONE, TelephonyStateUtils.deriveNrMode(
                TelephonyStateUtils.NETWORK_TYPE_LTE_CA, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA));
    }

    @Test
    public void nrMode_none_whenLteWithoutNrOverride()
    {
        assertEquals(NrMode.NONE, TelephonyStateUtils.deriveNrMode(
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE));
        assertEquals(NrMode.NONE, TelephonyStateUtils.deriveNrMode(
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE));
    }

    @Test
    public void hero_standalone_and_nonStandalone()
    {
        HeroResult sa = TelephonyStateUtils.deriveHero(NrMode.STANDALONE,
                TelephonyManager.NETWORK_TYPE_NR, Collections.emptyList());
        assertEquals(HeroState.NR_STANDALONE, sa.state());

        HeroResult nsa = TelephonyStateUtils.deriveHero(NrMode.NON_STANDALONE,
                TelephonyManager.NETWORK_TYPE_LTE, Collections.emptyList());
        assertEquals(HeroState.NR_NON_STANDALONE, nsa.state());
    }

    @Test
    public void hero_plainDataRat()
    {
        HeroResult result = TelephonyStateUtils.deriveHero(NrMode.NONE,
                TelephonyManager.NETWORK_TYPE_LTE, Collections.emptyList());
        assertEquals(HeroState.DATA_RAT, result.state());
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, result.rat());
    }

    @Test
    public void hero_iwlanBase_usesRegisteredPsWwanRat()
    {
        // Wi-Fi calling before the display info callback fires: getDataNetworkType() reports
        // IWLAN, but the hero must show the cellular RAT the device is camped on.
        List<RegistrationRow> rows = Arrays.asList(
                psWlan(true, TelephonyManager.NETWORK_TYPE_IWLAN),
                psWwan(true, TelephonyManager.NETWORK_TYPE_NR));

        HeroResult result = TelephonyStateUtils.deriveHero(NrMode.NONE,
                TelephonyManager.NETWORK_TYPE_IWLAN, rows);
        assertEquals(HeroState.DATA_RAT, result.state());
        assertEquals(TelephonyManager.NETWORK_TYPE_NR, result.rat());
    }

    @Test
    public void hero_csOnlyCamping_usesRegisteredCsRat()
    {
        // 2G voice-only camping (no data): getDataNetworkType() is UNKNOWN, but the registered CS
        // row tells us the phone is on GSM.
        List<RegistrationRow> rows = Arrays.asList(
                cs(true, TelephonyManager.NETWORK_TYPE_GSM),
                psWwan(false, TelephonyManager.NETWORK_TYPE_UNKNOWN));

        HeroResult result = TelephonyStateUtils.deriveHero(NrMode.NONE,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, rows);
        assertEquals(HeroState.DATA_RAT, result.state());
        assertEquals(TelephonyManager.NETWORK_TYPE_GSM, result.rat());
    }

    @Test
    public void hero_noService_whenRowsExistButNoneRegistered()
    {
        List<RegistrationRow> rows = Arrays.asList(
                cs(false, TelephonyManager.NETWORK_TYPE_UNKNOWN),
                psWwan(false, TelephonyManager.NETWORK_TYPE_UNKNOWN));

        assertEquals(HeroState.NO_SERVICE, TelephonyStateUtils.deriveHero(NrMode.NONE,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, rows).state());
    }

    @Test
    public void hero_unknown_whenNoRows()
    {
        assertEquals(HeroState.UNKNOWN, TelephonyStateUtils.deriveHero(NrMode.NONE,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, null).state());
        assertEquals(HeroState.UNKNOWN, TelephonyStateUtils.deriveHero(NrMode.NONE,
                TelephonyManager.NETWORK_TYPE_UNKNOWN, Collections.emptyList()).state());
    }

    @Test
    public void brandingOverride_trueOnlyForRealOverrides()
    {
        assertTrue(TelephonyStateUtils.isBrandingOverride(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA));
        assertTrue(TelephonyStateUtils.isBrandingOverride(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO));
        assertTrue(TelephonyStateUtils.isBrandingOverride(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA));
        assertTrue(TelephonyStateUtils.isBrandingOverride(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED));

        assertFalse(TelephonyStateUtils.isBrandingOverride(TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE));
        assertFalse(TelephonyStateUtils.isBrandingOverride(-1));
        assertFalse(TelephonyStateUtils.isBrandingOverride(99));
    }

    @Test
    public void domainAndTransportNames()
    {
        assertEquals("CS", CalculationUtils.getDomainName(NetworkRegistrationInfo.DOMAIN_CS));
        assertEquals("PS", CalculationUtils.getDomainName(NetworkRegistrationInfo.DOMAIN_PS));
        assertEquals("CS+PS", CalculationUtils.getDomainName(NetworkRegistrationInfo.DOMAIN_CS_PS));
        assertEquals("Unknown", CalculationUtils.getDomainName(NetworkRegistrationInfo.DOMAIN_UNKNOWN));

        assertEquals("WWAN", CalculationUtils.getTransportTypeName(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertEquals("WLAN", CalculationUtils.getTransportTypeName(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        assertEquals("Unknown", CalculationUtils.getTransportTypeName(-42));
    }

    @Test
    public void validBandwidths_filtersImplausibleEntries()
    {
        assertArrayEquals(new int[]{20000}, CellularBandwidthUtils.validBandwidthsKhz(
                new int[]{20000, 0, -5, Integer.MAX_VALUE}));
        assertArrayEquals(new int[0], CellularBandwidthUtils.validBandwidthsKhz(new int[]{0, 0}));
        assertArrayEquals(new int[0], CellularBandwidthUtils.validBandwidthsKhz(null));
    }

    @Test
    public void aggregateBandwidth_sumsValidKhz()
    {
        // The captured 5-carrier aggregate: 20 + 20 + 5 + 100 + 90 = 235 MHz.
        assertEquals(235_000, CellularBandwidthUtils.aggregateBandwidthKhz(
                new int[]{20000, 20000, 5000, 100000, 90000}));
        assertEquals(20_000, CellularBandwidthUtils.aggregateBandwidthKhz(new int[]{20000, 0, -1}));
        assertEquals(0, CellularBandwidthUtils.aggregateBandwidthKhz(new int[]{}));
        assertEquals(0, CellularBandwidthUtils.aggregateBandwidthKhz(null));
    }

    @Test
    public void formatBandwidth_wholeAndFractionalMhz()
    {
        assertEquals("20", CellularBandwidthUtils.formatBandwidthMhz(20_000));
        assertEquals("235", CellularBandwidthUtils.formatBandwidthMhz(235_000));
        // Fractional values format with the default locale's decimal separator.
        assertEquals(String.format(Locale.getDefault(), "%.1f", 1.4),
                CellularBandwidthUtils.formatBandwidthMhz(1_400));
    }
}
