package com.craxiom.networksurvey.logging.db;

import android.content.Context;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.PhoneState;
import com.craxiom.networksurvey.constants.NsAnalyticsConstants;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsQueueEntity;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.google.protobuf.util.JsonFormat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * Data store for NS Analytics that collects and stores complete protobuf survey records.
 * Unlike DbUploadStore which filters records, this stores complete messages for NS Analytics.
 */
public class NsAnalyticsDataStore implements ICellularSurveyRecordListener, IWifiSurveyRecordListener, IBluetoothSurveyRecordListener, IGnssSurveyRecordListener, IDeviceStatusListener
{
    private final SurveyDatabase database;
    private final ExecutorService executorService;
    private final JsonFormat.Printer jsonPrinter;
    private long nsAnalyticsSurveyStartTime = 0;
    private String currentBatchId; // FIXME Is this batch ID needed?

    // Counters for statistics
    private long totalRecordsStored = 0;
    private long lastRecordTimestamp = 0;

    public NsAnalyticsDataStore(Context context)
    {
        database = SurveyDatabase.getInstance(context);
        executorService = Executors.newSingleThreadExecutor();

        // Configure JSON printer for protobuf serialization
        jsonPrinter = JsonFormat.printer()
                .preservingProtoFieldNames();

        // Generate initial batch ID
        generateNewBatchId();
    }

    /**
     * Start collecting records for NS Analytics
     */
    public void startCollecting()
    {
        nsAnalyticsSurveyStartTime = System.currentTimeMillis();
        generateNewBatchId();
    }

    /**
     * Shutdown the data store
     */
    public void shutdown()
    {
        nsAnalyticsSurveyStartTime = 0;
        executorService.shutdown();
    }

    /**
     * Generate a new batch ID for grouping records
     */
    public final void generateNewBatchId()
    {
        currentBatchId = UUID.randomUUID().toString();
    }

    @Override
    public void onCellularBatch(List<CellularRecordWrapper> cellularGroup, int subscriptionId)
    {
        if (cellularGroup == null || cellularGroup.isEmpty())
        {
            return;
        }

        for (CellularRecordWrapper wrapper : cellularGroup)
        {
            String recordType;
            switch (wrapper.cellularProtocol)
            {
                case GSM:
                    recordType = NsAnalyticsConstants.RECORD_TYPE_GSM;
                    break;
                case CDMA:
                    recordType = NsAnalyticsConstants.RECORD_TYPE_CDMA;
                    break;
                case UMTS:
                    recordType = NsAnalyticsConstants.RECORD_TYPE_UMTS;
                    break;
                case LTE:
                    recordType = NsAnalyticsConstants.RECORD_TYPE_LTE;
                    break;
                case NR:
                    recordType = NsAnalyticsConstants.RECORD_TYPE_NR;
                    break;
                default:
                    Timber.w("Unknown cellular protocol: %s", wrapper.cellularProtocol);
                    continue;
            }

            storeRecord(recordType, wrapper.cellularRecord);
        }
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (wifiBeaconRecords == null || wifiBeaconRecords.isEmpty())
        {
            return;
        }

        for (WifiRecordWrapper wrapper : wifiBeaconRecords)
        {
            if (wrapper.getWifiBeaconRecord() != null)
            {
                storeRecord(NsAnalyticsConstants.RECORD_TYPE_WIFI, wrapper.getWifiBeaconRecord());
            }
        }
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        if (bluetoothRecord == null)
        {
            return;
        }

        storeRecord(NsAnalyticsConstants.RECORD_TYPE_BLUETOOTH, bluetoothRecord);
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        if (bluetoothRecords == null || bluetoothRecords.isEmpty())
        {
            return;
        }

        for (BluetoothRecord bluetoothRecord : bluetoothRecords)
        {
            if (bluetoothRecord != null)
            {
                storeRecord(NsAnalyticsConstants.RECORD_TYPE_BLUETOOTH, bluetoothRecord);
            }
        }
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        if (gnssRecord == null)
        {
            return;
        }

        storeRecord(NsAnalyticsConstants.RECORD_TYPE_GNSS, gnssRecord);
    }

    @Override
    public void onDeviceStatus(DeviceStatus status)
    {
        if (status == null)
        {
            return;
        }

        storeRecord(NsAnalyticsConstants.RECORD_TYPE_DEVICE_STATUS, status);
    }

    @Override
    public void onPhoneState(PhoneState phoneState)
    {
        if (phoneState == null)
        {
            return;
        }

        storeRecord(NsAnalyticsConstants.RECORD_TYPE_PHONE_STATE, phoneState);
    }

    public long getNsAnalyticsSurveyStartTime()
    {
        return nsAnalyticsSurveyStartTime;
    }

    /**
     * Store a protobuf record in the database
     */
    private void storeRecord(String recordType, com.google.protobuf.Message message)
    {
        executorService.execute(() -> {
            try
            {
                // Convert protobuf to JSON
                String jsonString = jsonPrinter.print(message);

                // Create entity
                NsAnalyticsQueueEntity entity = new NsAnalyticsQueueEntity();
                entity.recordType = recordType;
                entity.protobufJson = jsonString;
                entity.timestamp = System.currentTimeMillis();
                entity.batchId = currentBatchId;
                entity.payloadSize = jsonString.length();

                // Store in database
                if (database.isOpen())
                {
                    database.nsAnalyticsDao().insertRecord(entity);
                    totalRecordsStored++;
                    lastRecordTimestamp = entity.timestamp;
                }
            } catch (Exception e)
            {
                Timber.e(e, "Failed to store NS Analytics record of type %s", recordType);
            }
        });
    }

    /**
     * Get statistics about stored records
     */
    public NsAnalyticsStats getStatistics() // FIXME Call this or remove it
    {
        NsAnalyticsStats stats = new NsAnalyticsStats();
        stats.totalRecordsStored = totalRecordsStored;
        stats.lastRecordTimestamp = lastRecordTimestamp;
        stats.currentBatchId = currentBatchId;

        try
        {
            if (database.isOpen())
            {
                stats.pendingRecordCount = database.nsAnalyticsDao().getPendingRecordCount();
                Long totalSize = database.nsAnalyticsDao().getTotalPendingPayloadSize();
                stats.totalPendingPayloadSize = totalSize != null ? totalSize : 0L;
            }
        } catch (Exception e)
        {
            Timber.e(e, "Failed to get NS Analytics statistics");
        }

        return stats;
    }

    /**
     * Clean up old uploaded records
     */
    public void cleanupOldRecords() // FIXME Call this or remove it
    {
        executorService.execute(() -> {
            try
            {
                long cutoffTime = System.currentTimeMillis() -
                        (NsAnalyticsConstants.CLEANUP_AGE_DAYS * 24 * 60 * 60 * 1000L);

                if (database.isOpen())
                {
                    database.nsAnalyticsDao().cleanupOldUploadedRecords(cutoffTime);
                    database.nsAnalyticsDao().deleteFailedRecords(NsAnalyticsConstants.MAX_RETRY_COUNT);

                    Timber.i("Cleaned up old NS Analytics records");
                }
            } catch (Exception e)
            {
                Timber.e(e, "Failed to cleanup old NS Analytics records");
            }
        });
    }

    /**
     * Statistics class for NS Analytics data
     */
    public static class NsAnalyticsStats
    {
        public long totalRecordsStored;
        public long lastRecordTimestamp;
        public String currentBatchId;
        public int pendingRecordCount;
        public long totalPendingPayloadSize;

        public String getPayloadSizeFormatted()
        {
            if (totalPendingPayloadSize < 1024)
            {
                return totalPendingPayloadSize + " B";
            } else if (totalPendingPayloadSize < 1024 * 1024)
            {
                return String.format("%.2f KB", totalPendingPayloadSize / 1024.0);
            } else
            {
                return String.format("%.2f MB", totalPendingPayloadSize / (1024.0 * 1024));
            }
        }
    }
}