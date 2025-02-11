package com.craxiom.networksurvey.logging.db.uploader.ocid;

import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper;
import com.craxiom.networksurvey.util.NsUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class OpenCelliDCsvFormatter
{
    private static final String CSV_HEADER = "lat,lon,mcc,mnc,sid,lac,tac,nid,cellid,bid,psc,pci,signal,ta,measured_at,rating,speed,direction,act,devn";
    private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_FORMATTER = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });
    private static final String DEVICE_MODEL = NsUtils.getDeviceModel();

    public static String formatRecords(UploadRecordsWrapper recordsWrapper)
    {
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append(CSV_HEADER).append("\n");

        for (GsmRecordEntity record : recordsWrapper.gsmRecords())
        {
            csvBuilder.append(formatGsmRecord(record)).append("\n");
        }
        for (UmtsRecordEntity record : recordsWrapper.umtsRecords())
        {
            csvBuilder.append(formatUmtsRecord(record)).append("\n");
        }
        for (LteRecordEntity record : recordsWrapper.lteRecords())
        {
            csvBuilder.append(formatLteRecord(record)).append("\n");
        }
        for (NrRecordEntity record : recordsWrapper.nrRecords())
        {
            csvBuilder.append(formatNrRecord(record)).append("\n");
        }

        return csvBuilder.toString();
    }

    private static String formatGsmRecord(GsmRecordEntity record)
    {
        return String.format(Locale.US,
                "%.6f,%.6f,%d,%d,,%d,,,%d,,,,%s,,%s,%d,%.2f,,%s,\"%s\"",
                record.latitude, record.longitude, record.mcc, record.mnc,
                record.lac, record.ci, formatSignal(record.signalStrength),
                formatTimestamp(record.deviceTime), record.accuracy, record.speed, "GSM", DEVICE_MODEL
        );
    }

    private static String formatUmtsRecord(UmtsRecordEntity record)
    {
        return String.format(Locale.US,
                "%.6f,%.6f,%d,%d,,%d,,,%d,%d,,%s,,%s,%d,%.2f,,%s,\"%s\"",
                record.latitude, record.longitude, record.mcc, record.mnc,
                record.lac, record.cid, record.psc, formatSignal(record.rscp),
                formatTimestamp(record.deviceTime), record.accuracy, record.speed, "UMTS", DEVICE_MODEL
        );
    }

    private static String formatLteRecord(LteRecordEntity record)
    {
        return String.format(Locale.US,
                "%.6f,%.6f,%d,%d,,,%d,,%d,,,%d,%s,%d,%s,%d,%.2f,,%s,\"%s\"",
                record.latitude, record.longitude, record.mcc, record.mnc,
                record.tac, record.eci, record.pci, formatSignal(record.rsrp),
                record.ta, formatTimestamp(record.deviceTime), record.accuracy, record.speed, "LTE", DEVICE_MODEL
        );
    }

    private static String formatNrRecord(NrRecordEntity record)
    {
        return String.format(Locale.US,
                "%.6f,%.6f,%d,%d,,,%d,,%d,,,%d,%s,%d,%s,%d,%.2f,,%s,\"%s\"",
                record.latitude, record.longitude, record.mcc, record.mnc,
                record.tac, record.nci, record.pci, formatSignal(record.ssRsrp),
                record.ta, formatTimestamp(record.deviceTime), record.accuracy, record.speed, "NR", DEVICE_MODEL
        );
    }

    private static String formatSignal(Float signal)
    {
        return signal != null ? String.valueOf(signal) : "";
    }

    private static String formatTimestamp(String timestamp)
    {
        return timestamp != null ? timestamp : TIMESTAMP_FORMATTER.get().format(new Date());
    }
}
