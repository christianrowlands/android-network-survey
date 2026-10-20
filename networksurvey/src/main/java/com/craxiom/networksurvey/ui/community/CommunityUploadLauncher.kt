package com.craxiom.networksurvey.ui.community

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.OpenCelliDSigning
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * Starts a community databases upload and counts what waits for one, for the Community databases
 * page and the Survey screen's "Still to do" panel alike.
 */
object CommunityUploadLauncher {

    /**
     * Enqueues an upload with the given choices.
     *
     * @return False if there is no network, so the caller can say so.
     */
    fun start(context: Context, uploadToOpenCellId: Boolean, anonymously: Boolean, uploadToBeaconDb: Boolean, retry: Boolean): Boolean {
        if (!NsUtils.isNetworkAvailable(context)) return false

        val inputData = Data.Builder()
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOpenCellId)
            .putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymously)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDb)
            .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
            .putString(NsUploaderWorker.INPUT_SOURCE, NsUploaderWorker.SOURCE_MANUAL)
            .build()
        val request = OneTimeWorkRequest.Builder(NsUploaderWorker::class.java)
            .addTag(NsUploaderWorker.WORKER_TAG)
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NetworkSurveyConstants.COMMUNITY_UPLOAD_UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request,
        )
        return true
    }

    /**
     * @return True if Upload now should show the upload dialog first, which it does until the user
     * picks "don't show again".
     */
    fun shouldConfirm(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, true)

    /** Saves the upload dialog's "don't show again" choice. */
    fun setSkipConfirmation(context: Context, skip: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, !skip)
        }
    }

    /**
     * Saves the choices made in the upload dialog, so the next upload starts from them, then
     * enqueues the upload.
     *
     * @return False if there is no network, so the caller can say so.
     */
    fun startWithChoices(context: Context, uploadToOpenCellId: Boolean, anonymously: Boolean, uploadToBeaconDb: Boolean, retry: Boolean): Boolean {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOpenCellId)
            putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDb)
            putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
        }
        OpenCelliDSigning.setAnonymousChosen(context, anonymously)
        return start(context, uploadToOpenCellId, anonymously, uploadToBeaconDb, retry)
    }

    /**
     * Uploads with the targets saved in the settings.
     *
     * @return False if there is no network.
     */
    fun startWithSavedSettings(context: Context): Boolean = start(
        context,
        uploadToOpenCellId = PreferenceUtils.getBoolean(
            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID,
        ),
        anonymously = OpenCelliDSigning.isAnonymous(context),
        uploadToBeaconDb = PreferenceUtils.getBoolean(
            NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB,
        ),
        retry = PreferenceUtils.getBoolean(
            NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, NetworkSurveyConstants.DEFAULT_UPLOAD_RETRY_ENABLED,
        ),
    )

    /** The Cellular and Wi-Fi records saved for the community databases and not uploaded yet. */
    data class Pending(val cellular: Int, val wifi: Int)

    /** @return The records waiting to upload. Reads the database, so call it off the main thread. */
    fun pending(context: Context): Pending {
        val dao = SurveyDatabase.getInstance(context).surveyRecordDao()
        return Pending(NsUploaderWorker.getTotalCellularRecordsForUpload(dao), dao.wifiRecordCountForUpload)
    }
}
