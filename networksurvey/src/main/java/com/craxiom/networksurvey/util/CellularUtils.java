package com.craxiom.networksurvey.util;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.ConnectionStatus;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.data.api.Tower;
import com.craxiom.networksurvey.util.band.BandNames;
import com.craxiom.networksurvey.util.band.LteBandTable;
import com.craxiom.networksurvey.util.band.NrBandTable;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.model.NrRecordWrapper;
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo;
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo;
import com.craxiom.networksurvey.ui.cellular.model.TowerIdentity;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;

import java.util.List;

/**
 * Helper methods for working with cellular networks.
 */
public class CellularUtils
{
    /**
     * Joins the candidate bands of an ambiguous NARFCN (e.g. "n48 / n77 / n78"). Device-reported
     * bands are joined with {@link #REPORTED_BAND_SEPARATOR} instead, so the separator itself
     * tells the reader whether the value was reported by the modem or derived from the channel.
     */
    private static final String DERIVED_BAND_SEPARATOR = " / ";

    /**
     * Joins bands the device actually reported.
     */
    private static final String REPORTED_BAND_SEPARATOR = ", ";

    /**
     * Converts 5G NR ARFCN to frequency in MHz according to 3GPP TS 38.104 specification.
     * The formula is: F_REF = F_REF-Offs + (N_REF - N_REF-Offs) * Δf
     * <p>
     * Resource: <a href="https://5g-tools.com/5g-nr-arfcn-calculator/">5G NARFCN Calculator</a>
     *
     * @param narfcn The NR-ARFCN (Absolute Radio Frequency Channel Number) to convert.
     * @return The frequency in MHz, or -1.0 if the NARFCN is not in a valid range.
     */
    public static double narfcnToFrequencyMhz(int narfcn)
    {
        if (narfcn < 0)
        {
            return -1.0;
        }

        // 3GPP TS 38.104 Table 5.4.2.1-1: Global frequency raster parameters for NR
        if (narfcn <= 599999)
        {
            // Range 1: 0 ≤ ARFCN ≤ 599,999
            // Δf = 5 kHz, F_REF-Offs = 0, N_REF-Offs = 0
            return narfcn * 0.005; // 5 kHz = 0.005 MHz
        } else if (narfcn <= 2016666)
        {
            // Range 2: 600,000 ≤ ARFCN ≤ 2,016,666
            // Δf = 15 kHz, F_REF-Offs = 3000 MHz, N_REF-Offs = 600,000
            return 3000.0 + (narfcn - 600000) * 0.015; // 15 kHz = 0.015 MHz
        } else if (narfcn <= 3279165)
        {
            // Range 3: 2,016,667 ≤ ARFCN ≤ 3,279,165
            // Δf = 60 kHz, F_REF-Offs = 24,250.08 MHz, N_REF-Offs = 2,016,667
            return 24250.08 + (narfcn - 2016667) * 0.060; // 60 kHz = 0.060 MHz
        } else
        {
            // NARFCN is outside the valid range
            return -1.0;
        }
    }

    /**
     * Gets the band name for a given LTE band number.
     *
     * @param bandNumber The LTE band number (e.g., 1, 2, 3, etc.).
     * @return The band name, or null if the band number is not recognized.
     */
    public static String getLteBandName(int bandNumber)
    {
        return BandNames.getLteBandName(bandNumber);
    }

    /**
     * Gets the band name for a given 5G NR band number.
     *
     * @param bandNumber The NR band number (e.g., 1, 2, 3, etc.).
     * @return The band name, or null if the band number is not recognized.
     */
    public static String getNrBandName(int bandNumber)
    {
        return BandNames.getNrBandName(bandNumber);
    }

    /**
     * Returns the LTE band for a given EARFCN.
     *
     * @param earfcn The EARFCN to get the band for.
     * @return The LTE band for the given EARFCN, or -1 if the EARFCN is not in a known band.
     */
    public static int downlinkEarfcnToBand(int earfcn)
    {
        return LteBandTable.downlinkEarfcnToBand(earfcn);
    }

    /**
     * Returns every 5G NR band whose downlink range contains the given NARFCN, ascending.
     * <p>
     * NR ranges overlap heavily, so a single NARFCN routinely falls in more than one band and
     * this returns all of them. See {@link NrBandTable} for which bands take part at all.
     *
     * @param narfcn The downlink NARFCN to look up.
     * @return The matching band numbers, or an empty array. Never null.
     */
    public static int[] downlinkNarfcnToBands(int narfcn)
    {
        return NrBandTable.downlinkNarfcnToBands(narfcn);
    }

    /**
     * Returns the 5G NR band for a given downlink NARFCN, but only when the NARFCN falls in
     * exactly one operating band. A NARFCN contained in more than one band returns -1 rather than
     * guessing, which keeps the derived value trustworthy at the cost of leaving ambiguous ranges
     * unresolved. Callers that can display several candidates should use
     * {@link #downlinkNarfcnToBands(int)} instead.
     *
     * @param narfcn The downlink NARFCN to look up.
     * @return The NR band number, or -1 when the NARFCN is invalid, matches no band, or matches
     * more than one band.
     */
    public static int downlinkNarfcnToBand(int narfcn)
    {
        return NrBandTable.downlinkNarfcnToBand(narfcn);
    }

    /**
     * The NR bands a cell is on, using the same precedence as {@link #formatNrBands(int[], int)}:
     * whatever the device reported, and otherwise every band whose range contains the NARFCN.
     * <p>
     * Exists so that a caller which needs the numbers, such as making the Band field tappable,
     * cannot drift from what the Band field displays.
     *
     * @param bands  The NR band numbers reported for the cell, or null/empty when none were.
     * @param narfcn The cell's downlink NARFCN to fall back to.
     * @return The band numbers, or an empty array when nothing could be determined. Never null.
     */
    public static int[] resolveNrBands(int[] bands, int narfcn)
    {
        if (bands != null && bands.length > 0) return bands;

        return downlinkNarfcnToBands(narfcn);
    }

    /**
     * Same as {@link #formatNrBands(int[])}, except that when the device did not report any bands
     * they are derived from the downlink NARFCN via {@link #downlinkNarfcnToBands(int)}.
     * Device-reported bands always take precedence. The derivation is display-only; logged and
     * streamed records never carry a band.
     * <p>
     * When the NARFCN is ambiguous every candidate is listed, joined by
     * {@link #DERIVED_BAND_SEPARATOR} and without friendly names, because a quarter of the
     * covered NARFCN space matches more than one band and rendering nothing there hides the
     * serving band entirely on devices that report none. Dropping the names keeps the string
     * short enough for the Band row; a slash-joined list of bare designators is also self
     * evidently an inference rather than a reported value, which a single name would not be.
     *
     * @param bands  The NR band numbers reported for the cell, or null/empty when none were.
     * @param narfcn The cell's downlink NARFCN to fall back to.
     * @return The formatted band string, or an empty string when the NARFCN is invalid or falls
     * in no known band.
     */
    public static String formatNrBands(int[] bands, int narfcn)
    {
        if (bands != null && bands.length > 0) return formatNrBands(bands);

        final int[] derivedBands = resolveNrBands(null, narfcn);
        if (derivedBands.length == 0) return "";
        if (derivedBands.length == 1) return formatNrBands(derivedBands);

        final StringBuilder bandString = new StringBuilder();
        for (int i = 0; i < derivedBands.length; i++)
        {
            bandString.append('n').append(derivedBands[i]);
            if (i < derivedBands.length - 1) bandString.append(DERIVED_BAND_SEPARATOR);
        }

        return bandString.toString();
    }

    /**
     * Formats an array of 5G NR band numbers for display using the standard 3GPP "n" prefix and
     * the friendly band name when one is known (e.g. "n77 (TD 3700)"). Multiple bands are joined
     * with a comma.
     *
     * @param bands The NR band numbers reported for the cell.
     * @return The formatted band string, or an empty string when no bands were reported.
     */
    public static String formatNrBands(int[] bands)
    {
        if (bands == null || bands.length == 0) return "";

        final StringBuilder bandString = new StringBuilder();
        for (int i = 0; i < bands.length; i++)
        {
            final int bandNumber = bands[i];
            final String bandName = getNrBandName(bandNumber);

            bandString.append('n').append(bandNumber);
            if (bandName != null)
            {
                bandString.append(" (").append(bandName).append(")");
            }

            if (i < bands.length - 1)
            {
                bandString.append(REPORTED_BAND_SEPARATOR);
            }
        }

        return bandString.toString();
    }

    /**
     * Selects the NR record to display on the NR Secondary Cell details card from the non-serving
     * NR records of a single scan: the first record whose device-reported connection status is
     * {@link ConnectionStatus#SECONDARY_SERVING}. On 5G NSA (EN-DC) that is the NR cell actively
     * carrying the 5G data (the phone is registered on the LTE anchor, so that cell reports
     * {@code isRegistered() == false}); on 5G SA with NR carrier aggregation it is an NR SCell.
     * <p>
     * The selection is deliberately limited to a device-reported status. An earlier version
     * guessed the NSA data leg by strongest SS-RSRP when no cell reported a status, but a guessed
     * cell rendered identically to a device-reported one and removed a genuine neighbor from the
     * neighbors table, so on devices that report no connection status the card simply does not
     * show. Do not reintroduce an inference here without a provenance flag in the view state.
     *
     * @param nrRecords The non-serving NR records from one scan.
     * @return The selected wrapper, or null when the list is empty or nothing qualifies.
     */
    public static NrRecordWrapper selectSecondaryServingNrCell(List<NrRecordWrapper> nrRecords)
    {
        if (nrRecords == null || nrRecords.isEmpty()) return null;

        for (NrRecordWrapper wrapper : nrRecords)
        {
            final NrRecordData data = ((NrRecord) wrapper.cellularRecord).getData();
            if (data.getConnectionStatus() == ConnectionStatus.SECONDARY_SERVING) return wrapper;
        }

        return null;
    }

    /**
     * @return Returns true if the servingCell field is present and also set to true.
     */
    public static boolean isServingCell(GeneratedMessage message)
    {
        try
        {
            // Get the descriptor for the top-level message
            Descriptors.Descriptor descriptor = message.getDescriptorForType();

            // Get the descriptor for the 'data' field
            Descriptors.FieldDescriptor dataField = descriptor.findFieldByName("data");
            if (dataField == null)
            {
                return false;
            }

            // Get the value of the 'data' field
            GeneratedMessage dataMessage = (GeneratedMessage) message.getField(dataField);

            // Get the descriptor for the 'servingCell' field within the 'data' field
            Descriptors.Descriptor dataDescriptor = dataMessage.getDescriptorForType();
            Descriptors.FieldDescriptor servingCellField = dataDescriptor.findFieldByName("servingCell");
            if (servingCellField == null)
            {
                return false;
            }

            // Get the value of the 'servingCell' field
            return ((BoolValue) dataMessage.getField(servingCellField)).getValue();
        } catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Get the ID used to identify a tower on the map. This is NOT the CGI because I wanted to
     * include the TAC for LTE and NR, but the CGI doesn't include the TAC.
     */
    public static String getTowerId(Tower tower)
    {
        if (tower == null)
        {
            return "";
        }

        return TowerIdentity.towerId(tower.getMcc(), tower.getMnc(), tower.getArea(), tower.getCid());
    }

    /**
     * Get the ID used to identify a tower on the map. This is NOT the CGI because I wanted to
     * include the TAC for LTE and NR, but the CGI doesn't include the TAC.
     *
     * @param servingCellInfo The ServingCellInfo to get the ID from.
     * @return The ID, or an empty string if the ServingCellInfo is null or the ServingCell is null.
     */
    public static String getTowerId(ServingCellInfo servingCellInfo)
    {
        return servingCellInfo == null ? "" : getTowerId(servingCellInfo.getServingCell());
    }

    /**
     * Get the ID used to identify a tower on the map. This is NOT the CGI because I wanted to
     * include the TAC for LTE and NR, but the CGI doesn't include the TAC.
     *
     * @param cellularRecord The cellular record to get the ID from.
     * @return The ID, or an empty string if the record is null or its protocol has no tower ID.
     */
    public static String getTowerId(CellularRecordWrapper cellularRecord)
    {
        if (cellularRecord == null)
        {
            return "";
        }

        switch (cellularRecord.cellularProtocol)
        {
            case NONE:
                return "";

            case GSM:
                final GsmRecordData gsmData = ((GsmRecord) cellularRecord.cellularRecord).getData();
                String[] gsmMccMnc = NsUtils.extractMccMncStrings(gsmData.hasPlmn(),
                        gsmData.hasPlmn() ? gsmData.getPlmn().getValue() : null,
                        gsmData.getMcc().getValue(), gsmData.getMnc().getValue());
                return TowerIdentity.towerId(gsmMccMnc[0], gsmMccMnc[1], gsmData.getLac().getValue(), gsmData.getCi().getValue());

            case CDMA:
                // We don't support CDMA since it is pretty much gone
                break;

            case UMTS:
                final UmtsRecordData umtsData = ((UmtsRecord) cellularRecord.cellularRecord).getData();
                String[] umtsMccMnc = NsUtils.extractMccMncStrings(umtsData.hasPlmn(),
                        umtsData.hasPlmn() ? umtsData.getPlmn().getValue() : null,
                        umtsData.getMcc().getValue(), umtsData.getMnc().getValue());
                return TowerIdentity.towerId(umtsMccMnc[0], umtsMccMnc[1], umtsData.getLac().getValue(), umtsData.getCid().getValue());

            case LTE:
                final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                String[] lteMccMnc = NsUtils.extractMccMncStrings(lteData.hasPlmn(),
                        lteData.hasPlmn() ? lteData.getPlmn().getValue() : null,
                        lteData.getMcc().getValue(), lteData.getMnc().getValue());
                return TowerIdentity.towerId(lteMccMnc[0], lteMccMnc[1], lteData.getTac().getValue(), lteData.getEci().getValue());

            case NR:
                final NrRecordData nrData = ((NrRecord) cellularRecord.cellularRecord).getData();
                String[] nrMccMnc = NsUtils.extractMccMncStrings(nrData.hasPlmn(),
                        nrData.hasPlmn() ? nrData.getPlmn().getValue() : null,
                        nrData.getMcc().getValue(), nrData.getMnc().getValue());
                return TowerIdentity.towerId(nrMccMnc[0], nrMccMnc[1], nrData.getTac().getValue(), nrData.getNci().getValue());
        }

        return "";
    }

    public static ServingSignalInfo getSignalInfo(CellularRecordWrapper cellularRecord)
    {
        if (cellularRecord == null || cellularRecord.cellularProtocol == null)
        {
            return null;
        }

        return switch (cellularRecord.cellularProtocol)
        {
            case NONE -> null;
            case GSM ->
            {
                final GsmRecordData gsmData = ((GsmRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.GSM, (int) gsmData.getSignalStrength().getValue(), -1);
            }
            case CDMA ->
            {
                final CdmaRecordData cdmaData = ((CdmaRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.CDMA, ((int) cdmaData.getEcio().getValue()), -1);
            }
            case UMTS ->
            {
                final UmtsRecordData umtsData = ((UmtsRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.UMTS, ((int) umtsData.getSignalStrength().getValue()), (int) umtsData.getRscp().getValue());
            }
            case LTE ->
            {
                final LteRecordData lteData = ((LteRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.LTE, (int) lteData.getRsrp().getValue(), (int) lteData.getRsrq().getValue());
            }
            case NR ->
            {
                final NrRecordData nrData = ((NrRecord) cellularRecord.cellularRecord).getData();
                yield new ServingSignalInfo(CellularProtocol.NR, (int) nrData.getSsRsrp().getValue(), (int) nrData.getSsRsrq().getValue());
            }
        };
    }
}