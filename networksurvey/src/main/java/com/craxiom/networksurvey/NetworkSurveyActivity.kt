package com.craxiom.networksurvey

import android.Manifest
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.listeners.IGnssFailureListener
import com.craxiom.networksurvey.services.GrpcConnectionService
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.NetworkSurveyService.SurveyServiceBinder
import com.craxiom.networksurvey.ui.main.MainCompose
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import com.craxiom.networksurvey.util.ToggleLoggingTask
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.function.Function
import java.util.function.Supplier

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 */
@AndroidEntryPoint
class NetworkSurveyActivity : AppCompatActivity() {

    private var surveyServiceConnection: SurveyServiceConnection? = null
    private var networkSurveyService: NetworkSurveyService? = null
    private var turnOnCellularLoggingOnNextServiceConnection = false
    private var turnOnWifiLoggingOnNextServiceConnection = false
    private var turnOnBluetoothLoggingOnNextServiceConnection = false
    private var turnOnGnssLoggingOnNextServiceConnection = false
    private var turnOnCdrLoggingOnNextServiceConnection = false
    private var gnssFailureListener: IGnssFailureListener? = null
    private var hasRequestedPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // Force Dark Mode
        setContent {
            MainCompose(appVersion = NsUtils.getAppVersionName(this))
        }

        // Install the defaults specified in the XML preferences file, this is only done the first time the app is opened
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        val applicationContext = applicationContext
        turnOnCellularLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(
            NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING,
            false,
            applicationContext
        )
        turnOnWifiLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(
            NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING,
            false,
            applicationContext
        )
        turnOnBluetoothLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(
            NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING,
            false,
            applicationContext
        )
        turnOnGnssLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(
            NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING,
            false,
            applicationContext
        )
        turnOnCdrLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(
            NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING,
            false,
            applicationContext
        )

        surveyServiceConnection = SurveyServiceConnection()

        Application.createNotificationChannel(this)

        gnssFailureListener = IGnssFailureListener {
            try {
                runOnUiThread {
                    val fragmentView =
                        LayoutInflater.from(this).inflate(R.layout.gnss_failure, null)

                    val gnssFailureDialog = AlertDialog.Builder(this)
                        .setView(fragmentView)
                        .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                            val rememberDecisionCheckBox =
                                fragmentView.findViewById<CheckBox>(R.id.failureRememberDecisionCheckBox)
                            val checked = rememberDecisionCheckBox.isChecked
                            if (checked) {
                                PreferenceUtils.saveBoolean(
                                    Application.get()
                                        .getString(R.string.pref_key_ignore_raw_gnss_failure), true
                                )
                                // No need for GNSS failure updates anymore
                                if (networkSurveyService != null) {
                                    networkSurveyService!!.clearGnssFailureListener()
                                }
                            }
                        }
                        .create()

                    if (!this@NetworkSurveyActivity.isFinishing) {
                        gnssFailureDialog.show()
                        val viewById =
                            gnssFailureDialog.findViewById<TextView>(R.id.failureDescriptionTextView)
                        if (viewById != null) viewById.movementMethod =
                            LinkMovementMethod.getInstance()
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Something went wrong when trying to show the GNSS Failure Dialog")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (missingAnyRegularPermissions()) showPermissionRationaleAndRequestPermissions()

        // If we have been granted the location permission, we want to check to see if the location service is enabled.
        // If it is not, then this call will report that to the user and give them the option to enable it.
        if (hasLocationPermission()) checkLocationProvider(true)

        // As of Android 11, you have to request the Background location permission as a separate request, otherwise it
        // fails: https://developer.android.com/about/versions/11/privacy/location#background-location
        if (missingBackgroundLocationPermission()) showBackgroundLocationRationaleAndRequest()

        // All we need for the cellular information is the Manifest.permission.READ_PHONE_STATE permission.  Location is optional
        if (hasCellularPermission()) startAndBindToNetworkSurveyService()
    }

    override fun onPause() {
        if (networkSurveyService != null) {
            val applicationContext = applicationContext

            networkSurveyService!!.onUiHidden()

            if (!networkSurveyService!!.isBeingUsed) {
                // We can safely shutdown the service since both logging and the connections are turned off
                val networkSurveyServiceIntent = Intent(
                    applicationContext,
                    NetworkSurveyService::class.java
                )
                val connectionServiceIntent = Intent(
                    applicationContext,
                    GrpcConnectionService::class.java
                )
                stopService(networkSurveyServiceIntent)
                stopService(connectionServiceIntent)
            }

            try {
                applicationContext.unbindService(surveyServiceConnection!!)
                networkSurveyService = null
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not unbind the service because it is not bound.")
            } catch (e: Exception) {
                Timber.e(e, "Could not unbind the service because of an exception.")
            }
        }

        super.onPause()
    }

    override fun onDestroy() {
        networkSurveyService = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == ACCESS_PERMISSION_REQUEST_ID) {
            for (index in permissions.indices) {
                if (Manifest.permission.ACCESS_FINE_LOCATION == permissions[index]) {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                        checkLocationProvider(true)
                        startAndBindToNetworkSurveyService()
                    } else {
                        Timber.w("The ACCESS_FINE_LOCATION Permission was denied.")
                    }
                }
            }
        }
    }

    /**
     * Check to see if we should show the rationale for any of the regular permissions. If so, then display a dialog that
     * explains what permissions we need for this app to work properly.
     *
     *
     * If we should not show the rationale, then just request the permissions.
     */
    private fun showPermissionRationaleAndRequestPermissions() {
        var shouldShowPermissionsRationale = false
        for (cdrPermission in PERMISSIONS) {
            // If we are on Android 13+ and the permission is for the notification, then don't
            // show the permission rationale. This is because the app gets stuck in a permission
            // loop because the shouldShowRequestPermissionRationale method was always returning
            // true for POST_NOTIFICATION, but when requesting the permission it was never prompting
            // the user. I hope this bug is fixed in future Android versions.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || Manifest.permission.POST_NOTIFICATIONS != cdrPermission
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, cdrPermission)) {
                    shouldShowPermissionsRationale = true
                    break
                }
            }
        }

        if (shouldShowPermissionsRationale) {
            Timber.d("Showing the permissions rationale dialog")

            val alertBuilder = AlertDialog.Builder(this)
            alertBuilder.setCancelable(true)
            alertBuilder.setTitle(getString(R.string.permissions_rationale_title))
            alertBuilder.setMessage(getText(R.string.permissions_rationale))
            alertBuilder.setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, _: Int -> requestPermissions() }

            val permissionsExplanationDialog = alertBuilder.create()
            permissionsExplanationDialog.show()
        } else if (!hasRequestedPermissions && !hasLocationPermission()) {
            Timber.d("Showing the location permissions rationale dialog")

            val alertBuilder = AlertDialog.Builder(this)
            alertBuilder.setCancelable(true)
            alertBuilder.setTitle(getString(R.string.location_permission_rationale_title))
            alertBuilder.setMessage(getText(R.string.location_permission_rationale))
            alertBuilder.setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, _: Int -> requestPermissions() }

            val permissionsExplanationDialog = alertBuilder.create()
            permissionsExplanationDialog.show()
        } else if (!hasRequestedPermissions) {
            requestPermissions()
        }
    }

    /**
     * Check to see if we should show the rationale for the background location permission.  If so, then display a
     * dialog that explains why we need the background location permission.
     *
     *
     * We can only request the background location permission if the user has already granted the general location
     * permission.
     *
     * @since 1.4.0
     */
    private fun showBackgroundLocationRationaleAndRequest() {
        val deniedBackgroundAlready = PreferenceUtils.hasDeniedBackgroundLocationPermission(this)

        if (deniedBackgroundAlready) return

        if (hasLocationPermission() && ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        ) {
            Timber.d("Showing the background location permission rationale dialog")

            val alertBuilder = AlertDialog.Builder(this)
            alertBuilder.setCancelable(true)
            alertBuilder.setTitle(getString(R.string.background_location_permission_rationale_title))
            alertBuilder.setMessage(getText(R.string.background_location_permission_rationale))
            alertBuilder.setPositiveButton(
                R.string.open_settings
            ) { _: DialogInterface?, _: Int -> requestBackgroundLocationPermission() }
            alertBuilder.setNegativeButton(
                R.string.deny_permission
            ) { _: DialogInterface?, which: Int ->
                if (which == DialogInterface.BUTTON_NEGATIVE) {
                    PreferenceUtils.denyBackgroundLocationPermission(this)
                }
            }

            val permissionsExplanationDialog = alertBuilder.create()
            permissionsExplanationDialog.show()
        }
    }

    /**
     * Request the permissions needed for this app if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private fun requestPermissions() {
        if (missingAnyRegularPermissions()) {
            hasRequestedPermissions = true
            ActivityCompat.requestPermissions(this, PERMISSIONS, ACCESS_PERMISSION_REQUEST_ID)
        }
    }

    /**
     * Request the background location permission, which presents the user with the App's location permission settings
     * page.
     *
     * @since 1.4.0
     */
    private fun requestBackgroundLocationPermission() {
        if (missingBackgroundLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    ACCESS_BACKGROUND_LOCATION_PERMISSION_REQUEST_ID
                )
            }
        }
    }

    /**
     * Checks that the location provider is enabled.  If GPS location is not enabled on this device, and
     * `informUser` is set to true, then the settings UI is opened so the user can enable it.
     *
     *
     * If either the GPS device is not present, or if the GPS provider is disabled, an appropriate toast message is
     * displayed as long as the `informUser` parameter is set to true.
     *
     * @param informUser If this method should display a toast and prompt the user to enable GPS set this to true,
     * false otherwise.
     * @return True if the device has GPS capabilities, and location services are enabled on the device. False otherwise.
     */
    private fun checkLocationProvider(informUser: Boolean): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        if (locationManager == null) {
            Timber.w("Could not get the location manager.  Skipping checking the location provider")
            return false
        }

        if (!hasLocationPermission()) {
            runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    "Missing location permission",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }

        val locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER)
        if (locationProvider == null) {
            val noGpsMessage = getString(R.string.no_gps_device)
            Timber.w(noGpsMessage)
            if (informUser) {
                Toast.makeText(applicationContext, noGpsMessage, Toast.LENGTH_LONG).show()
            }
            return false
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // gps exists, but isn't on
            val turnOnGpsMessage = getString(R.string.turn_on_gps)
            Timber.w(turnOnGpsMessage)
            if (informUser) {
                Toast.makeText(applicationContext, turnOnGpsMessage, Toast.LENGTH_LONG).show()

                promptEnableGps()
            }
            return false
        }

        return true
    }

    /**
     * Ask the user if they want to enable GPS.  If they do, then open the Location settings.
     */
    private fun promptEnableGps() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.enable_gps_message))
            .setPositiveButton(
                getString(R.string.enable_gps_positive_button)
            ) { _: DialogInterface?, _: Int ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(
                getString(R.string.enable_gps_negative_button)
            ) { _: DialogInterface?, _: Int -> }
            .show()
    }

    /**
     * @return True if any of the permissions for this app have been denied.  False if all the permissions have been granted.
     */
    private fun missingAnyRegularPermissions(): Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.i("Missing the permission: %s", permission)
                return true
            }
        }

        return false
    }

    /**
     * @return True if the background location permission for this app has been denied; false otherwise.
     * @since 1.4.0
     */
    private fun missingBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.i(
                    "Missing the permission: %s",
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                return true
            }
        }

        return false
    }

    /**
     * @return True if the [Manifest.permission.ACCESS_FINE_LOCATION] permission has been granted.  False otherwise.
     */
    private fun hasLocationPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("The ACCESS_FINE_LOCATION permission has not been granted")
            return false
        }

        return true
    }

    /**
     * @return True if the [Manifest.permission.READ_PHONE_STATE] permission has been granted.  False otherwise.
     */
    private fun hasCellularPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("The READ_PHONE_STATE permission has not been granted")
            return false
        }

        return true
    }

    /**
     * Start the Network Survey Service (it won't start if it is already started), and then bind to the service.
     *
     *
     * Starting the service will cause the cellular records to be pulled from the Android system so they can be shown
     * in the UI, logged to a file, sent over a connection, or any combination of the three.
     *
     *
     * The Network survey service also handles getting GNSS information so that it can be used accordingly.
     */
    private fun startAndBindToNetworkSurveyService() {
        try {
            // Start and bind to the survey service
            val applicationContext = applicationContext
            val startServiceIntent = Intent(applicationContext, NetworkSurveyService::class.java)
            startService(startServiceIntent)

            val serviceIntent = Intent(applicationContext, NetworkSurveyService::class.java)
            val bound = applicationContext.bindService(
                serviceIntent,
                surveyServiceConnection!!, BIND_ABOVE_CLIENT
            )
            Timber.i("NetworkSurveyService bound in the NetworkSurveyActivity: %s", bound)
        } catch (e: IllegalStateException) {
            // It appears that an IllegalStateException will occur if the user opens this app but the then quickly
            // switches away from it. The IllegalStateException indicates that we can't call startService while the
            // app is in the background. We catch this here so that we can prevent the app from crashing.
            Timber.w(e, "Could not start the Network Survey service.")
        }
    }

    /**
     * Starts or stops writing the Cellular log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private fun toggleCellularLogging(enable: Boolean) {
        ToggleLoggingTask(Supplier {
            if (networkSurveyService != null) {
                return@Supplier networkSurveyService!!.toggleCellularLogging(enable)
            }
            null
        }, Function { enabled: Boolean? ->
            if (enabled == null) return@Function getString(R.string.cellular_logging_toggle_failed)
            getString(if (enabled) R.string.cellular_logging_start_toast else R.string.cellular_logging_stop_toast)
        }, applicationContext).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * Starts or stops writing the Wi-Fi log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @since 0.1.2
     */
    private fun toggleWifiLogging(enable: Boolean) {
        ToggleLoggingTask(Supplier {
            if (networkSurveyService != null) return@Supplier networkSurveyService!!.toggleWifiLogging(
                enable
            )
            null
        }, Function { enabled: Boolean? ->
            if (enabled == null) return@Function getString(R.string.wifi_logging_toggle_failed)
            getString(if (enabled) R.string.wifi_logging_start_toast else R.string.wifi_logging_stop_toast)
        }, applicationContext).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * Starts or stops writing the Bluetooth log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @since 1.0.0
     */
    private fun toggleBluetoothLogging(enable: Boolean) {
        ToggleLoggingTask(Supplier {
            if (networkSurveyService != null) {
                return@Supplier networkSurveyService!!.toggleBluetoothLogging(enable)
            }
            null
        }, Function { enabled: Boolean? ->
            if (enabled == null) return@Function getString(R.string.bluetooth_logging_toggle_failed)
            getString(if (enabled) R.string.bluetooth_logging_start_toast else R.string.bluetooth_logging_stop_toast)
        }, applicationContext).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * Starts or stops writing the GNSS log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private fun toggleGnssLogging(enable: Boolean) {
        ToggleLoggingTask(Supplier {
            if (!checkLocationProvider(false)) return@Supplier null
            if (networkSurveyService != null) return@Supplier networkSurveyService!!.toggleGnssLogging(
                enable
            )
            null
        }, Function { enabled: Boolean? ->
            if (enabled == null) return@Function getString(R.string.gnss_logging_toggle_failed)
            getString(if (enabled) R.string.gnss_logging_start_toast else R.string.gnss_logging_stop_toast)
        }, applicationContext).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * Starts or stops writing the CDR log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private fun toggleCdrLogging(enable: Boolean) {
        ToggleLoggingTask(Supplier {
            if (!checkLocationProvider(false)) return@Supplier null
            if (networkSurveyService != null) return@Supplier networkSurveyService!!.toggleCdrLogging(
                enable
            )
            null
        }, Function { enabled: Boolean? ->
            if (enabled == null) return@Function getString(R.string.cdr_logging_toggle_failed)
            getString(if (enabled) R.string.cdr_logging_start_toast else R.string.cdr_logging_stop_toast)
        }, applicationContext).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    /**
     * A [ServiceConnection] implementation for binding to the [NetworkSurveyService].
     */
    private inner class SurveyServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, iBinder: IBinder) {
            Timber.i("%s service connected", name)

            val binder = iBinder as SurveyServiceBinder
            networkSurveyService = binder.service as NetworkSurveyService
            networkSurveyService!!.onUiVisible(this@NetworkSurveyActivity)
            networkSurveyService!!.registerGnssFailureListener(gnssFailureListener)

            val cellularLoggingEnabled = networkSurveyService!!.isCellularLoggingEnabled
            if (turnOnCellularLoggingOnNextServiceConnection && !cellularLoggingEnabled) {
                toggleCellularLogging(true)
            }

            val wifiLoggingEnabled = networkSurveyService!!.isWifiLoggingEnabled
            if (turnOnWifiLoggingOnNextServiceConnection && !wifiLoggingEnabled) {
                toggleWifiLogging(true)
            }

            val bluetoothLoggingEnabled = networkSurveyService!!.isBluetoothLoggingEnabled
            if (turnOnBluetoothLoggingOnNextServiceConnection && !bluetoothLoggingEnabled) {
                toggleBluetoothLogging(true)
            }

            val gnssLoggingEnabled = networkSurveyService!!.isGnssLoggingEnabled
            if (turnOnGnssLoggingOnNextServiceConnection && !gnssLoggingEnabled) {
                toggleGnssLogging(true)
            }

            val cdrLoggingEnabled = networkSurveyService!!.isCdrLoggingEnabled
            if (turnOnCdrLoggingOnNextServiceConnection && !cdrLoggingEnabled) {
                toggleCdrLogging(true)
            }

            turnOnCellularLoggingOnNextServiceConnection = false
            turnOnWifiLoggingOnNextServiceConnection = false
            turnOnBluetoothLoggingOnNextServiceConnection = false
            turnOnGnssLoggingOnNextServiceConnection = false
            turnOnCdrLoggingOnNextServiceConnection = false
        }

        override fun onServiceDisconnected(name: ComponentName) {
            networkSurveyService = null
            Timber.i("%s service disconnected", name)
        }
    }

    companion object {
        private const val ACCESS_PERMISSION_REQUEST_ID = 1
        val PERMISSIONS: Array<String>

        // The BLUETOOTH_CONNECT and BLUETOOTH_SCAN permissions are only for Android 12 and above.
        @JvmField
        val BLUETOOTH_PERMISSIONS: Array<String>

        init {
            // Android 13+ (SDK 33) requires permission for push notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                BLUETOOTH_PERMISSIONS = arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
                PERMISSIONS = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BLUETOOTH_PERMISSIONS = arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
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

        private const val ACCESS_BACKGROUND_LOCATION_PERMISSION_REQUEST_ID = 2
    }
}
