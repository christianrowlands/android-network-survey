package com.craxiom.networksurvey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.survey.setup.SurveyAutoStart
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber

/** The runtime permissions the app asks for at launch, and checks on them. */
object AppPermissions {

    /** The permissions the app asks for when it opens. */
    @JvmField
    val PERMISSIONS: Array<String>

    /** BLUETOOTH_CONNECT and BLUETOOTH_SCAN, which only exist from Android 12. */
    @JvmField
    val BLUETOOTH_PERMISSIONS: Array<String>

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ also needs permission to post notifications
            BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            PERMISSIONS = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            PERMISSIONS = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
            )
        } else {
            BLUETOOTH_PERMISSIONS = arrayOf()
            PERMISSIONS = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE
            )
        }
    }

    /** @return The [PERMISSIONS] that have not been granted. */
    fun missingRegularPermissions(context: Context): Array<String> =
        PERMISSIONS.filterNot { isGranted(context, it) }.toTypedArray()

    /**
     * @return True if the background location explanation should show on app launch: the survey
     * starts on its own (the only thing that needs location in the background, to start at boot),
     * foreground location is granted, background location is not, and the user has not said no.
     */
    fun shouldExplainBackgroundLocationOnLaunch(context: Context): Boolean =
        SurveyAutoStart.isEnabled(context) && canAskForBackgroundLocation(context) &&
            !PreferenceUtils.hasDeniedBackgroundLocationPermission(context)

    /** @return True if background location is missing and can be asked for (foreground location is granted). */
    fun canAskForBackgroundLocation(context: Context): Boolean =
        missingBackgroundLocation(context) && hasLocation(context)

    /** @return True if any of [PERMISSIONS] has not been granted. */
    fun missingAnyRegularPermission(context: Context): Boolean = PERMISSIONS.any { permission ->
        val missing = !isGranted(context, permission)
        if (missing) Timber.i("Missing the permission: %s", permission)
        missing
    }

    /** @return True if the background location permission has not been granted (Android 10+). */
    fun missingBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val missing = !isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (missing) Timber.i("Missing the permission: %s", Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return missing
    }

    /** @return True if [Manifest.permission.ACCESS_FINE_LOCATION] has been granted. */
    fun hasLocation(context: Context): Boolean = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION).also {
        if (!it) Timber.w("The ACCESS_FINE_LOCATION permission has not been granted")
    }

    /** @return True if [Manifest.permission.READ_PHONE_STATE], all cellular data needs, has been granted. */
    fun hasCellular(context: Context): Boolean = isGranted(context, Manifest.permission.READ_PHONE_STATE).also {
        if (!it) Timber.w("The READ_PHONE_STATE permission has not been granted")
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
