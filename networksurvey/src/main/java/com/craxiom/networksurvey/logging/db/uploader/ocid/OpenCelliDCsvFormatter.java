package com.craxiom.networksurvey.logging.db.uploader.ocid;

import com.craxiom.networksurvey.logging.db.model.BaseRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper;
import com.craxiom.networksurvey.logging.db.uploader.ObservationTime;
import com.craxiom.networksurvey.util.NsUtils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Builds the CSV file for an OpenCelliD measurement upload (measure/uploadCsv).
 * <p>
 * Each row is filled by column name rather than by position, so a row can never shift a value into
 * the next column. The column meanings follow OpenCelliD's measurement fields: psc is for UMTS
 * only, pci for LTE and NR only, ta for GSM and LTE only, signal is an integer in dBm, rating is
 * the GPS accuracy in meters, speed is in meters per second, measured_at is the observation time
 * (here in milliseconds since the epoch, one of the accepted formats) and devn is at most 50
 * characters. Every number is written locale independently (ASCII digits, a dot for decimals).
 */
public class OpenCelliDCsvFormatter
{
    /**
     * The upload columns in header order. The lowercase name of each constant is the header text.
     */
    enum Column
    {
        LAT, LON, MCC, MNC, SID, LAC, TAC, NID, CELLID, BID, PSC, PCI, SIGNAL, TA, MEASURED_AT,
        RATING, SPEED, DIRECTION, ACT, DEVN;

        /**
         * @return The header text for this column.
         */
        String headerName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final String ACT_GSM = "GSM";
    private static final String ACT_UMTS = "UMTS";
    private static final String ACT_LTE = "LTE";
    private static final String ACT_NR = "NR";
    private static final String LOCATION_FORMAT = "%.6f";
    private static final String SPEED_FORMAT = "%.2f";
    private static final int MAX_DEVICE_NAME_LENGTH = 50;
    private static final String EMPTY = "";
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setRecordSeparator('\n')
            .setHeader(Arrays.stream(Column.values()).map(Column::headerName).toArray(String[]::new))
            .build();

    private OpenCelliDCsvFormatter()
    {
    }

    /**
     * @param recordsWrapper The cellular records to upload.
     * @return The CSV file contents: the header line, then one line per record.
     */
    public static String formatRecords(UploadRecordsWrapper recordsWrapper)
    {
        final String deviceModel = truncate(NsUtils.getDeviceModel());
        final StringBuilder csv = new StringBuilder();

        try (CSVPrinter printer = new CSVPrinter(csv, CSV_FORMAT))
        {
            for (GsmRecordEntity record : recordsWrapper.gsmRecords())
            {
                printer.printRecord((Object[]) formatGsmRecord(record, deviceModel));
            }
            for (UmtsRecordEntity record : recordsWrapper.umtsRecords())
            {
                printer.printRecord((Object[]) formatUmtsRecord(record, deviceModel));
            }
            for (LteRecordEntity record : recordsWrapper.lteRecords())
            {
                printer.printRecord((Object[]) formatLteRecord(record, deviceModel));
            }
            for (NrRecordEntity record : recordsWrapper.nrRecords())
            {
                printer.printRecord((Object[]) formatNrRecord(record, deviceModel));
            }
        } catch (IOException e)
        {
            // A StringBuilder never throws, so this cannot happen.
            throw new UncheckedIOException(e);
        }

        return csv.toString();
    }

    private static String[] formatGsmRecord(GsmRecordEntity record, String deviceModel)
    {
        final String[] row = createRow(record, ACT_GSM, deviceModel);
        set(row, Column.MCC, record.mcc);
        set(row, Column.MNC, record.mnc);
        set(row, Column.LAC, record.lac);
        set(row, Column.CELLID, record.ci);
        row[Column.SIGNAL.ordinal()] = formatSignal(record.signalStrength);
        set(row, Column.TA, record.ta);
        return row;
    }

    private static String[] formatUmtsRecord(UmtsRecordEntity record, String deviceModel)
    {
        final String[] row = createRow(record, ACT_UMTS, deviceModel);
        set(row, Column.MCC, record.mcc);
        set(row, Column.MNC, record.mnc);
        set(row, Column.LAC, record.lac);
        set(row, Column.CELLID, record.cid);
        set(row, Column.PSC, record.psc);
        row[Column.SIGNAL.ordinal()] = formatSignal(record.rscp);
        return row;
    }

    private static String[] formatLteRecord(LteRecordEntity record, String deviceModel)
    {
        final String[] row = createRow(record, ACT_LTE, deviceModel);
        set(row, Column.MCC, record.mcc);
        set(row, Column.MNC, record.mnc);
        set(row, Column.TAC, record.tac);
        set(row, Column.CELLID, record.eci);
        set(row, Column.PCI, record.pci);
        row[Column.SIGNAL.ordinal()] = formatSignal(record.rsrp);
        set(row, Column.TA, record.ta);
        return row;
    }

    private static String[] formatNrRecord(NrRecordEntity record, String deviceModel)
    {
        final String[] row = createRow(record, ACT_NR, deviceModel);
        set(row, Column.MCC, record.mcc);
        set(row, Column.MNC, record.mnc);
        set(row, Column.TAC, record.tac);
        set(row, Column.CELLID, record.nci);
        set(row, Column.PCI, record.pci);
        row[Column.SIGNAL.ordinal()] = formatSignal(record.ssRsrp);
        // OpenCelliD defines ta for GSM and LTE only, so the NR timing advance is not sent.
        return row;
    }

    /**
     * Creates a row with every column empty except the ones every technology shares: location,
     * observation time, accuracy, speed, access technology and device name.
     */
    private static String[] createRow(BaseRecordEntity record, String act, String deviceModel)
    {
        final String[] row = new String[Column.values().length];
        Arrays.fill(row, EMPTY);

        row[Column.LAT.ordinal()] = String.format(Locale.ROOT, LOCATION_FORMAT, record.latitude);
        row[Column.LON.ordinal()] = String.format(Locale.ROOT, LOCATION_FORMAT, record.longitude);
        set(row, Column.MEASURED_AT, ObservationTime.toEpochMillis(record.deviceTime));
        set(row, Column.RATING, record.accuracy);
        if (record.speed != null)
        {
            row[Column.SPEED.ordinal()] = String.format(Locale.ROOT, SPEED_FORMAT, record.speed);
        }
        row[Column.ACT.ordinal()] = act;
        row[Column.DEVN.ordinal()] = deviceModel;

        return row;
    }

    /**
     * Writes an integer value into its column, leaving the cell empty when the value is missing.
     * {@link Long#toString} and {@link Integer#toString} always use ASCII digits.
     */
    private static void set(String[] row, Column column, Number value)
    {
        row[column.ordinal()] = value == null ? EMPTY : value.toString();
    }

    /**
     * @return The signal as whole dBm (OpenCelliD's signal field is an integer), or an empty string
     * if there is none.
     */
    private static String formatSignal(Float signal)
    {
        return signal == null ? EMPTY : String.valueOf(Math.round(signal));
    }

    private static String truncate(String deviceModel)
    {
        if (deviceModel == null) return EMPTY;
        return deviceModel.length() > MAX_DEVICE_NAME_LENGTH ? deviceModel.substring(0, MAX_DEVICE_NAME_LENGTH) : deviceModel;
    }
}
