package com.craxiom.networksurvey.logging.db.uploader

import android.content.Context
import java.util.UUID

/**
 * Keeps why the last community upload attempt failed when it is going to try again. WorkManager
 * drops a worker's output and progress when it returns retry, so without this the screens could
 * only say that an upload waits, not that it failed or why.
 *
 * It lives in its own preferences file so writing it doesn't wake the listeners on the default
 * preferences. The results are kept with the work's id, so an upload stopped by the system (which
 * also counts as an attempt) never shows an earlier upload's failure.
 */
object CommunityRetryStore {

    private const val PREFS_NAME = "community_upload_retry"
    private const val KEY_OPENCELLID = "opencellid_result"
    private const val KEY_BEACONDB = "beacondb_result"
    private const val KEY_WORK_ID = "work_id"

    /** The two targets' results of the attempt that failed. */
    data class Results(val openCelliD: UploadResult?, val beaconDb: UploadResult?)

    /** Saves the results of an attempt of the work [workId] that failed and will be tried again. */
    @JvmStatic
    fun save(context: Context, workId: UUID, results: UploadResultBundle) {
        prefs(context).edit()
            .putString(KEY_WORK_ID, workId.toString())
            .putString(KEY_OPENCELLID, results.getResult(UploadTarget.OpenCelliD).name)
            .putString(KEY_BEACONDB, results.getResult(UploadTarget.BeaconDB).name)
            .apply()
    }

    /**
     * @return The results of the last failed attempt of the work [workId], or null if none of its
     * attempts failed (it was only stopped and rescheduled).
     */
    fun read(context: Context, workId: UUID): Results? {
        val prefs = prefs(context)
        if (prefs.getString(KEY_WORK_ID, null) != workId.toString()) return null
        return Results(resultOf(prefs.getString(KEY_OPENCELLID, null)), resultOf(prefs.getString(KEY_BEACONDB, null)))
    }

    private fun resultOf(name: String?): UploadResult? = UploadResult.entries.firstOrNull { it.name == name }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
