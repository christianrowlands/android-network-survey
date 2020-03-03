package com.craxiom.networksurvey;

import android.Manifest;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
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

    public DrawerLayout drawerLayout;
    public NavController navController;

    private MenuItem startStopCellularLoggingMenuItem;
    private MenuItem startStopGnssLoggingMenuItem;

    private SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private boolean turnOnLoggingOnNextServiceConnection = false;
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
        turnOnLoggingOnNextServiceConnection = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_LOGGING_KEY, false);

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
            Log.e(LOG_TAG, "The Notification Manager could not be retrieved to add the Network Suryey notification channel");
        }

        showPermissionRationaleAndRequestPermissions();
    }

    @Override
    protected void onResume()
    {
        startAndBindToNetworkSurveyService();

        super.onResume();
    }

    @Override
    protected void onPause()
    {
        final Context applicationContext = getApplicationContext();

        if (networkSurveyService != null)
        {
            networkSurveyService.onUiHidden();

            if (!networkSurveyService.isCellularLoggingEnabled() && GrpcConnectionService.getConnectedState() == ConnectionState.DISCONNECTED)
            {
                // We can safely shutdown the service since both logging and the connection are turned off
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
    protected void onDestroy()
    {
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
        startStopGnssLoggingMenuItem = menu.findItem(R.id.action_start_stop_gnss_logging);

        if (networkSurveyService != null)
        {
            updateCellularLoggingButton(networkSurveyService.isCellularLoggingEnabled());
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
            toggleCellularLogging();
            return true;
        } else if (id == R.id.action_start_stop_gnss_logging)
        {
            toggleGnssLogging(!item.isChecked());
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
     * Request the permissions needed for this app.
     */
    private void requestPermissions()
    {
        ActivityCompat.requestPermissions(NetworkSurveyActivity.this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE},
                ACCESS_PERMISSION_REQUEST_ID);
    }

    private void startAndBindToNetworkSurveyService()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Log.w(LOG_TAG, "The ACCESS_FINE_LOCATION permission has not been granted, not starting the service");
            return;
        }

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

        appBarConfiguration = new AppBarConfiguration.Builder(R.id.main_cellular_fragment, R.id.main_gnss_fragment)
                .setDrawerLayout(drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        NavigationUI.setupWithNavController(navigationView, navController);
        NavigationUI.setupWithNavController(bottomNav, navController);
    }

    /**
     * Starts or stops writing the log file based on the current state.
     */
    private void toggleCellularLogging()
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null) return networkSurveyService.toggleLogging();
            return null;
        }, enabled -> {
            updateCellularLoggingButton(enabled);
            return getString(enabled ? R.string.cellular_logging_start_toast : R.string.cellular_logging_stop_toast);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        // FIXME I thought I already fixed this but I guess not.  Checking if logging is enabled here results in a race
        // condition since the ToggleLoggingTask above runs on a different thread.
        if (networkSurveyService != null && networkSurveyService.isCellularLoggingEnabled())
        {
            networkSurveyService.initializePing();
        }
    }

    /**
     * Starts or stops writing the GNSS log file based on the current state.
     */
    private void toggleGnssLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (networkSurveyService != null) return networkSurveyService.toggleGnssLogging(enable);
            return null;
        }, enabled -> {
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
    private class ToggleLoggingTask extends AsyncTask<Void, Void, Boolean>
    {
        private final Supplier<Boolean> toggleLoggingFunction;
        private final Function<Boolean, String> postExecuteFunction;

        private ToggleLoggingTask(Supplier<Boolean> toggleLoggingFunction, Function<Boolean, String> postExecuteFunction)
        {
            this.toggleLoggingFunction = toggleLoggingFunction;
            this.postExecuteFunction = postExecuteFunction;
        }

        protected Boolean doInBackground(Void... nothing)
        {
            return toggleLoggingFunction.get();
        }

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
     * A {@link ServiceConnection} implementation for binding to the {@link GrpcConnectionService}.
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

            final boolean loggingEnabled = networkSurveyService.isCellularLoggingEnabled();

            if (turnOnLoggingOnNextServiceConnection && !loggingEnabled)
            {
                toggleCellularLogging();
            } else
            {
                updateCellularLoggingButton(loggingEnabled);
            }

            turnOnLoggingOnNextServiceConnection = false;
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            networkSurveyService = null;
            Log.i(LOG_TAG, name + " service disconnected");
        }
    }
}
