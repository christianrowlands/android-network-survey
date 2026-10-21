package com.craxiom.networksurvey.logging.db.uploader;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.logging.db.SurveyedPointStore;
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao;
import com.craxiom.networksurvey.util.OpenCelliDSigning;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import okhttp3.MediaType;
import timber.log.Timber;

/**
 * This class was pulled from the Tower Collector app and modified to work with Network Survey.
 * <p>
 * See: <a href="https://github.com/zamojski/TowerCollector/blob/7c8c4ff7bc2a536a94a34e059189f905ecd52b34/app/src/main/java/info/zamojski/soft/towercollector/uploader/UploaderWorker.java">here</a>
 */
public class NsUploaderWorker extends Worker
{
    public static final String WORKER_TAG = "NS_UPLOADER_WORKER";
    public static final MediaType MEDIA_TYPE_CSV = MediaType.parse("text/csv; charset=UTF-8");

    public static final String PROGRESS = "PROGRESS";
    public static final String PROGRESS_MAX = "PROGRESS_MAX";
    public static final String PROGRESS_STATUS_MESSAGE = "PROGRESS_STATUS_MESSAGE";
    public static final int PROGRESS_MIN_VALUE = 0;
    public static final int PROGRESS_MAX_VALUE = 100;
    public static final String OCID_RESULT = "OCID_RESULT";
    public static final String BEACONDB_RESULT = "BEACONDB_RESULT";
    public static final String OCID_RESULT_MESSAGE = "OCID_RESULT_MESSAGE";
    public static final String BEACONDB_RESULT_MESSAGE = "BEACONDB_RESULT_MESSAGE";

    public static final String INPUT_SOURCE = "INPUT_SOURCE";
    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_AUTO = "auto";
    public static final String OCID_RESULT_ENUM = "OCID_RESULT_ENUM";
    public static final String BEACONDB_RESULT_ENUM = "BEACONDB_RESULT_ENUM";
    public static final int NOTIFICATION_ID = NetworkSurveyConstants.UPLOADER_NOTIFICATION_ID;
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
        final long uploadStartTime = System.currentTimeMillis();
        try
        {
            Notification notification = notificationHelper.createNotification(notificationManager);
            notificationManager.notify(NOTIFICATION_ID, notification);

            String source = getInputData().getString(INPUT_SOURCE);
            Timber.d("Starting upload process (source: %s)...", source != null ? source : SOURCE_MANUAL);

            // Read work input parameters
            isOpenCellIdUploadEnabled = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, false);
            anonymousUploadToOcid = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD,
                    OpenCelliDSigning.isAnonymous(getApplicationContext()));
            isBeaconDBUploadEnabled = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, false);
            boolean isRetryEnabled = getInputData().getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, false);

            if (!isOpenCellIdUploadEnabled && !isBeaconDBUploadEnabled)
            {
                Timber.d("No upload targets enabled.");
                UploadResultBundle uploadResultBundle = new UploadResultBundle();
                uploadResultBundle.setResult(UploadTarget.OpenCelliD, UploadResult.UploadDisabledForTarget);
                uploadResultBundle.setResult(UploadTarget.BeaconDB, UploadResult.UploadDisabledForTarget);
                return Result.success(getResultData(uploadResultBundle));
            }

            SurveyRecordDao dao = database.surveyRecordDao();
            // Records every target in play already has (a target turned off since) can go first
            dao.deleteAllUploadedRecords(isOpenCellIdUploadEnabled, isBeaconDBUploadEnabled);
            if (getTotalRecordsForUpload(dao, isOpenCellIdUploadEnabled, isBeaconDBUploadEnabled) == 0)
            {
                Timber.d("No records to upload.");
                UploadResultBundle uploadResultBundle = new UploadResultBundle();
                uploadResultBundle.setResult(UploadTarget.OpenCelliD, UploadResult.NoData);
                uploadResultBundle.setResult(UploadTarget.BeaconDB, UploadResult.NoData);
                return Result.success(getResultData(uploadResultBundle));
            }

            reportProgress(PROGRESS_MIN_VALUE, PROGRESS_MAX_VALUE, getApplicationContext().getString(R.string.upload_progress_records));
            CommunityBatchUploader uploader = new CommunityBatchUploader(getApplicationContext(), database,
                    isOpenCellIdUploadEnabled, anonymousUploadToOcid, isBeaconDBUploadEnabled);
            UploadResultBundle uploadResultBundle = uploader.run(this::isStopped, progress -> {
                reportProgress(progress, PROGRESS_MAX_VALUE, getApplicationContext().getString(R.string.upload_progress_records));
                return kotlin.Unit.INSTANCE;
            });

            // Whatever every target in play has taken can go, even when a target failed
            dao.deleteAllUploadedRecords(isOpenCellIdUploadEnabled, isBeaconDBUploadEnabled);

            // Cancelled, or replaced by Upload now: the work that replaced it reports for both
            if (isStopped() || uploadResultBundle.getResult(UploadTarget.OpenCelliD) == UploadResult.Cancelled)
            {
                uploadResultBundle.markAllCancelled();
                return Result.failure(getResultData(uploadResultBundle));
            }

            SurveyedPointStore.onCommunityUploadCompleted(database.surveyedPointDao(), uploadStartTime, uploadResultBundle);

            if (uploadResultBundle.hasAnyFailures())
            {
                if (isRetryEnabled && uploadResultBundle.isRetryableError())
                {
                    Timber.d("Upload failed, retry enabled.");
                    CommunityRetryStore.save(getApplicationContext(), getId(), uploadResultBundle);
                    return Result.retry();
                }
                Timber.e("Upload failed with no retry.");
                return Result.failure(getResultData(uploadResultBundle));
            }

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

    /**
     * Only used by WorkManager for expedited work on Android 11 and below, where expedited work has
     * to run as a foreground service. Reuses the same notification id and channel as the progress
     * row so those devices see one upload notification instead of two.
     */
    @NonNull
    @Override
    public ListenableFuture<ForegroundInfo> getForegroundInfoAsync()
    {
        Notification notification = notificationHelper.createNotification(notificationManager);
        return Futures.immediateFuture(new ForegroundInfo(NOTIFICATION_ID, notification));
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
                .putString(OCID_RESULT_ENUM, ocidResult.name())
                .putString(BEACONDB_RESULT_ENUM, beaconDbResult.name())
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

    /**
     * @return The records waiting for a target in play: Cellular for either, Wi-Fi only for BeaconDB.
     * A record a target already took doesn't count for that target.
     */
    public static int getTotalRecordsForUpload(SurveyRecordDao surveyRecordDao, boolean openCelliD, boolean beaconDb)
    {
        return getTotalCellularRecordsForUpload(surveyRecordDao, openCelliD, beaconDb)
                + surveyRecordDao.getWifiRecordCountForUpload(beaconDb);
    }

    /**
     * @return The Cellular records waiting for a target in play.
     */
    public static int getTotalCellularRecordsForUpload(SurveyRecordDao surveyRecordDao, boolean openCelliD, boolean beaconDb)
    {
        return surveyRecordDao.getGsmRecordCountForUpload(openCelliD, beaconDb)
                + surveyRecordDao.getCdmaRecordCountForUpload(openCelliD, beaconDb)
                + surveyRecordDao.getUmtsRecordCountForUpload(openCelliD, beaconDb)
                + surveyRecordDao.getLteRecordCountForUpload(openCelliD, beaconDb)
                + surveyRecordDao.getNrRecordCountForUpload(openCelliD, beaconDb);
    }
}
