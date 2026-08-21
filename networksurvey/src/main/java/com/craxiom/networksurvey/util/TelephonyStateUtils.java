package com.craxiom.networksurvey.util;

import android.os.Build;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Derives human-meaningful cellular technology facts from an Android {@link ServiceState} that the
 * flat {@link TelephonyManager} getters cannot express.
 * <p>
 * The motivating case: {@link TelephonyManager#getVoiceNetworkType()} reads only the circuit
 * switched (CS) registration on the cellular (WWAN) transport, so it reports
 * {@link TelephonyManager#NETWORK_TYPE_UNKNOWN} whenever voice is carried over Wi-Fi calling
 * (IWLAN) or VoNR. This class instead walks the full per-(domain, transport) registration table to
 * infer the actual voice bearer, and derives 5G NR Standalone vs Non-Standalone, neither of which
 * the platform exposes directly to a normal app. It also derives the "hero" technology summary
 * shown at the top of the cellular details screen.
 * <p>
 * The derivation functions are pure: they take plain {@link RegistrationRow} tuples plus ints and
 * reference only compile-time-constant telephony values, so they are unit-testable without Android
 * framework objects. Only {@link #extractRows} touches the framework and is guarded to API 30+.
 */
public final class TelephonyStateUtils
{
    /**
     * {@link TelephonyManager}.NETWORK_TYPE_LTE_CA is hidden from the public SDK, so its value is
     * mirrored here (CalculationUtils#getNetworkType matches on the raw 19 for the same reason).
     */
    public static final int NETWORK_TYPE_LTE_CA = 19;

    /**
     * The technology inferred to be carrying voice calls. VoLTE / VoNR are best-effort inferences:
     * VoPS support ({@code getDataSpecificInfo}) and the IMS registration APIs are hidden from
     * normal apps, so they cannot be confirmed.
     */
    public enum VoiceBearer
    {
        WIFI_CALLING, VOLTE, VONR, CIRCUIT_SWITCHED, NONE
    }

    /**
     * 5G NR connectivity mode. NONE means the device is not on NR at all.
     */
    public enum NrMode
    {
        STANDALONE, NON_STANDALONE, NONE
    }

    /**
     * The kind of technology summary the top card's hero line should show. DATA_RAT means "show
     * the RAT carried in {@link HeroResult#rat()}"; the other states have fixed labels.
     */
    public enum HeroState
    {
        NR_STANDALONE, NR_NON_STANDALONE, DATA_RAT, NO_SERVICE, UNKNOWN
    }

    /**
     * What the 5G NR Secondary Cell details card should show for a scan: the live cell (ACTIVE),
     * a dimmed last-seen cell while the NR leg is idle on NSA (IDLE), or nothing (HIDDEN).
     */
    public enum NrCardState
    {
        ACTIVE, IDLE, HIDDEN
    }

    /**
     * A single network registration row, keyed by (domain, transport). Domain/transport/rat use the
     * Android integer constants (compile-time inlined, so this record is framework-object-free).
     */
    public record RegistrationRow(int domain, int transportType, boolean registered,
                                  int accessNetworkType, String registeredPlmn)
    {
    }

    /**
     * Result of {@link #deriveVoiceBearer}. {@code circuitSwitchedRat} is only meaningful for
     * {@link VoiceBearer#CIRCUIT_SWITCHED}, where it carries the 2G/3G RAT to display.
     */
    public record VoiceBearerResult(VoiceBearer type, int circuitSwitchedRat)
    {
    }

    /**
     * Result of {@link #deriveHero}. {@code rat} is only meaningful for
     * {@link HeroState#DATA_RAT}, where it carries the RAT to display.
     */
    public record HeroResult(HeroState state, int rat)
    {
    }

    private TelephonyStateUtils()
    {
    }

    /**
     * Flattens a {@link ServiceState}'s registration list into plain {@link RegistrationRow} tuples.
     *
     * @param serviceState The service state to read; may be null.
     * @return The registration rows, never null (empty when the state is null or has no rows).
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    @NonNull
    public static List<RegistrationRow> extractRows(ServiceState serviceState)
    {
        final List<RegistrationRow> rows = new ArrayList<>();
        if (serviceState == null) return rows;

        for (NetworkRegistrationInfo info : serviceState.getNetworkRegistrationInfoList())
        {
            rows.add(new RegistrationRow(info.getDomain(), info.getTransportType(),
                    isRegistered(info), info.getAccessNetworkTechnology(), info.getRegisteredPlmn()));
        }
        return rows;
    }

    /**
     * The {@code isNetworkRegistered()} getter reports the true modem state but was only added in
     * API 34; the deprecated {@code isRegistered()} goes back to API 30 and reports the same thing
     * modulo carrier-config overrides.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    @SuppressWarnings("deprecation")
    private static boolean isRegistered(NetworkRegistrationInfo info)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? info.isNetworkRegistered()
                : info.isRegistered();
    }

    /**
     * Infers the voice bearer from the registration table. Ordering matters:
     * <ol>
     *     <li>A registered CS-domain WWAN row with a genuine 2G/3G RAT wins first. This covers 2G/3G
     *     camping and CSFB (circuit switched fallback always holds a real CS registration). A CS row
     *     reporting LTE or NR is NOT circuit switched voice; LTE and NR have no CS domain, and
     *     combined-attach devices routinely report a registered CS row with rat=LTE while voice
     *     actually rides IMS.</li>
     *     <li>Else a registered PS-domain WLAN row means Wi-Fi calling (an established ePDG tunnel).
     *     Note the WLAN row is not required to advertise {@code SERVICE_TYPE_VOICE}; real devices
     *     (including our captured T-Mobile Pixel) leave it advertising only DATA while Wi-Fi
     *     calling is plainly active.</li>
     *     <li>Else classify the registered PS-domain WWAN row (falling back to a registered
     *     CS-domain WWAN row): NR is inferred as VoNR, LTE as VoLTE, and a 2G/3G RAT as circuit
     *     switched voice (a phone with a PS registration on UMTS still carries calls on the CS
     *     side of that 3G network).</li>
     *     <li>Else NONE.</li>
     * </ol>
     * Domain matching is by bitmask so DOMAIN_CS_PS rows match both the CS and PS lookups.
     *
     * @param rows The registration rows; may be null/empty.
     * @return The inferred bearer, never null.
     */
    @NonNull
    public static VoiceBearerResult deriveVoiceBearer(List<RegistrationRow> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return new VoiceBearerResult(VoiceBearer.NONE, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        }

        for (RegistrationRow row : rows)
        {
            if (row.registered() && hasDomain(row, NetworkRegistrationInfo.DOMAIN_CS)
                    && row.transportType() == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                    && isCircuitSwitchedRat(row.accessNetworkType()))
            {
                return new VoiceBearerResult(VoiceBearer.CIRCUIT_SWITCHED, row.accessNetworkType());
            }
        }

        if (findRegistered(rows, NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WLAN) != null)
        {
            return new VoiceBearerResult(VoiceBearer.WIFI_CALLING, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        }

        VoiceBearerResult result = classifyPacketEraRow(
                findRegistered(rows, NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        if (result == null)
        {
            result = classifyPacketEraRow(
                    findRegistered(rows, NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        }
        if (result != null) return result;

        return new VoiceBearerResult(VoiceBearer.NONE, TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    /**
     * Classifies a registered WWAN row by its RAT for step 3 of {@link #deriveVoiceBearer}.
     *
     * @return The bearer result, or null when the row is null or its RAT is unknown.
     */
    private static VoiceBearerResult classifyPacketEraRow(RegistrationRow row)
    {
        if (row == null) return null;

        final int rat = row.accessNetworkType();
        if (rat == TelephonyManager.NETWORK_TYPE_NR)
        {
            return new VoiceBearerResult(VoiceBearer.VONR, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        }
        if (rat == TelephonyManager.NETWORK_TYPE_LTE || rat == NETWORK_TYPE_LTE_CA)
        {
            return new VoiceBearerResult(VoiceBearer.VOLTE, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        }
        if (isCircuitSwitchedRat(rat))
        {
            return new VoiceBearerResult(VoiceBearer.CIRCUIT_SWITCHED, rat);
        }
        return null;
    }

    /**
     * Derives 5G NR Standalone vs Non-Standalone. Keyed off the base data RAT, never the override
     * alone: OVERRIDE_NETWORK_TYPE_NR_ADVANCED appears on both SA and NSA (our SA capture had a NR
     * base with an NR_ADVANCED override), so only the base RAT distinguishes them.
     *
     * @param baseDataRat         The base cellular data RAT (e.g. {@link TelephonyManager#NETWORK_TYPE_NR}).
     * @param overrideNetworkType The {@link TelephonyDisplayInfo} override network type.
     * @return STANDALONE, NON_STANDALONE, or NONE.
     */
    @NonNull
    public static NrMode deriveNrMode(int baseDataRat, int overrideNetworkType)
    {
        if (baseDataRat == TelephonyManager.NETWORK_TYPE_NR) return NrMode.STANDALONE;
        final boolean lteBase = baseDataRat == TelephonyManager.NETWORK_TYPE_LTE
                || baseDataRat == NETWORK_TYPE_LTE_CA;
        if (lteBase && isNrOverride(overrideNetworkType))
        {
            return NrMode.NON_STANDALONE;
        }
        return NrMode.NONE;
    }

    /**
     * Decides what the 5G NR Secondary Cell card should show for the current scan. The card is
     * ACTIVE when the device reported a SECONDARY_SERVING NR cell in the scan. On NSA the NR leg
     * only attaches while data is actively transferring and detaches within seconds of going
     * idle, so when the cell is absent but the phone is still in NSA mode the card holds the
     * last-seen cell in a dimmed IDLE state instead of vanishing, until the last sighting is
     * older than maxLastSeenAgeMs. In every other case the card is HIDDEN, matching the behavior
     * before the idle state existed.
     *
     * @param hasSecondaryCell Whether the current scan contains a SECONDARY_SERVING NR cell.
     * @param nrMode           The NR mode in effect for the same scan.
     * @param lastSeenAgeMs    Milliseconds since the last ACTIVE sighting, or -1 when there is none.
     * @param maxLastSeenAgeMs The maximum last-seen age for which the IDLE state is shown.
     * @return The card state, never null.
     */
    @NonNull
    public static NrCardState resolveNrCardState(boolean hasSecondaryCell, NrMode nrMode,
                                                 long lastSeenAgeMs, long maxLastSeenAgeMs)
    {
        if (hasSecondaryCell) return NrCardState.ACTIVE;
        if (nrMode == NrMode.NON_STANDALONE && lastSeenAgeMs >= 0 && lastSeenAgeMs <= maxLastSeenAgeMs)
        {
            return NrCardState.IDLE;
        }
        return NrCardState.HIDDEN;
    }

    /**
     * Derives what the top card's hero line should show.
     * <ol>
     *     <li>An NR mode means 5G Standalone or Non-Standalone.</li>
     *     <li>Else show the effective data RAT: the base data RAT when it is a real cellular RAT,
     *     otherwise the RAT of a registered WWAN row (PS preferred, then CS). The fallback keeps
     *     Wi-Fi calling from heroing "IWLAN" and lets 2G/3G voice-only camping hero its RAT even
     *     though {@code getDataNetworkType()} reports UNKNOWN there.</li>
     *     <li>Else, when rows exist and none is registered, the device has no service.</li>
     *     <li>Else the state is simply unknown (e.g. no ServiceState reported yet).</li>
     * </ol>
     *
     * @param nrMode      The derived NR mode.
     * @param baseDataRat The base cellular data RAT fed to {@link #deriveNrMode}.
     * @param rows        The registration rows; may be null/empty.
     * @return The hero result, never null.
     */
    @NonNull
    public static HeroResult deriveHero(NrMode nrMode, int baseDataRat, List<RegistrationRow> rows)
    {
        if (nrMode == NrMode.STANDALONE)
        {
            return new HeroResult(HeroState.NR_STANDALONE, TelephonyManager.NETWORK_TYPE_NR);
        }
        if (nrMode == NrMode.NON_STANDALONE)
        {
            return new HeroResult(HeroState.NR_NON_STANDALONE, baseDataRat);
        }

        int rat = baseDataRat > TelephonyManager.NETWORK_TYPE_UNKNOWN
                && baseDataRat != TelephonyManager.NETWORK_TYPE_IWLAN
                ? baseDataRat : TelephonyManager.NETWORK_TYPE_UNKNOWN;
        if (rat == TelephonyManager.NETWORK_TYPE_UNKNOWN)
        {
            rat = registeredWwanRat(rows);
        }
        if (rat != TelephonyManager.NETWORK_TYPE_UNKNOWN)
        {
            return new HeroResult(HeroState.DATA_RAT, rat);
        }

        if (rows != null && !rows.isEmpty() && rows.stream().noneMatch(RegistrationRow::registered))
        {
            return new HeroResult(HeroState.NO_SERVICE, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        }
        return new HeroResult(HeroState.UNKNOWN, TelephonyManager.NETWORK_TYPE_UNKNOWN);
    }

    /**
     * @return The RAT of a registered WWAN row (PS domain preferred, then CS), or
     * {@link TelephonyManager#NETWORK_TYPE_UNKNOWN} when there is none with a known RAT.
     */
    private static int registeredWwanRat(List<RegistrationRow> rows)
    {
        final RegistrationRow ps = findRegistered(rows, NetworkRegistrationInfo.DOMAIN_PS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (ps != null && ps.accessNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN)
        {
            return ps.accessNetworkType();
        }
        final RegistrationRow cs = findRegistered(rows, NetworkRegistrationInfo.DOMAIN_CS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        if (cs != null && cs.accessNetworkType() != TelephonyManager.NETWORK_TYPE_UNKNOWN)
        {
            return cs.accessNetworkType();
        }
        return TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    /**
     * @param overrideNetworkType The {@link TelephonyDisplayInfo} override network type, or -1 if
     *                            never reported.
     * @return True when the carrier is branding the connection with an override value worth showing
     * (e.g. "NR Advanced"); false for none/-1/unrecognized.
     */
    public static boolean isBrandingOverride(int overrideNetworkType)
    {
        return switch (overrideNetworkType)
        {
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                 TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
                 TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                 TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                 TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> true;
            default -> false;
        };
    }

    /**
     * Decides whether the details card's pill row is worth showing. On a live hero state the row
     * always shows with explicit values so nothing appears or disappears during normal use. On the
     * degraded {@link HeroState#NO_SERVICE} and {@link HeroState#UNKNOWN} states the row only
     * shows when at least one of the voice or data displays carries a real value: a degraded hero
     * does not imply the pills are empty (e.g. on API 26-30 during Wi-Fi calling the hero is
     * UNKNOWN while the legacy data network type is a perfectly displayable IWLAN).
     *
     * @param heroState    The derived hero state.
     * @param voiceDisplay The voice pill display value; may be null.
     * @param dataDisplay  The data pill display value; may be null.
     * @param noiseValues  The display values that carry no information (e.g. "Unknown", "None").
     * @return True when the pill row should be visible.
     */
    public static boolean shouldShowPillRow(HeroState heroState, String voiceDisplay,
                                            String dataDisplay, Set<String> noiseValues)
    {
        if (heroState != HeroState.NO_SERVICE && heroState != HeroState.UNKNOWN) return true;
        return isDisplayable(voiceDisplay, noiseValues) || isDisplayable(dataDisplay, noiseValues);
    }

    /**
     * @return True when the pill display value carries real information: non-null, non-empty, and
     * not one of the noise values.
     */
    private static boolean isDisplayable(String display, Set<String> noiseValues)
    {
        return display != null && !display.isEmpty() && !noiseValues.contains(display);
    }

    private static boolean isNrOverride(int overrideNetworkType)
    {
        return overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE
                || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED;
    }

    /**
     * @return True when the RAT is a 2G/3G technology whose voice calls are genuinely circuit
     * switched. LTE, NR, and IWLAN return false (their voice rides IMS or the ePDG tunnel).
     */
    private static boolean isCircuitSwitchedRat(int rat)
    {
        return switch (rat)
        {
            case TelephonyManager.NETWORK_TYPE_GPRS,
                 TelephonyManager.NETWORK_TYPE_EDGE,
                 TelephonyManager.NETWORK_TYPE_UMTS,
                 TelephonyManager.NETWORK_TYPE_CDMA,
                 TelephonyManager.NETWORK_TYPE_EVDO_0,
                 TelephonyManager.NETWORK_TYPE_EVDO_A,
                 TelephonyManager.NETWORK_TYPE_EVDO_B,
                 TelephonyManager.NETWORK_TYPE_1xRTT,
                 TelephonyManager.NETWORK_TYPE_HSDPA,
                 TelephonyManager.NETWORK_TYPE_HSUPA,
                 TelephonyManager.NETWORK_TYPE_HSPA,
                 TelephonyManager.NETWORK_TYPE_HSPAP,
                 TelephonyManager.NETWORK_TYPE_IDEN,
                 TelephonyManager.NETWORK_TYPE_EHRPD,
                 TelephonyManager.NETWORK_TYPE_GSM,
                 TelephonyManager.NETWORK_TYPE_TD_SCDMA -> true;
            default -> false;
        };
    }

    /**
     * Finds the first registered row matching the domain bit and transport. Domain matching is by
     * bitmask so DOMAIN_CS_PS rows match both the CS and PS lookups.
     */
    private static RegistrationRow findRegistered(List<RegistrationRow> rows, int domainBit, int transportType)
    {
        if (rows == null) return null;
        for (RegistrationRow row : rows)
        {
            if (row.registered() && hasDomain(row, domainBit) && row.transportType() == transportType)
            {
                return row;
            }
        }
        return null;
    }

    private static boolean hasDomain(RegistrationRow row, int domainBit)
    {
        return (row.domain() & domainBit) != 0;
    }

}
