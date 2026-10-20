package com.craxiom.networksurvey.logging.db.uploader;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.util.OpenCelliDSigning;
import com.craxiom.networksurvey.util.PreferenceUtils;

import timber.log.Timber;

/**
 * Enqueues the community databases' automatic uploads (OpenCelliD / BeaconDB): the ones
 * {@link AutoUploadManager} starts during a survey, and the one that uploads what is left when the
 * survey stops. Both go to the targets saved in the settings, only on Wi-Fi if the settings say
 * so, and never on a low battery; WorkManager keeps the upload until those conditions are met,
 * even if the app is closed.
 */
public final class AutoUploadRequest
{
    private AutoUploadRequest()
    {
    }

    /**
     * Uploads the records left when a survey stops sending to the community databases with
     * automatic upload on, so they don't wait for the next survey. Reads the database, so call it
     * off the main thread.
     *
     * @return True if records were waiting and an upload was enqueued.
     */
    public static boolean uploadRemaining(Context context)
    {
        boolean beaconDb = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB);
        int pending = NsUploaderWorker.getTotalRecordsForUpload(
                SurveyDatabase.getInstance(context).surveyRecordDao(), beaconDb);
        if (pending == 0) return false;

        Timber.i("Uploading the %d community records left at the end of the survey", pending);
        enqueue(context);
        return true;
    }

    /**
     * Enqueues an automatic upload. Keeps an upload that is already waiting or running, since it
     * uploads every record that is waiting.
     */
    static void enqueue(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean uploadToOcid = prefs.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID,
                NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID);
        boolean anonymousOcid = OpenCelliDSigning.isAnonymous(context);
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
    }
}
