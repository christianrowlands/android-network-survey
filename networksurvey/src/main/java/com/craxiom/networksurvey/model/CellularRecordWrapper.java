package com.craxiom.networksurvey.model;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.util.NsUtils;
import com.google.protobuf.GeneratedMessage;

import java.util.Objects;

/**
 * Wraps the various cellular records so that we can include a variable that specifies which record type it is.
 *
 * @since 1.6.0
 */
public class CellularRecordWrapper
{
    public final CellularProtocol cellularProtocol;
    public final GeneratedMessage cellularRecord;
    private final int hash;
    private final String comparableString;

    public CellularRecordWrapper(CellularProtocol cellularProtocol, GeneratedMessage cellularRecord)
    {
        this.cellularProtocol = cellularProtocol;
        this.cellularRecord = cellularRecord;

        comparableString = getComparableString(this);
        hash = Objects.hash(cellularProtocol, comparableString);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CellularRecordWrapper that = (CellularRecordWrapper) o;

        if (cellularProtocol != that.cellularProtocol) return false;
        return comparableString.equals(that.comparableString);
    }

    @Override
    public int hashCode()
    {
        return hash;
    }

    /**
     * @return The PLMN associated with the cellular record.
     */
    public Plmn getPlmn()
    {
        return switch (cellularProtocol)
        {
            case GSM ->
            {
                GsmRecordData gsmData = ((GsmRecord) cellularRecord).getData();
                String mncStr = NsUtils.extractMncFromPlmnOrNull(gsmData.hasPlmn() ? gsmData.getPlmn().getValue() : null);
                yield new Plmn(gsmData.getMcc().getValue(), gsmData.getMnc().getValue(), mncStr);
            }
            case CDMA ->
            {
                CdmaRecordData cdmaData = ((CdmaRecord) cellularRecord).getData();
                yield new Plmn(cdmaData.getSid().getValue(), cdmaData.getNid().getValue());
            }
            case UMTS ->
            {
                UmtsRecordData umtsData = ((UmtsRecord) cellularRecord).getData();
                String mncStr = NsUtils.extractMncFromPlmnOrNull(umtsData.hasPlmn() ? umtsData.getPlmn().getValue() : null);
                yield new Plmn(umtsData.getMcc().getValue(), umtsData.getMnc().getValue(), mncStr);
            }
            case LTE ->
            {
                LteRecordData lteData = ((LteRecord) cellularRecord).getData();
                String mncStr = NsUtils.extractMncFromPlmnOrNull(lteData.hasPlmn() ? lteData.getPlmn().getValue() : null);
                yield new Plmn(lteData.getMcc().getValue(), lteData.getMnc().getValue(), mncStr);
            }
            case NR ->
            {
                NrRecordData nrData = ((NrRecord) cellularRecord).getData();
                String mncStr = NsUtils.extractMncFromPlmnOrNull(nrData.hasPlmn() ? nrData.getPlmn().getValue() : null);
                yield new Plmn(nrData.getMcc().getValue(), nrData.getMnc().getValue(), mncStr);
            }
            default -> new Plmn(0, 0);
        };
    }

    private static String getComparableString(CellularRecordWrapper wrapper)
    {
        return switch (wrapper.cellularProtocol)
        {
            case GSM ->
            {
                GsmRecordData gsmData = ((GsmRecord) wrapper.cellularRecord).getData();
                String[] gsmMccMnc = NsUtils.extractMccMncStrings(gsmData.hasPlmn(),
                        gsmData.hasPlmn() ? gsmData.getPlmn().getValue() : null,
                        gsmData.getMcc().getValue(), gsmData.getMnc().getValue());
                yield gsmMccMnc[0] + gsmMccMnc[1] + gsmData.getLac().getValue() + gsmData.getCi().getValue();
            }
            case UMTS ->
            {
                UmtsRecordData umtsData = ((UmtsRecord) wrapper.cellularRecord).getData();
                String[] umtsMccMnc = NsUtils.extractMccMncStrings(umtsData.hasPlmn(),
                        umtsData.hasPlmn() ? umtsData.getPlmn().getValue() : null,
                        umtsData.getMcc().getValue(), umtsData.getMnc().getValue());
                yield umtsMccMnc[0] + umtsMccMnc[1] + umtsData.getLac().getValue() + umtsData.getCid().getValue();
            }
            case LTE ->
            {
                LteRecordData lteData = ((LteRecord) wrapper.cellularRecord).getData();
                String[] lteMccMnc = NsUtils.extractMccMncStrings(lteData.hasPlmn(),
                        lteData.hasPlmn() ? lteData.getPlmn().getValue() : null,
                        lteData.getMcc().getValue(), lteData.getMnc().getValue());
                yield lteMccMnc[0] + lteMccMnc[1] + lteData.getTac().getValue() + lteData.getEci().getValue();
            }
            case NR ->
            {
                NrRecordData nrData = ((NrRecord) wrapper.cellularRecord).getData();
                String[] nrMccMnc = NsUtils.extractMccMncStrings(nrData.hasPlmn(),
                        nrData.hasPlmn() ? nrData.getPlmn().getValue() : null,
                        nrData.getMcc().getValue(), nrData.getMnc().getValue());
                yield nrMccMnc[0] + nrMccMnc[1] + nrData.getTac().getValue() + nrData.getNci().getValue();
            }
            default -> "";
        };
    }
}
