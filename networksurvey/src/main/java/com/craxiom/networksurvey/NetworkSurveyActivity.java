package com.craxiom.networksurvey;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IGnssFailureListener;
import com.craxiom.networksurvey.services.GrpcConnectionService;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.craxiom.networksurvey.util.ToggleLoggingTask;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 *
 * @since 0.0.1
 */
@AndroidEntryPoint
public class NetworkSurveyActivity extends AppCompatActivity
{
    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;
    public static final String[] PERMISSIONS;

    // The BLUETOOTH_CONNECT and BLUETOOTH_SCAN permissions are only for Android 12 and above.
    public static final String[] BLUETOOTH_PERMISSIONS;

    static
    {
        // Android 13+ (SDK 33) requires permission for push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            BLUETOOTH_PERMISSIONS = new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN};
            PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            BLUETOOTH_PERMISSIONS = new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN};
            PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE};
        } else
        {
            BLUETOOTH_PERMISSIONS = new String[]{};
            PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE};
        }
    }

    private static final int ACCESS_BACKGROUND_LOCATION_PERMISSION_REQUEST_ID = 2;

    public DrawerLayout drawerLayout;
    public NavController navController;

    private SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private boolean turnOnCellularLoggingOnNextServiceConnection = false;
    private boolean turnOnWifiLoggingOnNextServiceConnection = false;
    private boolean turnOnBluetoothLoggingOnNextServiceConnection = false;
    private boolean turnOnGnssLoggingOnNextServiceConnection = false;
    private AppBarConfiguration appBarConfiguration;
    private IGnssFailureListener gnssFailureListener;
    private boolean hasRequestedPermissions = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); // Force Dark Mode
        setContentView(R.layout.activity_network_details);
        setSupportActionBar(findViewById(R.id.toolbar));

        // Install the defaults specified in the XML preferences file, this is only done the first time the app is opened
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        final Context applicationContext = getApplicationContext();
        turnOnCellularLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, false, applicationContext);
        turnOnWifiLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false, applicationContext);
        turnOnBluetoothLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING, false, applicationContext);
        turnOnGnssLoggingOnNextServiceConnection = PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, false, applicationContext);

        setupNavigation();

        // Set the version number at the bottom of the navigation drawer
        setAppVersionNumber();

        surveyServiceConnection = new SurveyServiceConnection();

        Application.createNotificationChannel(this);

        gnssFailureListener = () -> {
            try
            {
                final View fragmentView = LayoutInflater.from(this).inflate(R.layout.gnss_failure, null);

                AlertDialog gnssFailureDialog = new AlertDialog.Builder(this)
                        .setView(fragmentView)
                        .setPositiveButton(R.string.ok, (dialog, id) -> {
                            CheckBox rememberDecisionCheckBox = fragmentView.findViewById(R.id.failureRememberDecisionCheckBox);
                            boolean checked = rememberDecisionCheckBox.isChecked();
                            if (checked)
                            {
                                PreferenceUtils.saveBoolean(Application.get().getString(R.string.pref_key_ignore_raw_gnss_failure), true);
                                // No need for GNSS failure updates anymore
                                if (networkSurveyService != null)
                                {
                                    networkSurveyService.clearGnssFailureListener();
                                }
                            }
                        })
                        .create();

                gnssFailureDialog.show();
                final TextView viewById = gnssFailureDialog.findViewById(R.id.failureDescriptionTextView);
                if (viewById != null) viewById.setMovementMethod(LinkMovementMethod.getInstance());
            } catch (Throwable t)
            {
                Timber.e(t, "Something went wrong when trying to show the GNSS Failure Dialog");
            }
        };
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (missingAnyRegularPermissions()) showPermissionRationaleAndRequestPermissions();

        // If we have been granted the location permission, we want to check to see if the location service is enabled.
        // If it is not, then this call will report that to the user and give them the option to enable it.
        if (hasLocationPermission()) checkLocationProvider(true);

        // As of Android 11, you have to request the Background location permission as a separate request, otherwise it
        // fails: https://developer.android.com/about/versions/11/privacy/location#background-location
        if (missingBackgroundLocationPermission()) showBackgroundLocationRationaleAndRequest();

        // All we need for the cellular information is the Manifest.permission.READ_PHONE_STATE permission.  Location is optional
        if (hasCellularPermission()) startAndBindToNetworkSurveyService();
    }

    @Override
    protected void onPause()
    {
        if (networkSurveyService != null)
        {
            final Context applicationContext = getApplicationContext();

            networkSurveyService.onUiHidden();

            if (!networkSurveyService.isBeingUsed())
            {
                // We can safely shutdown the service since both logging and the connections are turned off
                final Intent networkSurveyServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
                final Intent connectionServiceIntent = new Intent(applicationContext, GrpcConnectionService.class);
                stopService(networkSurveyServiceIntent);
                stopService(connectionServiceIntent);
            }

            try
            {
                applicationContext.unbindService(surveyServiceConnection);
                networkSurveyService = null;
            } catch (IllegalArgumentException e)
            {
                Timber.e(e, "Could not unbind the service because it is not bound.");
            }
        }

        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        networkSurveyService = null;
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp()
    {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ACCESS_PERMISSION_REQUEST_ID)
        {
            for (int index = 0; index < permissions.length; index++)
            {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[index]))
                {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                    {
                        checkLocationProvider(true);
                        startAndBindToNetworkSurveyService();
                    } else
                    {
                        Timber.w("The ACCESS_FINE_LOCATION Permission was denied.");
                    }
                }
            }
        }
    }

    /**
     * Check to see if we should show the rationale for any of the regular permissions. If so, then display a dialog that
     * explains what permissions we need for this app to work properly.
     * <p>
     * If we should not show the rationale, then just request the permissions.
     */
    private void showPermissionRationaleAndRequestPermissions()
    {
        boolean shouldShowPermissionsRationale = false;
        for (String cdrPermission : PERMISSIONS)
        {
            // If we are on Android 13+ and the permission is for the notification, then don't
            // show the permission rationale. This is because the app gets stuck in a permission
            // loop because the shouldShowRequestPermissionRationale method was always returning
            // true for POST_NOTIFICATION, but when requesting the permission it was never prompting
            // the user. I hope this bug is fixed in future Android versions.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    || !Manifest.permission.POST_NOTIFICATIONS.equals(cdrPermission))
            {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, cdrPermission))
                {
                    shouldShowPermissionsRationale = true;
                    break;
                }
            }
        }

        if (shouldShowPermissionsRationale)
        {
            Timber.d("Showing the permissions rationale dialog");

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.permissions_rationale));
            alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> requestPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        } else if (!hasRequestedPermissions && !hasLocationPermission())
        {
            Timber.d("Showing the location permissions rationale dialog");

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.location_permission_rationale_title));
            alertBuilder.setMessage(getText(R.string.location_permission_rationale));
            alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> requestPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        } else if (!hasRequestedPermissions)
        {
            requestPermissions();
        }
    }

    /**
     * Check to see if we should show the rationale for the background location permission.  If so, then display a
     * dialog that explains why we need the background location permission.
     * <p>
     * We can only request the background location permission if the user has already granted the general location
     * permission.
     *
     * @since 1.4.0
     */
    private void showBackgroundLocationRationaleAndRequest()
    {
        if (hasLocationPermission() && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        {
            Timber.d("Showing the background location permission rationale dialog");

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.background_location_permission_rationale_title));
            alertBuilder.setMessage(getText(R.string.background_location_permission_rationale));
            alertBuilder.setPositiveButton(R.string.open_settings, (dialog, which) -> requestBackgroundLocationPermission());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        }
    }

    /**
     * Request the permissions needed for this app if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestPermissions()
    {
        if (missingAnyRegularPermissions())
        {
            hasRequestedPermissions = true;
            ActivityCompat.requestPermissions(this, PERMISSIONS, ACCESS_PERMISSION_REQUEST_ID);
        }
    }

    /**
     * Request the background location permission, which presents the user with the App's location permission settings
     * page.
     *
     * @since 1.4.0
     */
    private void requestBackgroundLocationPermission()
    {
        if (missingBackgroundLocationPermission())
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, ACCESS_BACKGROUND_LOCATION_PERMISSION_REQUEST_ID);
            }
        }
    }

    /**
     * Checks that the location provider is enabled.  If GPS location is not enabled on this device, and
     * {@code informUser} is set to true, then the settings UI is opened so the user can enable it.
     * <p>
     * If either the GPS device is not present, or if the GPS provider is disabled, an appropriate toast message is
     * displayed as long as the {@code informUser} parameter is set to true.
     *
     * @param informUser If this method should display a toast and prompt the user to enable GPS set this to true,
     *                   false otherwise.
     * @return True if the device has GPS capabilities, and location services are enabled on the device. False otherwise.
     */
    private boolean checkLocationProvider(boolean informUser)
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
        {
            Timber.w("Could not get the location manager.  Skipping checking the location provider");
            return false;
        }

        if (!hasLocationPermission())
        {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Missing location permission", Toast.LENGTH_SHORT).show());
            return false;
        }

        final LocationProvider locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (locationProvider == null)
        {
            final String noGpsMessage = getString(R.string.no_gps_device);
            Timber.w(noGpsMessage);
            if (informUser)
            {
                Toast.makeText(getApplicationContext(), noGpsMessage, Toast.LENGTH_LONG).show();
            }
            return false;
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            // gps exists, but isn't on
            final String turnOnGpsMessage = getString(R.string.turn_on_gps);
            Timber.w(turnOnGpsMessage);
            if (informUser)
            {
                Toast.makeText(getApplicationContext(), turnOnGpsMessage, Toast.LENGTH_LONG).show();

                promptEnableGps();
            }
            return false;
        }

        return true;
    }

    /**
     * Ask the user if they want to enable GPS.  If they do, then open the Location settings.
     */
    private void promptEnableGps()
    {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.enable_gps_message))
                .setPositiveButton(getString(R.string.enable_gps_positive_button),
                        (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                )
                .setNegativeButton(getString(R.string.enable_gps_negative_button),
                        (dialog, which) -> {
                        }
                )
                .show();
    }

    /**
     * @return True if any of the permissions for this app have been denied.  False if all the permissions have been granted.
     */
    private boolean missingAnyRegularPermissions()
    {
        for (String permission : PERMISSIONS)
        {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                Timber.i("Missing the permission: %s", permission);
                return true;
            }
        }

        return false;
    }

    /**
     * @return True if the background location permission for this app has been denied; false otherwise.
     * @since 1.4.0
     */
    private boolean missingBackgroundLocationPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            {
                Timber.i("Missing the permission: %s", Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                return true;
            }
        }

        return false;
    }

    /**
     * @return True if the {@link Manifest.permission#ACCESS_FINE_LOCATION} permission has been granted.  False otherwise.
     */
    private boolean hasLocationPermission()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The ACCESS_FINE_LOCATION permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * @return True if the {@link Manifest.permission#READ_PHONE_STATE} permission has been granted.  False otherwise.
     */
    private boolean hasCellularPermission()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The READ_PHONE_STATE permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Start the Network Survey Service (it won't start if it is already started), and then bind to the service.
     * <p>
     * Starting the service will cause the cellular records to be pulled from the Android system so they can be shown
     * in the UI, logged to a file, sent over a connection, or any combination of the three.
     * <p>
     * The Network survey service also handles getting GNSS information so that it can be used accordingly.
     */
    private void startAndBindToNetworkSurveyService()
    {
        try
        {
            // Start and bind to the survey service
            final Context applicationContext = getApplicationContext();
            final Intent startServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
            startService(startServiceIntent);

            final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
            final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
            Timber.i("NetworkSurveyService bound in the NetworkSurveyActivity: %s", bound);
        } catch (IllegalStateException e)
        {
            // It appears that an IllegalStateException will occur if the user opens this app but the then quickly
            // switches away from it. The IllegalStateException indicates that we can't call startService while the
            // app is in the background. We catch this here so that we can prevent the app from crashing.
            Timber.w(e, "Could not start the Network Survey service.");
        }
    }

    /**
     * Setup the navigation drawer and the bottom navigation view.
     *
     * @since 0.0.9
     */
    private void setupNavigation()
    {
        drawerLayout = findViewById(R.id.drawer_layout);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        final NavigationView navigationView = findViewById(R.id.navigation_view);

        final NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.main_content);
        navController = navHostFragment.getNavController();

        appBarConfiguration = new AppBarConfiguration.Builder(R.id.main_dashboard_fragment, R.id.main_cellular_fragment, R.id.main_wifi_fragment, R.id.main_bluetooth_fragment, R.id.main_gnss_fragment)
                .setOpenableLayout(drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        NavigationUI.setupWithNavController(navigationView, navController);
        NavigationUI.setupWithNavController(bottomNav, navController);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            final int destinationId = destination.getId();
            if (destinationId == R.id.main_dashboard_fragment
                    || destinationId == R.id.main_cellular_fragment
                    || destinationId == R.id.main_wifi_fragment
                    || destinationId == R.id.main_bluetooth_fragment
                    || destinationId == R.id.main_gnss_fragment)
            {
                bottomNav.setVisibility(View.VISIBLE);
            } else
            {
                bottomNav.setVisibility(View.GONE);
            }
        });

        OnBackPressedCallback callback = new OnBackPressedCallback(true)
        {
            @Override
            public void handleOnBackPressed()
            {
                // For whatever reason calling navigateUp from one of the top level destinations results in the
                // navigation drawer being opened.  Therefore, if the current destination is a top level we have custom
                // code here to move this activity to the back stack.
                final NavDestination currentDestination = navController.getCurrentDestination();
                if (currentDestination != null &&
                        (currentDestination.getId() == R.id.main_dashboard_fragment
                                || currentDestination.getId() == R.id.main_cellular_fragment
                                || currentDestination.getId() == R.id.main_wifi_fragment
                                || currentDestination.getId() == R.id.main_bluetooth_fragment
                                || currentDestination.getId() == R.id.main_gnss_fragment))
                {
                    moveTaskToBack(true);
                } else
                {
                    NavigationUI.navigateUp(navController, appBarConfiguration);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    /**
     * Get the app version number and set it at the bottom of the navigation drawer.
     *
     * @since 0.1.2
     */
    private void setAppVersionNumber()
    {
        try
        {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            final TextView appVersionView = findViewById(R.id.app_version_name);
            appVersionView.setText(getString(R.string.app_version, info.versionName));
        } catch (Exception e)
        {
            Timber.wtf(e, "Could not set the app version number");
        }
    }

    /**
     * Starts or stops writing the Cellular log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleCellularLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null)
            {
                return networkSurveyService.toggleCellularLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.cellular_logging_toggle_failed);
            return getString(enabled ? R.string.cellular_logging_start_toast : R.string.cellular_logging_stop_toast);
        }, getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the Wi-Fi log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @since 0.1.2
     */
    private void toggleWifiLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null) return networkSurveyService.toggleWifiLogging(enable);
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.wifi_logging_toggle_failed);
            return getString(enabled ? R.string.wifi_logging_start_toast : R.string.wifi_logging_stop_toast);
        }, getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the Bluetooth log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     * @since 1.0.0
     */
    private void toggleBluetoothLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null)
            {
                return networkSurveyService.toggleBluetoothLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.bluetooth_logging_toggle_failed);
            return getString(enabled ? R.string.bluetooth_logging_start_toast : R.string.bluetooth_logging_stop_toast);
        }, getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the GNSS log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleGnssLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (!checkLocationProvider(false)) return null;
            if (networkSurveyService != null) return networkSurveyService.toggleGnssLogging(enable);
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.gnss_logging_toggle_failed);
            return getString(enabled ? R.string.gnss_logging_start_toast : R.string.gnss_logging_stop_toast);
        }, getApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link NetworkSurveyService}.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder iBinder)
        {
            Timber.i("%s service connected", name);

            final NetworkSurveyService.SurveyServiceBinder binder = (NetworkSurveyService.SurveyServiceBinder) iBinder;
            networkSurveyService = (NetworkSurveyService) binder.getService();
            networkSurveyService.onUiVisible(NetworkSurveyActivity.this);
            networkSurveyService.registerGnssFailureListener(gnssFailureListener);

            final boolean cellularLoggingEnabled = networkSurveyService.isCellularLoggingEnabled();
            if (turnOnCellularLoggingOnNextServiceConnection && !cellularLoggingEnabled)
            {
                toggleCellularLogging(true);
            }

            final boolean wifiLoggingEnabled = networkSurveyService.isWifiLoggingEnabled();
            if (turnOnWifiLoggingOnNextServiceConnection && !wifiLoggingEnabled)
            {
                toggleWifiLogging(true);
            }

            final boolean bluetoothLoggingEnabled = networkSurveyService.isBluetoothLoggingEnabled();
            if (turnOnBluetoothLoggingOnNextServiceConnection && !bluetoothLoggingEnabled)
            {
                toggleBluetoothLogging(true);
            }

            final boolean gnssLoggingEnabled = networkSurveyService.isGnssLoggingEnabled();
            if (turnOnGnssLoggingOnNextServiceConnection && !gnssLoggingEnabled)
            {
                toggleGnssLogging(true);
            }

            turnOnCellularLoggingOnNextServiceConnection = false;
            turnOnWifiLoggingOnNextServiceConnection = false;
            turnOnBluetoothLoggingOnNextServiceConnection = false;
            turnOnGnssLoggingOnNextServiceConnection = false;
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            networkSurveyService = null;
            Timber.i("%s service disconnected", name);
        }
    }
}
