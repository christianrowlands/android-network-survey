package com.craxiom.networksurvey.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.craxiom.networksurvey.model.UploadScanningResult
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A modern replacement for AsyncTask to handle upload scanning operations.
 * Uses ExecutorService for background work and Handler for UI updates.
 */
class UploadScanningTask(
    private val backgroundOperation: () -> UploadScanningResult,
    private val onComplete: (UploadScanningResult) -> String, // Return String for toast message
    private val context: Context
) {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    fun execute() {
        executorService.execute {
            try {
                val result = backgroundOperation()
                mainHandler.post {
                    val toastMessage = onComplete(result)
                    showToast(toastMessage)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during upload scanning task")
                mainHandler.post {
                    val errorResult = UploadScanningResult(
                        success = false,
                        isEnabled = false,
                        message = "An unexpected error occurred"
                    )
                    val toastMessage = onComplete(errorResult)
                    showToast(toastMessage)
                }
            } finally {
                executorService.shutdown()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
