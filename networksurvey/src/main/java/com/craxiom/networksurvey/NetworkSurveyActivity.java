package com.craxiom.networksurvey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.services.GrpcConnectionService;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 *
 * @since 0.0.1
 */
public class NetworkSurveyActivity extends AppCompatActivity
{
    private static final String LOG_TAG = NetworkSurveyActivity.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE};

    public DrawerLayout drawerLayout;
    public NavController navController;

    private MenuItem startStopCellularLoggingMenuItem;
    private MenuItem startStopWifiLoggingMenuItem;
    private MenuItem startStopGnssLoggingMenuItem;

    private SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private boolean turnOnCellularLoggingOnNextServiceConnection = false;
    private boolean turnOnWifiLoggingOnNextServiceConnection = false;
    private boolean turnOnGnssLoggingOnNextServiceConnection = false;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); // Force Dark Mode
        setContentView(R.layout.activity_network_details);
        setSupportActionBar(findViewById(R.id.toolbar));

        // Install the defaults specified in the XML preferences file, this is only done the first time the app is opened
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        turnOnCellularLoggingOnNextServiceConnection = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, false);
        turnOnWifiLoggingOnNextServiceConnection = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false);
        turnOnGnssLoggingOnNextServiceConnection = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, false);

        setupNavigation();

        surveyServiceConnection = new SurveyServiceConnection();

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null)
        {
            final NotificationChannel channel = new NotificationChannel(NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID,
                    getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        } else
        {
            Log.e(LOG_TAG, "The Notification Manager could not be retrieved to add the Network Survey notification channel");
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (missingAnyPermissions()) showPermissionRationaleAndRequestPermissions();

        // If we have been granted the location permission, we want to check to see if the location service is enabled.
        // If it is not, then this call will report that to the user and give them the option to enable it.
        if (hasLocationPermission()) checkLocationProvider();

        // All we need for the cellular information is the Manifest.permission.READ_PHONE_STATE permission.  Location is optional
        if (hasCellularPermission()) startAndBindToNetworkSurveyService();
    }

    @Override
    protected void onPause()
    {
        final Context applicationContext = getApplicationContext();

        if (networkSurveyService != null)
        {
            networkSurveyService.onUiHidden();

            if (!networkSurveyService.isBeingUsed())
            {
                // We can safely shutdown the service since both logging and the connections are turned off
                final Intent networkSurveyServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
                final Intent connectionServiceIntent = new Intent(applicationContext, GrpcConnectionService.class);
                stopService(networkSurveyServiceIntent);
                stopService(connectionServiceIntent);
            }

            applicationContext.unbindService(surveyServiceConnection);
        }

        super.onPause();
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
                        checkLocationProvider();
                        startAndBindToNetworkSurveyService();
                    } else
                    {
                        Log.w(LOG_TAG, "The ACCESS_FINE_LOCATION Permission was denied.");
                    }
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_network_details, menu);
        startStopCellularLoggingMenuItem = menu.findItem(R.id.action_start_stop_cellular_logging);
        startStopWifiLoggingMenuItem = menu.findItem(R.id.action_start_stop_wifi_logging);
        startStopGnssLoggingMenuItem = menu.findItem(R.id.action_start_stop_gnss_logging);

        if (networkSurveyService != null)
        {
            updateCellularLoggingButton(networkSurveyService.isCellularLoggingEnabled());
            updateWifiLoggingButton(networkSurveyService.isWifiLoggingEnabled());
            updateGnssLoggingButton(networkSurveyService.isGnssLoggingEnabled());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_start_stop_cellular_logging)
        {
            toggleCellularLogging(!networkSurveyService.isCellularLoggingEnabled());
            return true;
        } else if (id == R.id.action_start_stop_wifi_logging)
        {
            toggleWifiLogging(!networkSurveyService.isWifiLoggingEnabled());
            return true;
        } else if (id == R.id.action_start_stop_gnss_logging)
        {
            toggleGnssLogging(!networkSurveyService.isGnssLoggingEnabled());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Check to see if we should show the rationale for any of the permissions.  If so, then display a dialog that
     * explains what permissions we need for this app to work properly.
     * <p>
     * If we should not show the rationale, then just request the permissions.
     */
    private void showPermissionRationaleAndRequestPermissions()
    {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE))
        {
            Log.d(LOG_TAG, "Showing the permissions rationale dialog");

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.permissions_rationale));
            alertBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> requestPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        } else
        {
            requestPermissions();
        }
    }

    /**
     * Request the permissions needed for this app if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestPermissions()
    {
        if (missingAnyPermissions())
        {
            ActivityCompat.requestPermissions(this, PERMISSIONS, ACCESS_PERMISSION_REQUEST_ID);
        }
    }

    /**
     * Checks that the location provider is enabled and that the location permission has been granted.  If GPS location
     * is not enabled on this device, then the settings UI is opened so the user can enable it.
     */
    private void checkLocationProvider()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
        {
            Log.w(LOG_TAG, "Could not get the location manager.  Skipping checking the location provider");
            return;
        }

        final LocationProvider locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (locationProvider == null)
        {
            final String noGpsMessage = getString(R.string.no_gps_device);
            Log.w(LOG_TAG, noGpsMessage);
            Toast.makeText(getApplicationContext(), noGpsMessage, Toast.LENGTH_LONG).show();
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            // gps exists, but isn't on
            final String turnOnGpsMessage = getString(R.string.turn_on_gps);
            Log.w(LOG_TAG, turnOnGpsMessage);
            Toast.makeText(getApplicationContext(), turnOnGpsMessage, Toast.LENGTH_LONG).show();

            promptEnableGps();
        }
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
    private boolean missingAnyPermissions()
    {
        for (String permission : PERMISSIONS)
        {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
            {
                Log.i(LOG_TAG, "Missing the permission: " + permission);
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
            Log.w(LOG_TAG, "The ACCESS_FINE_LOCATION permission has not been granted");
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
            Log.w(LOG_TAG, "The READ_PHONE_STATE permission has not been granted");
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
        // Start and bind to the survey service
        final Context applicationContext = getApplicationContext();
        final Intent startServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        startService(startServiceIntent);

        final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
        Log.i(LOG_TAG, "NetworkSurveyService bound in the NetworkSurveyActivity: " + bound);
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

        navController = Navigation.findNavController(this, R.id.main_content);

        appBarConfiguration = new AppBarConfiguration.Builder(R.id.main_cellular_fragment, R.id.main_wifi_fragment, R.id.main_gnss_fragment)
                .setDrawerLayout(drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        NavigationUI.setupWithNavController(navigationView, navController);
        NavigationUI.setupWithNavController(bottomNav, navController);

        OnBackPressedCallback callback = new OnBackPressedCallback(true)
        {
            @Override
            public void handleOnBackPressed()
            {
                // For whatever reason calling navigateUp from one of the top level destinations results in the
                // navigation drawer being opened.  Therefore, if the current destination a top level we have custom
                // code here to move this activity to the back stack.
                final NavDestination currentDestination = navController.getCurrentDestination();
                if (currentDestination != null &&
                        (currentDestination.getId() == R.id.main_cellular_fragment
                                || currentDestination.getId() == R.id.main_wifi_fragment
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
     * Starts or stops writing the Cellular log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleCellularLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null) return networkSurveyService.toggleCellularLogging(enable);
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.cellular_logging_toggle_failed);
            updateCellularLoggingButton(enabled);
            return getString(enabled ? R.string.cellular_logging_start_toast : R.string.cellular_logging_stop_toast);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
            updateWifiLoggingButton(enabled);
            return getString(enabled ? R.string.wifi_logging_start_toast : R.string.wifi_logging_stop_toast);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the GNSS log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleGnssLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null) return networkSurveyService.toggleGnssLogging(enable);
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.gnss_logging_toggle_failed);
            updateGnssLoggingButton(enabled);
            return getString(enabled ? R.string.gnss_logging_start_toast : R.string.gnss_logging_stop_toast);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Updates the Cellular logging button based on the specified logging state.
     *
     * @param enabled True if logging is currently enabled, false otherwise.
     */
    private void updateCellularLoggingButton(boolean enabled)
    {
        if (startStopCellularLoggingMenuItem == null) return;

        final String menuTitle = getString(enabled ? R.string.action_stop_cellular_logging : R.string.action_start_cellular_logging);
        startStopCellularLoggingMenuItem.setTitle(menuTitle);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        startStopCellularLoggingMenuItem.setIconTintList(colorStateList);
    }

    /**
     * Updates the Wi-Fi logging button based on the specified logging state.
     *
     * @param enabled True if logging is currently enabled, false otherwise.
     * @since 0.1.2
     */
    private void updateWifiLoggingButton(boolean enabled)
    {
        if (startStopWifiLoggingMenuItem == null) return;

        final String menuTitle = getString(enabled ? R.string.action_stop_wifi_logging : R.string.action_start_wifi_logging);
        startStopWifiLoggingMenuItem.setTitle(menuTitle);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        startStopWifiLoggingMenuItem.setIconTintList(colorStateList);
    }

    /**
     * Updates the GNSS logging button based on the specified logging state.
     *
     * @param enabled True if logging is currently enabled, false otherwise.
     */
    private void updateGnssLoggingButton(boolean enabled)
    {
        if (startStopGnssLoggingMenuItem == null) return;

        final String menuTitle = getString(enabled ? R.string.action_stop_gnss_logging : R.string.action_start_gnss_logging);
        startStopGnssLoggingMenuItem.setTitle(menuTitle);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        startStopGnssLoggingMenuItem.setIconTintList(colorStateList);
    }

    /**
     * A task to move the action of starting or stopping logging off of the UI thread.
     */
    @SuppressLint("StaticFieldLeak")
    private class ToggleLoggingTask extends AsyncTask<Void, Void, Boolean>
    {
        private final Supplier<Boolean> toggleLoggingFunction;
        private final Function<Boolean, String> postExecuteFunction;

        private ToggleLoggingTask(Supplier<Boolean> toggleLoggingFunction, Function<Boolean, String> postExecuteFunction)
        {
            this.toggleLoggingFunction = toggleLoggingFunction;
            this.postExecuteFunction = postExecuteFunction;
        }

        @Override
        protected Boolean doInBackground(Void... nothing)
        {
            return toggleLoggingFunction.get();
        }

        @Override
        protected void onPostExecute(Boolean enabled)
        {
            if (enabled == null)
            {
                // An exception occurred or something went wrong, so don't do anything
                Toast.makeText(getApplicationContext(), "Error: Could not enable Logging", Toast.LENGTH_LONG).show();
                return;
            }

            Toast.makeText(getApplicationContext(), postExecuteFunction.apply(enabled), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link NetworkSurveyService}.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder iBinder)
        {
            Log.i(LOG_TAG, name + " service connected");
            final NetworkSurveyService.SurveyServiceBinder binder = (NetworkSurveyService.SurveyServiceBinder) iBinder;
            networkSurveyService = binder.getService();
            networkSurveyService.onUiVisible(NetworkSurveyActivity.this);

            final boolean cellularLoggingEnabled = networkSurveyService.isCellularLoggingEnabled();
            if (turnOnCellularLoggingOnNextServiceConnection && !cellularLoggingEnabled)
            {
                toggleCellularLogging(true);
            } else
            {
                updateCellularLoggingButton(cellularLoggingEnabled);
            }

            final boolean wifiLoggingEnabled = networkSurveyService.isWifiLoggingEnabled();
            if (turnOnWifiLoggingOnNextServiceConnection && !wifiLoggingEnabled)
            {
                toggleWifiLogging(true);
            } else
            {
                updateWifiLoggingButton(wifiLoggingEnabled);
            }

            final boolean gnssLoggingEnabled = networkSurveyService.isGnssLoggingEnabled();
            if (turnOnGnssLoggingOnNextServiceConnection && !gnssLoggingEnabled)
            {
                toggleGnssLogging(true);
            } else
            {
                updateGnssLoggingButton(gnssLoggingEnabled);
            }

            turnOnCellularLoggingOnNextServiceConnection = false;
            turnOnWifiLoggingOnNextServiceConnection = false;
            turnOnGnssLoggingOnNextServiceConnection = false;
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            networkSurveyService = null;
            Log.i(LOG_TAG, name + " service disconnected");
        }
    }
}
