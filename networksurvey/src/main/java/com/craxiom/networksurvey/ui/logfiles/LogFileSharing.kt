package com.craxiom.networksurvey.ui.logfiles

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.craxiom.networksurvey.logging.LogFileProvider
import timber.log.Timber

/** Hands log files to other apps: the share sheet for one or many files, and a file manager for the folder. */
object LogFileSharing {

    private const val CSV_MIME_TYPE = "text/csv"

    /** The system storage provider; its documents are paths on the shared storage volume. */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PRIMARY_VOLUME = "primary"

    /** Opens the share sheet for [files]. @return False if they could not be shared. */
    fun share(context: Context, files: List<LogFile>): Boolean {
        if (files.isEmpty()) return false
        return try {
            val uris = files.map { FileProvider.getUriForFile(context, context.packageName + LogFileProvider.AUTHORITY_SUFFIX, it.file) }
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            send.setType(CSV_MIME_TYPE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The read grant reaches the receiving app through the clip, for every file
            send.clipData = clipOf(uris)
            context.startActivity(Intent.createChooser(send, files.singleOrNull()?.name))
            true
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "A log file is outside the shared folder")
            false
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No app can receive shared log files")
            false
        }
    }

    /**
     * Opens the log files folder in a file manager: the folder itself where the file manager
     * supports that (the system Files app does), otherwise the Downloads app.
     *
     * @return False if no app could open either.
     */
    fun openFolder(context: Context): Boolean {
        val folder = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "$PRIMARY_VOLUME:${LogFilesRepository.DISPLAY_PATH}")
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR),
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS),
        )
        return attempts.any { intent ->
            try {
                // No read grant: the storage provider's documents are not ours to grant, and the
                // file manager reads them with its own access
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                Timber.i(e, "No app for %s", intent.action)
                false
            } catch (e: SecurityException) {
                Timber.w(e, "Not allowed to open %s", intent.action)
                false
            }
        }
    }

    private fun clipOf(uris: List<Uri>): ClipData {
        val clip = ClipData.newRawUri(null, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return clip
    }
}
