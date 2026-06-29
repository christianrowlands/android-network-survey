package com.craxiom.networksurvey

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.data.PlmnColorOverrideManager
import com.craxiom.networksurvey.listeners.IGnssFailureListener
import com.craxiom.networksurvey.services.GrpcConnectionService
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.services.NetworkSurveyService.SurveyServiceBinder
import com.craxiom.networksurvey.ui.main.DeepLinkViewModel
import com.craxiom.networksurvey.ui.main.MainCompose
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.main.StartupDialog
import com.craxiom.networksurvey.ui.main.StartupDialogActions
import com.craxiom.networksurvey.util.NsAnalyticsDeepLinkHandler
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 */
@AndroidEntryPoint
class NetworkSurveyActivity : AppCompatActivity(), StartupDialogActions {

    private val deepLinkViewModel: DeepLinkViewModel by viewModels()
    private val sharedViewModel: SharedViewModel by viewModels()
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

        // Handle NS Analytics deep links
        handleNsAnalyticsDeepLink()
        // Handle a tap on the "uploads paused" notification
        handleNsAnalyticsNavigationExtra()
        // Handle a tap on a watchlist "Seen" notification
        handleWatchlistNavigationExtra()

        // Load user color overrides before Compose renders the tower map
        PlmnColorOverrideManager(this)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // Force Dark Mode
        setContent {
            MainCompose(
                appVersion = NsUtils.getAppVersionName(this),
                deepLinkViewModel = deepLinkViewModel
            )
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
                    if (!isFinishing && !isDestroyed) {
                        sharedViewModel.showGnssFailureDialog()
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Something went wrong when trying to show the GNSS Failure Dialog")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Update the activity's intent so it can be processed
        setIntent(intent)
        // Handle the deep link when app is already running
        handleNsAnalyticsDeepLink()
        // Handle a tap on the "uploads paused" notification when app is already running
        handleNsAnalyticsNavigationExtra()
        // Handle a tap on a watchlist "Seen" notification when app is already running
        handleWatchlistNavigationExtra()
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
        // Clear the GNSS failure listener to prevent callbacks to a destroyed activity
        networkSurveyService?.clearGnssFailureListener()
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

        // Consolidate the previously separate "general" and "location" rationale dialogs into a
        // single rationale screen shown before the OS prompt. The activity still owns the request
        // logic; the dialog is rendered by the shared Compose components.
        if (shouldShowPermissionsRationale || (!hasRequestedPermissions && !hasLocationPermission())) {
            Timber.d("Showing the consolidated permissions rationale dialog")
            sharedViewModel.showStartupDialog(StartupDialog.PermissionRationale)
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
            sharedViewModel.showStartupDialog(StartupDialog.BackgroundLocationRationale)
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
        // Don't overwrite a pending permission rationale dialog (single dialog slot). The GPS prompt
        // will be shown again on a later onResume once the permission dialog has been resolved.
        if (sharedViewModel.startupDialog.value != null) return
        sharedViewModel.showStartupDialog(StartupDialog.EnableGps)
    }

    override fun onPermissionRationaleAcknowledged() {
        requestPermissions()
    }

    override fun onEnableGpsConfirmed() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    override fun onBackgroundLocationConfirmed() {
        requestBackgroundLocationPermission()
    }

    override fun onBackgroundLocationDenied() {
        PreferenceUtils.denyBackgroundLocationPermission(this)
    }

    override fun onGnssFailureAcknowledged(rememberDecision: Boolean) {
        if (rememberDecision) {
            PreferenceUtils.saveBoolean(
                Application.get().getString(R.string.pref_key_ignore_raw_gnss_failure), true
            )
            // No need for GNSS failure updates anymore
            networkSurveyService?.clearGnssFailureListener()
        }
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
        lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                networkSurveyService?.toggleCellularLogging(enable)
            }

            val message = when (enabled) {
                null -> getString(R.string.cellular_logging_toggle_failed)
                true -> getString(R.string.cellular_logging_start_toast)
                false -> getString(R.string.cellular_logging_stop_toast)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts or stops writing the Wi-Fi log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @since 0.1.2
     */
    private fun toggleWifiLogging(enable: Boolean) {
        lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                networkSurveyService?.toggleWifiLogging(enable)
            }

            val message = when (enabled) {
                null -> getString(R.string.wifi_logging_toggle_failed)
                true -> getString(R.string.wifi_logging_start_toast)
                false -> getString(R.string.wifi_logging_stop_toast)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts or stops writing the Bluetooth log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @since 1.0.0
     */
    private fun toggleBluetoothLogging(enable: Boolean) {
        lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                networkSurveyService?.toggleBluetoothLogging(enable)
            }

            val message = when (enabled) {
                null -> getString(R.string.bluetooth_logging_toggle_failed)
                true -> getString(R.string.bluetooth_logging_start_toast)
                false -> getString(R.string.bluetooth_logging_stop_toast)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts or stops writing the GNSS log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private fun toggleGnssLogging(enable: Boolean) {
        lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                if (!checkLocationProvider(false)) return@withContext null
                networkSurveyService?.toggleGnssLogging(enable)
            }

            val message = when (enabled) {
                null -> getString(R.string.gnss_logging_toggle_failed)
                true -> getString(R.string.gnss_logging_start_toast)
                false -> getString(R.string.gnss_logging_stop_toast)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts or stops writing the CDR log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private fun toggleCdrLogging(enable: Boolean) {
        lifecycleScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                if (!checkLocationProvider(false)) return@withContext null
                networkSurveyService?.toggleCdrLogging(enable)
            }

            val message = when (enabled) {
                null -> getString(R.string.cdr_logging_toggle_failed)
                true -> getString(R.string.cdr_logging_start_toast)
                false -> getString(R.string.cdr_logging_stop_toast)
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle incoming deep links for NS Analytics registration.
     * If a valid deep link is detected, stores the QR data for processing
     * by the NS Analytics screen.
     */
    private fun handleNsAnalyticsDeepLink() {
        Timber.d(
            "handleNsAnalyticsDeepLink: intent=%s, action=%s, data=%s",
            intent,
            intent.action,
            intent.data
        )

        when (val result = NsAnalyticsDeepLinkHandler.parseIntent(intent)) {
            is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success -> {
                Timber.i(
                    "Received NS Analytics deep link, storing QR data for workspace: %s",
                    result.qrData.workspaceId
                )
                NsAnalyticsSecureStorage.storeQrData(applicationContext, result.qrData)
                // Trigger navigation via ViewModel
                deepLinkViewModel.navigateToNsAnalytics()
                Timber.d("Triggered navigation to NS Analytics via ViewModel")
            }

            is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error -> {
                Timber.w("Invalid NS Analytics deep link: %s", result.message)
                Toast.makeText(
                    this,
                    "Invalid registration link: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            is NsAnalyticsDeepLinkHandler.DeepLinkResult.NotApplicable -> {
                Timber.d("Not an NS Analytics deep link, ignoring")
            }
        }
    }

    /**
     * Handle a tap on the "uploads paused" notification, which routes the user to the NS Analytics
     * screen via the navigation extra set by [com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsNotificationHelper].
     */
    private fun handleNsAnalyticsNavigationExtra() {
        if (intent?.getBooleanExtra(
                NsAnalyticsConstants.EXTRA_NAVIGATE_TO_NS_ANALYTICS,
                false
            ) == true
        ) {
            Timber.d("Navigating to NS Analytics from notification tap")
            deepLinkViewModel.navigateToNsAnalytics()
            // Clear the extra so a configuration change or re-create doesn't navigate again.
            intent.removeExtra(NsAnalyticsConstants.EXTRA_NAVIGATE_TO_NS_ANALYTICS)
        }
    }

    /**
     * Handle a tap on a watchlist "Seen" notification, which routes the user to the Watchlist
     * history screen via the navigation extra set by
     * [com.craxiom.networksurvey.services.watchlist.WatchlistNotificationHelper].
     */
    private fun handleWatchlistNavigationExtra() {
        if (intent?.getBooleanExtra(
                NetworkSurveyConstants.EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY,
                false
            ) == true
        ) {
            Timber.d("Navigating to Watchlist history from notification tap")
            deepLinkViewModel.navigateToWatchlistHistory()
            // Clear the extra so a configuration change or re-create doesn't navigate again.
            intent.removeExtra(NetworkSurveyConstants.EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY)
        }
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
