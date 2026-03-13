package com.craxiom.networksurvey.logging.db.uploader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.Observer;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IUploadRecordCountListener;
import com.craxiom.networksurvey.services.BatteryMonitor;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Orchestrates automatic uploading of community survey data (OpenCelliD / BeaconDB).
 * <p>
 * Implements {@link IUploadRecordCountListener} to receive callbacks when records are written
 * to the upload database. When the accumulated record count exceeds a threshold, and all
 * preconditions are met (cooldown, battery, circuit breaker), an upload is triggered via
 * WorkManager.
 * <p>
 * This class delegates to the original listener (typically {@link com.craxiom.networksurvey.services.NetworkSurveyService})
 * so that existing record count tracking (e.g., for the Survey Monitor UI) is preserved.
 * <p>
 * Note: Changes to the auto-upload preference do not take effect until the upload survey
 * session is restarted (stopped and started again).
 */
public class AutoUploadManager implements IUploadRecordCountListener
{
    private final Context context;
    private final BatteryMonitor batteryMonitor;
    private final IUploadRecordCountListener delegate;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicInteger pendingRecordCount = new AtomicInteger(0);

    /**
     * Holds the current work observer so it can be removed in {@link #reset()} to prevent leaks.
     */
    private Observer<List<WorkInfo>> currentWorkObserver;

    private volatile long lastAutoUploadTimestamp = 0;
    private volatile int consecutiveFailureCount = 0;
    private volatile long circuitBreakerCooldownUntil = 0;

    // Per-target rate limit cooldowns
    private volatile long ocidCooldownUntil = 0;
    private volatile long beaconDbCooldownUntil = 0;

    private static final long RATE_LIMIT_COOLDOWN_MS = 30 * 60 * 1000L; // 30 minutes

    /**
     * Creates a new AutoUploadManager.
     *
     * @param context        The application context.
     * @param batteryMonitor The battery monitor to check battery state, or null if unavailable.
     * @param delegate       The original upload record count listener to forward callbacks to.
     */
    public AutoUploadManager(Context context, BatteryMonitor batteryMonitor,
                             IUploadRecordCountListener delegate)
    {
        this.context = context.getApplicationContext();
        this.batteryMonitor = batteryMonitor;
        this.delegate = delegate;
    }

    @Override
    public void onCellularUploadRecordsWritten(int recordCount)
    {
        if (delegate != null) delegate.onCellularUploadRecordsWritten(recordCount);

        pendingRecordCount.addAndGet(recordCount);
        checkAndTriggerAutoUpload();
    }

    @Override
    public void onWifiUploadRecordsWritten(int recordCount)
    {
        if (delegate != null) delegate.onWifiUploadRecordsWritten(recordCount);

        pendingRecordCount.addAndGet(recordCount);
        checkAndTriggerAutoUpload();
    }

    /**
     * Checks all preconditions and triggers an auto-upload if they are all met.
     * Synchronized to prevent concurrent trigger from interleaved callbacks.
     */
    private synchronized void checkAndTriggerAutoUpload()
    {
        if (pendingRecordCount.get() < NetworkSurveyConstants.AUTO_UPLOAD_RECORD_THRESHOLD)
        {
            return;
        }

        if (!PreferenceUtils.isAutoUploadEnabled(context))
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastAutoUploadTimestamp < NetworkSurveyConstants.AUTO_UPLOAD_COOLDOWN_MS)
        {
            Timber.d("Auto-upload skipped: cooldown active");
            return;
        }

        if (batteryMonitor != null && batteryMonitor.isPausedDueToBattery())
        {
            Timber.d("Auto-upload skipped: battery too low");
            return;
        }

        if (now < circuitBreakerCooldownUntil)
        {
            Timber.d("Auto-upload skipped: circuit breaker active until %d", circuitBreakerCooldownUntil);
            return;
        }

        if (isAnyRateLimitActive(now))
        {
            Timber.d("Auto-upload skipped: per-target rate limit active");
            return;
        }

        triggerAutoUpload();
    }

    /**
     * Returns true if all enabled upload targets have active rate limits, meaning
     * there is no target ready to accept an upload. Returns true also if no targets
     * are enabled at all.
     */
    private boolean isAnyRateLimitActive(long now)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean ocidEnabled = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID);
        boolean beaconDbEnabled = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB);

        if (!ocidEnabled && !beaconDbEnabled) return true;

        // Check if at least one enabled target is not rate-limited
        if (ocidEnabled && now >= ocidCooldownUntil) return false;
        return !beaconDbEnabled || now < beaconDbCooldownUntil;
    }

    /**
     * Triggers an auto-upload by enqueuing a WorkManager work request with the appropriate
     * constraints and input data.
     */
    private void triggerAutoUpload()
    {
        Timber.i("Auto-upload triggered with %d pending records", pendingRecordCount.get());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean uploadToOcid = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID);
        boolean anonymousOcid = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, false);
        boolean uploadToBeaconDb = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB);
        boolean retry = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED,
                NetworkSurveyConstants.DEFAULT_UPLOAD_RETRY_ENABLED);

        boolean wifiOnly = PreferenceUtils.isAutoUploadWifiOnly(context);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOcid)
                .putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymousOcid)
                .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDb)
                .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
                .putString(NsUploaderWorker.INPUT_SOURCE, NsUploaderWorker.SOURCE_AUTO)
                .build();

        OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(NsUploaderWorker.class)
                .addTag(NsUploaderWorker.WORKER_TAG)
                .setInputData(inputData)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                uploadWorkRequest
        );

        pendingRecordCount.set(0);
        lastAutoUploadTimestamp = System.currentTimeMillis();

        observeUniqueWorkResult();
    }

    /**
     * Observes the unique work result by name (not by ID) to correctly handle the case
     * where {@code enqueueUniqueWork} with {@code KEEP} discards our request because
     * work is already running.
     */
    private void observeUniqueWorkResult()
    {
        // LiveData observation must happen on the main thread
        mainHandler.post(() -> {
            try
            {
                WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWorkLiveData(NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME)
                        .observeForever(currentWorkObserver = new Observer<>()
                        {
                            @Override
                            public void onChanged(List<WorkInfo> workInfos)
                            {
                                if (workInfos == null || workInfos.isEmpty()) return;

                                WorkInfo workInfo = workInfos.get(0);
                                if (workInfo.getState().isFinished())
                                {
                                    currentWorkObserver = null;
                                    WorkManager.getInstance(context)
                                            .getWorkInfosForUniqueWorkLiveData(
                                                    NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME)
                                            .removeObserver(this);

                                    handleWorkResult(workInfo);
                                }
                            }
                        });
            } catch (Exception e)
            {
                Timber.e(e, "Failed to observe auto-upload work result");
            }
        });
    }

    /**
     * Handles the result of an auto-upload work request, updating the circuit breaker.
     */
    private synchronized void handleWorkResult(WorkInfo workInfo)
    {
        if (workInfo.getState() == WorkInfo.State.SUCCEEDED)
        {
            Timber.i("Auto-upload succeeded");
            consecutiveFailureCount = 0;
            circuitBreakerCooldownUntil = 0;

            checkForRateLimitResults(workInfo);
        } else if (workInfo.getState() == WorkInfo.State.FAILED)
        {
            consecutiveFailureCount++;
            Timber.w("Auto-upload failed (consecutive failures: %d)", consecutiveFailureCount);

            checkForRateLimitResults(workInfo);

            if (consecutiveFailureCount >= NetworkSurveyConstants.AUTO_UPLOAD_MAX_CONSECUTIVE_FAILURES)
            {
                // Progressive backoff: 15min, 30min, 60min cap
                int multiplier = Math.min(consecutiveFailureCount - NetworkSurveyConstants.AUTO_UPLOAD_MAX_CONSECUTIVE_FAILURES + 1, 4);
                long backoffMs = NetworkSurveyConstants.AUTO_UPLOAD_COOLDOWN_MS * multiplier;
                circuitBreakerCooldownUntil = System.currentTimeMillis() + backoffMs;
                Timber.w("Circuit breaker activated. Cooldown until %d (backoff: %d ms)",
                        circuitBreakerCooldownUntil, backoffMs);
            }
        }
    }

    /**
     * Checks the work output for per-target rate limit or API key errors and sets
     * appropriate cooldowns. Uses raw enum names from the worker output to avoid
     * locale-sensitive string comparisons.
     */
    private void checkForRateLimitResults(WorkInfo workInfo)
    {
        Data outputData = workInfo.getOutputData();

        String ocidResultEnum = outputData.getString(NsUploaderWorker.OCID_RESULT_ENUM);
        String beaconDbResultEnum = outputData.getString(NsUploaderWorker.BEACONDB_RESULT_ENUM);

        if (ocidResultEnum != null)
        {
            try
            {
                UploadResult result = UploadResult.valueOf(ocidResultEnum);
                if (result == UploadResult.LimitExceeded)
                {
                    ocidCooldownUntil = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS;
                    Timber.w("OCID rate limit hit, cooldown set for 30 minutes");
                } else if (result == UploadResult.InvalidApiKey)
                {
                    ocidCooldownUntil = Long.MAX_VALUE;
                    Timber.w("OCID invalid API key, auto-upload disabled for OCID until preference changes");
                }
            } catch (IllegalArgumentException e)
            {
                Timber.w(e, "Unknown OCID result enum: %s", ocidResultEnum);
            }
        }

        if (beaconDbResultEnum != null)
        {
            try
            {
                UploadResult result = UploadResult.valueOf(beaconDbResultEnum);
                if (result == UploadResult.LimitExceeded)
                {
                    beaconDbCooldownUntil = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS;
                    Timber.w("BeaconDB rate limit hit, cooldown set for 30 minutes");
                }
            } catch (IllegalArgumentException e)
            {
                Timber.w(e, "Unknown BeaconDB result enum: %s", beaconDbResultEnum);
            }
        }
    }

    /**
     * Resets the manager state. Should be called when upload scanning is stopped.
     * Synchronized to prevent concurrent state modification with
     * {@link #checkAndTriggerAutoUpload()} and {@link #handleWorkResult(WorkInfo)}.
     */
    public synchronized void reset()
    {
        pendingRecordCount.set(0);
        lastAutoUploadTimestamp = 0;
        consecutiveFailureCount = 0;
        circuitBreakerCooldownUntil = 0;
        ocidCooldownUntil = 0;
        beaconDbCooldownUntil = 0;

        // Remove any active work observer to prevent leaks
        Observer<List<WorkInfo>> observer = currentWorkObserver;
        if (observer != null)
        {
            currentWorkObserver = null;
            mainHandler.post(() -> {
                try
                {
                    WorkManager.getInstance(context)
                            .getWorkInfosForUniqueWorkLiveData(
                                    NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME)
                            .removeObserver(observer);
                } catch (Exception e)
                {
                    Timber.w(e, "Failed to remove work observer during reset");
                }
            });
        }
    }
}
