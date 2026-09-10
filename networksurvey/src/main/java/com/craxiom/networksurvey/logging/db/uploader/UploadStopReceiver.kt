package com.craxiom.networksurvey.logging.db.uploader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.WorkManager
import com.craxiom.networksurvey.R
import timber.log.Timber

const val STOP_UPLOADER = "com.craxiom.networksurvey.UPLOADER_STOP"

/**
 * Handles incoming broadcasts that perform actions on the tower uploaded (e.g. stop the upload).
 */
class UploadStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == STOP_UPLOADER) {
            Timber.i("Canceling the tower upload based on a broadcast intent")
            // Cancel all upload workers
            WorkManager.getInstance(context).cancelAllWorkByTag(NsUploaderWorker.WORKER_TAG)

            // Provide user feedback
            Toast.makeText(context, R.string.uploader_canceled, Toast.LENGTH_SHORT).show()
        }
    }
}
