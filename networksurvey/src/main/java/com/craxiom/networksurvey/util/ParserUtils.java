package com.craxiom.networksurvey.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.craxiom.messaging.NetworkRegistrationInfo;
import com.craxiom.messaging.phonestate.Domain;
import com.craxiom.messaging.phonestate.NetworkType;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;

import timber.log.Timber;

/**
 * Some basic utility methods to assist with parsing and converting the values used in this app.
 *
 * @since 1.4.0
 */
public class ParserUtils
{
    public static final String RSSI_KEY = "ss=";
    public static final String RSCP_KEY = "rscp=";
    public static final String REJECT_CAUSE_KEY = "rejectCause=";

    private ParserUtils()
    {
    }

    /**
     * Attempts to pull an int value from a String. The string is first checked to see if it is empty or null before
     * attempting to use Integer.parseInt().
     *
     * @param value        The String value to parse as an int.
     * @param defaultValue The return value of this method if an int could not be extracted from the String.
     * @return Returns the provided {@code defaultValue} if the provided value is null or empty, or a
     * NumberFormatException occurs while using the Integer.parseInt() method, otherwise, the int value is returned.
     */
    public static int parseInt(String value, int defaultValue)
    {
        if ((value != null) && (!value.isEmpty()))
        {
            try
            {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored)
            {
            }
        }
        return defaultValue;
    }

    /**
     * Given an Android created Network Registration Info object, create a NS Messaging API Network Registration Info
     * object.
     *
     * @param info The Android object with all the registration info.
     * @return The NS Messaging API Network Registration Info object.
     */
    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static NetworkRegistrationInfo convertNetworkInfo(android.telephony.NetworkRegistrationInfo info)
    {
        int rejectCause;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        {
            rejectCause = info.getRejectCause();
        } else
        {
            rejectCause = extractIntFromToString(info.toString(), REJECT_CAUSE_KEY);
        }

        return convertNetworkInfo(info.getCellIdentity(), info.getDomain(), rejectCause,
                info.getAccessNetworkTechnology(), info.isRoaming());
    }

    /**
     * This javadoc has been taken and modified from
     * {@link android.telephony.PhoneStateListener#onRegistrationFailed(CellIdentity, String, int, int, int)}.
     *
     * @param cellIdentity the CellIdentity, which must include the globally unique identifier
     *                     for the cell (for example, all components of the CGI or ECGI).
     * @param domain       DOMAIN_CS, DOMAIN_PS or both in case of a combined procedure.
     * @param causeCode    the primary failure cause code of the procedure.
     *                     For GSM/UMTS (MM), values are in TS 24.008 Sec 10.5.95
     *                     For GSM/UMTS (GMM), values are in TS 24.008 Sec 10.5.147
     *                     For LTE (EMM), cause codes are TS 24.301 Sec 9.9.3.9
     *                     For NR (5GMM), cause codes are TS 24.501 Sec 9.11.3.2
     *                     Integer.MAX_VALUE if this value is unused.
     */
    public static NetworkRegistrationInfo convertNetworkInfo(@NonNull CellIdentity cellIdentity, int domain, int causeCode)
    {
        return convertNetworkInfo(cellIdentity, domain, causeCode, Integer.MAX_VALUE, null);
    }

    /**
     * This javadoc has been taken and modified from
     * {@link android.telephony.PhoneStateListener#onRegistrationFailed(CellIdentity, String, int, int, int)}.
     *
     * @param cellIdentity            the CellIdentity, which must include the globally unique identifier
     *                                for the cell (for example, all components of the CGI or ECGI).
     * @param domain                  DOMAIN_CS, DOMAIN_PS or both in case of a combined procedure.
     * @param causeCode               the primary failure cause code of the procedure.
     *                                For GSM/UMTS (MM), values are in TS 24.008 Sec 10.5.95
     *                                For GSM/UMTS (GMM), values are in TS 24.008 Sec 10.5.147
     *                                For LTE (EMM), cause codes are TS 24.301 Sec 9.9.3.9
     *                                For NR (5GMM), cause codes are TS 24.501 Sec 9.11.3.2
     *                                Integer.MAX_VALUE if this value is unused.
     * @param accessNetworkTechnology The access network technology {@link NetworkType}. Integer.MAX_VALUE if this value is unused.
     * @param roaming                 True if roaming, false if not roaming, and null if unknown.
     */
    private static NetworkRegistrationInfo convertNetworkInfo(CellIdentity cellIdentity, int domain,
                                                              int causeCode, int accessNetworkTechnology, @Nullable Boolean roaming)
    {
        final NetworkRegistrationInfo.Builder regInfoBuilder = NetworkRegistrationInfo.newBuilder();

        final Domain domainEnum = DeviceStatusMessageConstants.convertDomain(domain);
        if (domainEnum != Domain.UNKNOWN) regInfoBuilder.setDomain(domainEnum);

        if (accessNetworkTechnology != Integer.MAX_VALUE)
        {
            regInfoBuilder.setAccessNetworkTechnology(NetworkType.forNumber(accessNetworkTechnology));
        }

        if (roaming != null)
        {
            regInfoBuilder.setRoaming(BoolValue.newBuilder().setValue(roaming).build());
        }

        if (causeCode != Integer.MAX_VALUE)
        {
            regInfoBuilder.setRejectCause(Int32Value.newBuilder().setValue(causeCode).build());
        }

        // For whatever reason, casting a cellIdentity object requires Android 8 or higher
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            if (cellIdentity instanceof CellIdentityGsm)
            {
                final CellIdentityGsm cellIdentityGsm = (CellIdentityGsm) cellIdentity;

                com.craxiom.messaging.CellIdentityGsm.Builder builder = com.craxiom.messaging.CellIdentityGsm.newBuilder();

                final int mcc = parseInt(cellIdentityGsm.getMccString(), -1);
                if (mcc != -1) builder.setMcc(Int32Value.newBuilder().setValue(mcc).build());

                final int mnc = parseInt(cellIdentityGsm.getMncString(), -1);
                if (mnc != -1) builder.setMnc(Int32Value.newBuilder().setValue(mnc).build());

                final int lac = cellIdentityGsm.getLac();
                if (lac != CellInfo.UNAVAILABLE)
                {
                    builder.setLac(Int32Value.newBuilder().setValue(lac).build());
                }

                final int cid = cellIdentityGsm.getCid();
                if (cid != CellInfo.UNAVAILABLE)
                {
                    builder.setCi(Int32Value.newBuilder().setValue(cid).build());
                }

                final int arfcn = cellIdentityGsm.getArfcn();
                if (arfcn != CellInfo.UNAVAILABLE)
                {
                    builder.setArfcn(Int32Value.newBuilder().setValue(arfcn).build());
                }

                final int bsic = cellIdentityGsm.getBsic();
                if (bsic != CellInfo.UNAVAILABLE)
                {
                    builder.setBsic(Int32Value.newBuilder().setValue(bsic).build());
                }

                regInfoBuilder.setCellIdentityGsm(builder);
            } else if (cellIdentity instanceof CellIdentityCdma)
            {
                final CellIdentityCdma cellIdentityCdma = (CellIdentityCdma) cellIdentity;

                com.craxiom.messaging.CellIdentityCdma.Builder builder = com.craxiom.messaging.CellIdentityCdma.newBuilder();

                final int sid = cellIdentityCdma.getSystemId();
                if (sid != CellInfo.UNAVAILABLE)
                {
                    builder.setSid(Int32Value.newBuilder().setValue(sid).build());
                }

                final int nid = cellIdentityCdma.getNetworkId();
                if (nid != CellInfo.UNAVAILABLE)
                {
                    builder.setNid(Int32Value.newBuilder().setValue(nid).build());
                }

                final int bsid = cellIdentityCdma.getBasestationId();
                if (bsid != CellInfo.UNAVAILABLE)
                {
                    builder.setBsid(Int32Value.newBuilder().setValue(bsid).build());
                }

                regInfoBuilder.setCellIdentityCdma(builder);
            } else if (cellIdentity instanceof CellIdentityWcdma)
            {
                final CellIdentityWcdma cellIdentityWcdma = (CellIdentityWcdma) cellIdentity;

                com.craxiom.messaging.CellIdentityUmts.Builder builder = com.craxiom.messaging.CellIdentityUmts.newBuilder();

                final int mcc = parseInt(cellIdentityWcdma.getMccString(), -1);
                if (mcc != -1) builder.setMcc(Int32Value.newBuilder().setValue(mcc).build());

                final int mnc = parseInt(cellIdentityWcdma.getMncString(), -1);
                if (mnc != -1) builder.setMnc(Int32Value.newBuilder().setValue(mnc).build());

                final int lac = cellIdentityWcdma.getLac();
                if (lac != CellInfo.UNAVAILABLE)
                {
                    builder.setLac(Int32Value.newBuilder().setValue(lac).build());
                }

                final int cid = cellIdentityWcdma.getCid();
                if (cid != CellInfo.UNAVAILABLE)
                {
                    builder.setCid(Int32Value.newBuilder().setValue(cid).build());
                }

                final int uarfcn = cellIdentityWcdma.getUarfcn();
                if (uarfcn != CellInfo.UNAVAILABLE)
                {
                    builder.setUarfcn(Int32Value.newBuilder().setValue(uarfcn).build());
                }

                final int psc = cellIdentityWcdma.getPsc();
                if (psc != CellInfo.UNAVAILABLE)
                {
                    builder.setPsc(Int32Value.newBuilder().setValue(psc).build());
                }

                regInfoBuilder.setCellIdentityUmts(builder);
            } else if (cellIdentity instanceof CellIdentityLte)
            {
                final CellIdentityLte cellIdentityLte = (CellIdentityLte) cellIdentity;

                com.craxiom.messaging.CellIdentityLte.Builder builder = com.craxiom.messaging.CellIdentityLte.newBuilder();

                final int mcc = parseInt(cellIdentityLte.getMccString(), -1);
                if (mcc != -1) builder.setMcc(Int32Value.newBuilder().setValue(mcc).build());

                final int mnc = parseInt(cellIdentityLte.getMncString(), -1);
                if (mnc != -1) builder.setMnc(Int32Value.newBuilder().setValue(mnc).build());

                final int tac = cellIdentityLte.getTac();
                if (tac != CellInfo.UNAVAILABLE)
                {
                    builder.setTac(Int32Value.newBuilder().setValue(tac).build());
                }

                final int eci = cellIdentityLte.getCi();
                if (eci != CellInfo.UNAVAILABLE)
                {
                    builder.setEci(Int32Value.newBuilder().setValue(eci).build());
                }

                final int earfcn = cellIdentityLte.getEarfcn();
                if (earfcn != CellInfo.UNAVAILABLE)
                {
                    builder.setEarfcn(Int32Value.newBuilder().setValue(earfcn).build());
                }

                final int pci = cellIdentityLte.getPci();
                if (pci != CellInfo.UNAVAILABLE)
                {
                    builder.setPci(Int32Value.newBuilder().setValue(pci).build());
                }

                regInfoBuilder.setCellIdentityLte(builder);
            } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellIdentity instanceof CellIdentityNr)
            {
                final CellIdentityNr cellIdentityNr = (CellIdentityNr) cellIdentity;

                com.craxiom.messaging.CellIdentityNr.Builder builder = com.craxiom.messaging.CellIdentityNr.newBuilder();

                final int mcc = parseInt(cellIdentityNr.getMccString(), -1);
                if (mcc != -1) builder.setMcc(Int32Value.newBuilder().setValue(mcc).build());

                final int mnc = parseInt(cellIdentityNr.getMncString(), -1);
                if (mnc != -1) builder.setMnc(Int32Value.newBuilder().setValue(mnc).build());

                final int tac = cellIdentityNr.getTac();
                if (tac != CellInfo.UNAVAILABLE)
                {
                    builder.setTac(Int32Value.newBuilder().setValue(tac).build());
                }

                final long nci = cellIdentityNr.getNci();
                if (nci != CellInfo.UNAVAILABLE)
                {
                    builder.setNci(Int64Value.newBuilder().setValue(nci).build());
                }

                final int narfcn = cellIdentityNr.getNrarfcn();
                if (narfcn != CellInfo.UNAVAILABLE)
                {
                    builder.setNarfcn(Int32Value.newBuilder().setValue(narfcn).build());
                }

                final int pci = cellIdentityNr.getPci();
                if (pci != CellInfo.UNAVAILABLE)
                {
                    builder.setPci(Int32Value.newBuilder().setValue(pci).build());
                }

                regInfoBuilder.setCellIdentityNr(builder);
            }
        }

        return regInfoBuilder.build();
    }

    /**
     * Given a string, look for the key `rejectCause=`, take the value after it, convert it to an int, and return it.
     * In other words, extract the reject cause value from the provided string.
     *
     * @param infoString The string to look for the reject cause in.
     * @return {@link Integer#MAX_VALUE} if the reject cause could not be found, or the reject cause value if it could
     * be extracted from the provided string.
     */
    protected static int extractRejectCause(String infoString)
    {
        try
        {
            final String rejectCauseKey = REJECT_CAUSE_KEY;
            final int rejectCauseIndex = infoString.indexOf(rejectCauseKey);
            if (rejectCauseIndex == -1) return Integer.MAX_VALUE;

            final int endRejectCauseIndex = infoString.indexOf(' ', rejectCauseIndex);
            if (endRejectCauseIndex == -1) return Integer.MAX_VALUE;

            final String rejectCause = infoString.substring(rejectCauseIndex + rejectCauseKey.length(), endRejectCauseIndex);

            return parseInt(rejectCause, Integer.MAX_VALUE);
        } catch (Throwable t)
        {
            Timber.e(t, "Could not get the rejectCause from the NetworkRegistrationInfo toString method");
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Given a string, look for the key `isUsingCarrierAggregation=`, take the value after it, convert it to a boolean,
     * and return it.
     *
     * @param infoString The string to look for the carrier aggregation in.
     * @return null if the carrier aggregation could not be found, or the carrier aggregation value if it could be
     * extracted from the provided string.
     */
    public static Boolean extractCarrierAggregationFromString(String infoString)
    {
        try
        {
            final String carrierAggregationKey = "isUsingCarrierAggregation=";
            final int carrierAggregationIndex = infoString.indexOf(carrierAggregationKey);
            if (carrierAggregationIndex == -1) return null;

            final int endCarrierAggregationIndex = infoString.indexOf(' ', carrierAggregationIndex);
            if (endCarrierAggregationIndex == -1) return null;

            final String isCaString = infoString.substring(carrierAggregationIndex + carrierAggregationKey.length(), endCarrierAggregationIndex);
            return Boolean.parseBoolean(isCaString);
        } catch (Throwable t)
        {
            Timber.e(t, "Could not get the carrier aggregation from the NetworkRegistrationInfo toString method");
            return null;
        }
    }

    /**
     * Given a string, look for the provided key, take the value after it, convert it to an int, and return it.
     * In other words, extract the value of interest from the provided toString.
     *
     * @param toString The string to look for the value in.
     * @return {@link Integer#MAX_VALUE} if the value could not be found, or the int value if it could
     * be extracted from the provided string.
     * @since 1.6.0
     */
    public static int extractIntFromToString(String toString, String valueKey)
    {
        try
        {
            final int rssiIndex = toString.indexOf(valueKey);
            if (rssiIndex == -1) return Integer.MAX_VALUE;

            final int endRssiIndex = toString.indexOf(' ', rssiIndex);
            if (endRssiIndex == -1) return Integer.MAX_VALUE;

            final String rssiString = toString.substring(rssiIndex + valueKey.length(), endRssiIndex);

            return parseInt(rssiString, Integer.MAX_VALUE);
        } catch (Throwable t)
        {
            Timber.e(t, "Could not get the int value from the provided toString");
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Converts a BSIC in decimal form (0 to 63), and converts it to a String in the format of "NCC-BCC" (e.g. 35 is
     * converted to "4-3").
     *
     * @param bsic The BSIC in decimal form to be converted to octal.
     * @return The octal representation of the BSIC in the form of "NCC-BCC", or an empty string if the BSIC is out
     * of range.
     * @since 1.6.0
     */
    public static String bsicToString(int bsic)
    {
        if (bsic < 0 || bsic > 63)
        {
            Timber.e("BSIC is not in the rage of 0 to 63 %s", bsic);
            return "";
        }

        // BSIC is displayed as a base 8 number in the format X-X
        int upperValue = bsic / 8;
        int lowerValue = bsic % 8;

        return upperValue + "-" + lowerValue;
    }
}
