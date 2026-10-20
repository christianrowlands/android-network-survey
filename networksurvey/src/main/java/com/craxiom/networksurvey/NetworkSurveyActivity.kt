package com.craxiom.networksurvey

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.data.PlmnColorOverrideManager
import com.craxiom.networksurvey.listeners.IGnssFailureListener
import com.craxiom.networksurvey.services.GrpcConnectionService
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.services.NetworkSurveyService.SurveyServiceBinder
import com.craxiom.networksurvey.ui.main.DeepLinkDispatcher
import com.craxiom.networksurvey.ui.main.DeepLinkRouter
import com.craxiom.networksurvey.ui.main.DeepLinkViewModel
import com.craxiom.networksurvey.ui.main.MainCompose
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.main.StartupDialogActions
import com.craxiom.networksurvey.ui.onboarding.OnboardingViewModel
import com.craxiom.networksurvey.ui.settings.SettingsDefaults
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
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private var surveyServiceConnection: SurveyServiceConnection? = null
    private var networkSurveyService: NetworkSurveyService? = null
    private var autoStartSurveyOnNextServiceConnection = false
    private var gnssFailureListener: IGnssFailureListener? = null
    private lateinit var deepLinkDispatcher: DeepLinkDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A deep link or notification tap is handled once: not again when the activity is
        // recreated (rotation, process death) or reopened from Recents with the same intent.
        deepLinkDispatcher = DeepLinkDispatcher(this, deepLinkViewModel)
        if (DeepLinkRouter.shouldRouteOnCreate(savedInstanceState != null, intent?.flags ?: 0)) {
            deepLinkDispatcher.dispatch(intent)
        }

        // Load user color overrides before Compose renders the tower map
        PlmnColorOverrideManager(this)

        setContent {
            MainCompose(deepLinkViewModel = deepLinkViewModel)
        }

        // Install the settings' defaults; this is only done the first time the app is opened
        SettingsDefaults.applyOnce(this)

        // Auto start runs once per process launch, never on a recreation (rotation, theme change),
        // so turning the phone never restarts a survey the user just stopped. After the process is
        // killed it runs again, because the survey died with the process.
        if (!autoStartAttemptedThisProcess) {
            autoStartAttemptedThisProcess = true
            autoStartSurveyOnNextServiceConnection = true
        }

        surveyServiceConnection = SurveyServiceConnection()

        Application.createNotificationChannel(this)

        // Finishing or skipping first run does not resume the activity, so run the launch checks
        // (the permission request a Skip left undone, background location) when it ends.
        lifecycleScope.launch { onboardingViewModel.finished.collect { runLaunchChecks() } }

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

        // Routed directly; the activity keeps the intent it was started with, which nothing else reads.
        deepLinkDispatcher.dispatch(intent)
    }

    override fun onResume() {
        super.onResume()

        // A managed configuration or a registration link that arrived meanwhile skips first run
        onboardingViewModel.onResume()
        runLaunchChecks()

        // All we need for the cellular information is the Manifest.permission.READ_PHONE_STATE permission.  Location is optional
        if (AppPermissions.hasCellular(this)) startAndBindToNetworkSurveyService()
    }

    /**
     * Asks for what the app needs at launch, once first run is out of the way (it explains and asks
     * for the launch permissions itself): the launch permissions, once per activity, and the
     * background location explanation while the survey is set to start on its own.
     */
    private fun runLaunchChecks() {
        if (onboardingViewModel.coversTabs) return

        // The system dialog is about to show; the background location check waits for the next resume
        if (AppPermissions.missingAnyRegularPermission(this) && !onboardingViewModel.permissionsRequested) {
            requestPermissions()
            return
        }

        // As of Android 11, you have to request the Background location permission as a separate request, otherwise it
        // fails: https://developer.android.com/about/versions/11/privacy/location#background-location
        // Once per launch, so coming back from the settings page without granting it does not ask again.
        if (!backgroundLocationExplainedThisProcess && AppPermissions.shouldExplainBackgroundLocationOnLaunch(this)) {
            Timber.d("Showing the background location permission rationale dialog")
            backgroundLocationExplainedThisProcess = true
            sharedViewModel.showBackgroundLocationRationale()
        }
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

    /**
     * Request the permissions needed for this app if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private fun requestPermissions() {
        if (AppPermissions.missingAnyRegularPermission(this)) {
            // Kept in the activity's view model, so a rotation does not ask again
            onboardingViewModel.onPermissionsRequested()
            ActivityCompat.requestPermissions(this, AppPermissions.PERMISSIONS, ACCESS_PERMISSION_REQUEST_ID)
        }
    }

    /**
     * Request the background location permission, which presents the user with the App's location permission settings
     * page.
     *
     * @since 1.4.0
     */
    private fun requestBackgroundLocationPermission() {
        if (AppPermissions.missingBackgroundLocation(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    ACCESS_BACKGROUND_LOCATION_PERMISSION_REQUEST_ID
                )
            }
        }
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
     * Starts the survey if it is set to start automatically when the app opens. The service decides
     * (organization policy, the user's setting, and whether the user stopped a managed survey).
     */
    private fun startAutoSurveyIfEnabled(service: NetworkSurveyService) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                service.startAutoSurveyIfEnabled(SurveyOrigin.APP_OPEN)
            }
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

            if (autoStartSurveyOnNextServiceConnection) {
                autoStartSurveyOnNextServiceConnection = false
                startAutoSurveyIfEnabled(networkSurveyService!!)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            networkSurveyService = null
            Timber.i("%s service disconnected", name)
        }
    }

    companion object {
        private const val ACCESS_PERMISSION_REQUEST_ID = 1

        /** True once this process has tried the app-open auto start. */
        private var autoStartAttemptedThisProcess = false

        /** True once this process has shown the background location explanation. */
        private var backgroundLocationExplainedThisProcess = false

        private const val ACCESS_BACKGROUND_LOCATION_PERMISSION_REQUEST_ID = 2
    }
}
