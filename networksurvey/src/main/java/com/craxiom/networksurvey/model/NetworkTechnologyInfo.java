package com.craxiom.networksurvey.model;

import com.craxiom.networksurvey.util.TelephonyStateUtils;

import java.util.List;

/**
 * Immutable snapshot of the cellular technology facts shown on the top card of the cellular details
 * screen. Built per cellular scan in the CellularController and delivered to the UI via
 * {@code ICellularSurveyRecordListener.onNetworkType}. Display strings are already resolved so the
 * fragment only has to bind them; the raw ints ({@link #overrideNetworkType}, {@link #baseDataRat})
 * and {@link #nrMode} are carried alongside because the fragment derives the hero line and the
 * Branding pill visibility from them.
 * <p>
 * On API 26-30 (no TelephonyCallback display info) the enriched fields stay at their defaults:
 * overrideNetworkType -1, nrMode NONE, null bandwidths and registration rows, and baseDataRat is
 * the plain {@code getDataNetworkType()} value.
 *
 * @param subscriptionId      The subscription (SIM) this snapshot belongs to.
 * @param voiceDisplay        Resolved voice bearer text (e.g. "Wi-Fi Calling", "VoNR", "GSM", "None").
 * @param dataDisplay         Resolved data RAT text (e.g. "NR", "LTE").
 * @param overrideDisplay     Resolved override network text (e.g. "NR Advanced", "None", "N/A").
 * @param overrideNetworkType The raw override network type int, or -1 when never reported.
 * @param baseDataRat         The base cellular data RAT int fed to the NR mode derivation.
 * @param nrMode              NR Standalone / Non-Standalone / none.
 * @param cellBandwidthsKhz   Per-carrier bandwidths in kHz for carrier aggregation; may be null.
 * @param registrationRows    Parsed registration table for the info dialog; may be null (pre-API-31).
 */
public record NetworkTechnologyInfo(
        int subscriptionId,
        String voiceDisplay,
        String dataDisplay,
        String overrideDisplay,
        int overrideNetworkType,
        int baseDataRat,
        TelephonyStateUtils.NrMode nrMode,
        int[] cellBandwidthsKhz,
        List<TelephonyStateUtils.RegistrationRow> registrationRows)
{
}
