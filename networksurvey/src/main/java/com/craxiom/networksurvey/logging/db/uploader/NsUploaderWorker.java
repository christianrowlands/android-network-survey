package com.craxiom.networksurvey.logging.db.uploader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao;
import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UploadRecordsWrapper;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;
import com.craxiom.networksurvey.logging.db.uploader.beacondb.BeaconDbUploadClient;
import com.craxiom.networksurvey.logging.db.uploader.ocid.OpenCelliDCsvFormatter;
import com.craxiom.networksurvey.logging.db.uploader.ocid.OpenCelliDUploadClient;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import timber.log.Timber;

/**
 * This class was pulled from the Tower Collector app and modified to work with Network Survey.
 * <p>
 * See: <a href="https://github.com/zamojski/TowerCollector/blob/7c8c4ff7bc2a536a94a34e059189f905ecd52b34/app/src/main/java/info/zamojski/soft/towercollector/uploader/UploaderWorker.java">here</a>
 */
public class NsUploaderWorker extends Worker
{
    public static final String SERVICE_FULL_NAME = NsUploaderWorker.class.getCanonicalName();
    public static final String WORKER_TAG = "NS_UPLOADER_WORKER";
    public static final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv; charset=UTF-8");

    public static final String PROGRESS = "PROGRESS";
    public static final String PROGRESS_MAX = "PROGRESS_MAX";
    public static final String PROGRESS_STATUS_MESSAGE = "PROGRESS_STATUS_MESSAGE";
    public static final int PROGRESS_MIN_VALUE = 0;
    public static final int PROGRESS_MAX_VALUE = 100;
    public static final String OCID_RESULT = "OCID_RESULT";
    public static final String BEACONDB_RESULT = "OCID_RESULT";
    public static final String OCID_RESULT_MESSAGE = "OCID_RESULT_MESSAGE";
    public static final String BEACONDB_RESULT_MESSAGE = "BEACONDB_RESULT_MESSAGE";
    public static final int NOTIFICATION_ID = 102;
    private static final int LOCATIONS_PER_PART = 100; // Batch size for uploads
    public static final String OCID_APP_ID = "NetworkSurvey " + BuildConfig.VERSION_NAME;

    private final NotificationManager notificationManager;
    private final UploaderNotificationHelper notificationHelper;
    private final SurveyDatabase database;

    private boolean isOpenCellIdUploadEnabled;
    private boolean anonymousUploadToOcid;
    private boolean isBeaconDBUploadEnabled;

    public NsUploaderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams)
    {
        super(context, workerParams);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationHelper = new UploaderNotificationHelper(context);
        database = SurveyDatabase.getInstance(context);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        try
        {
            Notification notification = notificationHelper.createNotification(notificationManager);
            notificationManager.notify(NOTIFICATION_ID, notification);

            Timber.d("Starting upload process...");

            // Read work input parameters
            isOpenCellIdUploadEnabled = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, false);
            anonymousUploadToOcid = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, false);
            isBeaconDBUploadEnabled = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, false);
            boolean isRetryEnabled = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, false);

            UploadResultBundle uploadResultBundle = new UploadResultBundle();

            if (!isOpenCellIdUploadEnabled && !isBeaconDBUploadEnabled)
            {
                Timber.d("No upload targets enabled.");
                uploadResultBundle.setResult(UploadTarget.OpenCelliD, UploadResult.UploadDisabledForTarget);
                uploadResultBundle.setResult(UploadTarget.BeaconDB, UploadResult.UploadDisabledForTarget);
                return Result.success(getResultData(uploadResultBundle));
            }

            int totalRecords = getTotalRecordsForUpload(database.surveyRecordDao(), isBeaconDBUploadEnabled);
            if (totalRecords == 0)
            {
                Timber.d("No records to upload.");
                uploadResultBundle.setResult(UploadTarget.OpenCelliD, UploadResult.NoData);
                uploadResultBundle.setResult(UploadTarget.BeaconDB, UploadResult.NoData);
                return Result.success(getResultData(uploadResultBundle));
            }

            int partsCount = (int) Math.ceil((double) totalRecords / LOCATIONS_PER_PART);
            for (int i = 0; i < partsCount; i++)
            {
                if (isStopped())
                {
                    Timber.d("Upload cancelled, stopping upload processing loop");
                    uploadResultBundle.markAllCancelled();
                    return Result.failure(getResultData(uploadResultBundle));
                }

                int progress = (int) (100.0 * i / partsCount);
                reportProgress(progress, PROGRESS_MAX_VALUE, "Uploading records...");

                uploadResultBundle.merge(processUploadBatch(LOCATIONS_PER_PART, isBeaconDBUploadEnabled));
                if (!uploadResultBundle.isAllSuccess())
                {
                    if (isRetryEnabled && uploadResultBundle.isRetryableError())
                    {
                        Timber.d("Upload failed, retry enabled.");
                        reportProgress(progress, PROGRESS_MAX_VALUE, "An error occurred, will retry later");
                        return Result.retry();
                    } else
                    {
                        Timber.e("Upload failed with no retry.");
                        return Result.failure(getResultData(uploadResultBundle));
                    }
                }

                // Progress update
                reportProgress((i + 1) * 100 / partsCount, PROGRESS_MAX_VALUE, "Uploading records...");
            }

            database.surveyRecordDao().deleteAllUploadedRecords();

            Timber.d("Upload process completed.");
            return Result.success(getResultData(uploadResultBundle));
        } catch (Exception e)
        {
            Timber.e(e, "Upload process failed.");
            UploadResultBundle uploadResultBundle = new UploadResultBundle();
            uploadResultBundle.markAllFailure();
            return Result.failure(getResultData(uploadResultBundle));
        } finally
        {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    @Override
    public void onStopped()
    {
        Timber.d("onStopped: Upload cancelled");
        notificationManager.cancel(NOTIFICATION_ID);
        super.onStopped();
    }

    @NonNull
    @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync()
    {
        // getForegroundInfo is needed for Android SDK prior to S (API 31, Android 12). This is
        // not used on later versions of Android.
        Context context = getApplicationContext();
        String channelId = "ns_upload_channel";

        NotificationChannel channel = new NotificationChannel(
                channelId,
                "Upload Worker",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(context, channelId)
                .setContentTitle("Network Survey Upload")
                .setContentText("Uploading data...")
                .setSmallIcon(R.drawable.ic_upload_24)
                .setOngoing(true)
                .build();

        ForegroundInfo foregroundInfo = new ForegroundInfo(1337, notification);
        return Futures.immediateFuture(foregroundInfo);
    }

    private UploadResultBundle processUploadBatch(int batchSize, boolean isBeaconDBUploadEnabled)
    {
        int remainingBatchSize = batchSize;

        // Fetch records from each protocol table while ensuring we don’t exceed batchSize
        List<GsmRecordEntity> gsmRecords = Collections.emptyList();
        if (remainingBatchSize > 0)
        {
            gsmRecords = database.surveyRecordDao().getGsmRecordsForUpload(remainingBatchSize);
            remainingBatchSize -= gsmRecords.size();
        }

        List<CdmaRecordEntity> cdmaRecords = Collections.emptyList();
        if (remainingBatchSize > 0)
        {
            cdmaRecords = database.surveyRecordDao().getCdmaRecordsForUpload(remainingBatchSize);
            remainingBatchSize -= cdmaRecords.size();
        }

        List<UmtsRecordEntity> umtsRecords = Collections.emptyList();
        if (remainingBatchSize > 0)
        {
            umtsRecords = database.surveyRecordDao().getUmtsRecordsForUpload(remainingBatchSize);
            remainingBatchSize -= umtsRecords.size();
        }

        List<LteRecordEntity> lteRecords = Collections.emptyList();
        if (remainingBatchSize > 0)
        {
            lteRecords = database.surveyRecordDao().getLteRecordsForUpload(remainingBatchSize);
            remainingBatchSize -= lteRecords.size();
        }

        List<NrRecordEntity> nrRecords = Collections.emptyList();
        if (remainingBatchSize > 0)
        {
            nrRecords = database.surveyRecordDao().getNrRecordsForUpload(remainingBatchSize);
            remainingBatchSize -= nrRecords.size();
        }

        List<WifiBeaconRecordEntity> wifiRecords = Collections.emptyList();
        if (isBeaconDBUploadEnabled && remainingBatchSize > 0)
        {
            wifiRecords = database.surveyRecordDao().getWifiRecordsForUpload(remainingBatchSize);
            remainingBatchSize -= wifiRecords.size();
        }

        // Create a combined results bundle
        final UploadResultBundle totalResultBundle = new UploadResultBundle();

        if (!gsmRecords.isEmpty())
        {
            totalResultBundle.merge(processUpload(gsmRecords));
        }
        if (!cdmaRecords.isEmpty())
        {
            totalResultBundle.merge(processUpload(cdmaRecords));
        }
        if (!umtsRecords.isEmpty())
        {
            totalResultBundle.merge(processUpload(umtsRecords));
        }
        if (!lteRecords.isEmpty())
        {
            totalResultBundle.merge(processUpload(lteRecords));
        }
        if (!nrRecords.isEmpty())
        {
            totalResultBundle.merge(processUpload(nrRecords));
        }

        if (!wifiRecords.isEmpty())
        {
            totalResultBundle.merge(processUpload(wifiRecords));
        }

        return totalResultBundle;
    }

    private <T> UploadResultBundle processUpload(List<T> records)
    {
        UploadResultBundle result = uploadRecords(records);
        Timber.i("UploadResultBundle OCID=%s, BeaconDB=%s",
                result.getResult(UploadTarget.OpenCelliD),
                result.getResult(UploadTarget.BeaconDB)
        );

        if (result.hasAnyFailures())
        {
            // If there are any failures, don't continue in this method because we don't want to
            // delete any records that were not successfully uploaded.
            return result;
        }

        if (result.getResult(UploadTarget.OpenCelliD) == UploadResult.Success || result.getResult(UploadTarget.OpenCelliD) == UploadResult.UploadDisabledForTarget)
        {
            markRecordsAsUploadedToOcid(records);
        }

        if (result.getResult(UploadTarget.BeaconDB) == UploadResult.Success || result.getResult(UploadTarget.BeaconDB) == UploadResult.UploadDisabledForTarget)
        {
            markRecordsAsUploadedToBeaconDb(records);
        }

        return result;
    }

    private <T> UploadResultBundle uploadRecords(List<T> records)
    {
        final UploadResultBundle uploadResultBundle = new UploadResultBundle();
        try
        {
            final UploadRecordsWrapper recordsWrapper = UploadRecordsWrapper.createRecordsWrapper(records);

            if (isOpenCellIdUploadEnabled)
            {
                final String ocidApiKeyString = PreferenceUtils.getOpenCelliDApiKey(getApplicationContext(), anonymousUploadToOcid);
                RequestBody apiKey = RequestBody.create(ocidApiKeyString, MultipartBody.FORM);
                RequestBody appId = RequestBody.create(OCID_APP_ID, MultipartBody.FORM);

                String csvContent = OpenCelliDCsvFormatter.formatRecords(recordsWrapper);
                RequestBody requestFile = RequestBody.create(csvContent, MEDIA_TYPE_CSV);
                MultipartBody.Part multipartFile = MultipartBody.Part.createFormData("datafile", "NetworkSurvey_measurements_" + System.currentTimeMillis() + ".csv", requestFile);

                OpenCelliDUploadClient ocidClient = OpenCelliDUploadClient.getInstance();
                Response<ResponseBody> response = ocidClient.uploadToOcid(apiKey, appId, multipartFile).execute();
                try (ResponseBody body = response.body())
                {
                    RequestResult requestResult = OpenCelliDUploadClient.handleOcidResponse(response.code(), body);
                    Timber.d("Server response: %s", requestResult);
                    UploadResult uploadResult = OpenCelliDUploadClient.mapRequestResultToUploadResult(requestResult);
                    uploadResultBundle.setResult(UploadTarget.OpenCelliD, uploadResult);
                } catch (Exception e)
                {
                    Timber.e(e, "OpenCelliD upload failed due to exception.");
                    uploadResultBundle.setResult(UploadTarget.OpenCelliD, UploadResult.Failure);
                }
            } else
            {
                Timber.d("OpenCelliD upload not enabled.");
                // When the user does not enable a target, we still need to mark the records so they can be deleted
                uploadResultBundle.setResult(UploadTarget.OpenCelliD, UploadResult.UploadDisabledForTarget);
            }

            if (isBeaconDBUploadEnabled)
            {
                BeaconDbUploadClient beaconDbClient = BeaconDbUploadClient.getInstance();
                Response<ResponseBody> response = beaconDbClient.uploadToBeaconDB(recordsWrapper).execute();
                try (ResponseBody body = response.body())
                {
                    RequestResult requestResult = BeaconDbUploadClient.handleBeaconDbResponse(response.code(), body);
                    Timber.d("Upload to BeaconDB: Server response: %s", requestResult);
                    UploadResult uploadResult = BeaconDbUploadClient.mapRequestResultToUploadResult(requestResult);
                    uploadResultBundle.setResult(UploadTarget.BeaconDB, uploadResult);
                } catch (Exception e)
                {
                    Timber.e(e, "BeaconDB upload failed due to exception.");
                    uploadResultBundle.setResult(UploadTarget.BeaconDB, UploadResult.Failure);
                }
            } else
            {
                Timber.d("BeaconDB upload not enabled.");
                // When the user does not enable a target, we still need to mark the records so they can be deleted
                uploadResultBundle.setResult(UploadTarget.BeaconDB, UploadResult.UploadDisabledForTarget);
            }

            return uploadResultBundle;
        } catch (Exception e)
        {
            Timber.e(e, "Upload failed due to exception.");
            uploadResultBundle.markAllFailure();
            return uploadResultBundle;
        }
    }

    private Data getResultData(UploadResultBundle resultBundle)
    {
        UploadResult ocidResult = resultBundle.getResult(UploadTarget.OpenCelliD);
        UploadResult beaconDbResult = resultBundle.getResult(UploadTarget.BeaconDB);
        Context applicationContext = getApplicationContext();

        return new Data.Builder()
                .putString(OCID_RESULT, applicationContext.getString(UploadResult.getMessage(ocidResult)))
                .putString(BEACONDB_RESULT, applicationContext.getString(UploadResult.getMessage(beaconDbResult)))
                .putString(OCID_RESULT_MESSAGE, applicationContext.getString(ocidResult.getDescription()))
                .putString(BEACONDB_RESULT_MESSAGE, applicationContext.getString(beaconDbResult.getDescription()))
                .build();
    }

    public void reportProgress(int value, int max, String message)
    {
        if (isStopped())
        {
            return;
        }
        setProgressAsync(new Data.Builder()
                .putInt(PROGRESS, value)
                .putInt(PROGRESS_MAX, max)
                .putString(PROGRESS_STATUS_MESSAGE, message)
                .build());
        Notification notification = notificationHelper.updateNotificationProgress(value, max);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private <T> void markRecordsAsUploadedToOcid(List<T> records)
    {
        if (records == null || records.isEmpty()) return;

        if (records.get(0) instanceof WifiBeaconRecordEntity)
        {
            return; // OCID does not support Wifi records
        }

        database.runInTransaction(() -> {
            if (records.get(0) instanceof NrRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((NrRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markNrRecordsAsUploadedToOcid(recordIds);
            } else if (records.get(0) instanceof LteRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((LteRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markLteRecordsAsUploadedToOcid(recordIds);
            } else if (records.get(0) instanceof UmtsRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((UmtsRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markUmtsRecordsAsUploadedToOcid(recordIds);
            } else if (records.get(0) instanceof GsmRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((GsmRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markGsmRecordsAsUploadedToOcid(recordIds);
            } else if (records.get(0) instanceof CdmaRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((CdmaRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markCdmaRecordsAsUploadedToOcid(recordIds);
            }

            Timber.d("%d records marked as uploaded to OCID", records.size());
        });
    }

    private <T> void markRecordsAsUploadedToBeaconDb(List<T> records)
    {
        if (records == null || records.isEmpty()) return;

        database.runInTransaction(() -> {
            if (records.get(0) instanceof NrRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((NrRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markNrRecordsAsUploadedToBeaconDb(recordIds);
            } else if (records.get(0) instanceof LteRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((LteRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markLteRecordsAsUploadedToBeaconDb(recordIds);
            } else if (records.get(0) instanceof UmtsRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((UmtsRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markUmtsRecordsAsUploadedToBeaconDb(recordIds);
            } else if (records.get(0) instanceof GsmRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((GsmRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markGsmRecordsAsUploadedToBeaconDb(recordIds);
            } else if (records.get(0) instanceof CdmaRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((CdmaRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markCdmaRecordsAsUploadedToBeaconDb(recordIds);
            } else if (records.get(0) instanceof WifiBeaconRecordEntity)
            {
                List<Long> recordIds = records.stream().map(record -> ((WifiBeaconRecordEntity) record).id).collect(Collectors.toList());
                database.surveyRecordDao().markWifiRecordsAsUploadedToBeaconDb(recordIds);
            }

            Timber.d("%d records marked as uploaded to BeaconDB", records.size());
        });
    }

    /**
     * Sums up the total number of records to be uploaded for all protocols.
     */
    public static int getTotalRecordsForUpload(SurveyRecordDao surveyRecordDao, boolean isBeaconDBUploadEnabled)
    {
        if (isBeaconDBUploadEnabled)
        {
            return getTotalCellularRecordsForUpload(surveyRecordDao)
                    + surveyRecordDao.getWifiRecordCountForUpload();
        } else
        {
            return getTotalCellularRecordsForUpload(surveyRecordDao);
        }
    }

    /**
     * Sums up the total number of cellular records to be uploaded for all cellular protocols.
     */
    public static int getTotalCellularRecordsForUpload(SurveyRecordDao surveyRecordDao)
    {
        return surveyRecordDao.getGsmRecordCountForUpload()
                + surveyRecordDao.getCdmaRecordCountForUpload()
                + surveyRecordDao.getUmtsRecordCountForUpload()
                + surveyRecordDao.getLteRecordCountForUpload()
                + surveyRecordDao.getNrRecordCountForUpload();
    }
}
