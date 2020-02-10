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
import com.google.android.material.navigation.NavigationView;

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

    private MenuItem startStopLoggingMenuItem;

    private SurveyServiceConnection surveyServiceConnection;
    private NetworkSurveyService networkSurveyService;
    private boolean turnOnLoggingOnNextServiceConnection = false;

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

        // Find the view pager that will allow the user to swipe between fragments
        // TODO ViewPager viewPager = findViewById(R.id.viewpager);

        surveyServiceConnection = new SurveyServiceConnection();

        // Create an adapter that knows which fragment should be shown on each page
        // FIXME sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this, grpcConnectionController);

        // Set the adapter onto the view pager
        // TODO viewPager.setAdapter(sectionsPagerAdapter);

        // Give the TabLayout the ViewPager
        /*TODO TabLayout tabLayout = findViewById(R.id.sliding_tabs);
        tabLayout.setupWithViewPager(viewPager);*/

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

            if (!networkSurveyService.isLoggingEnabled() && GrpcConnectionService.getConnectedState() == ConnectionState.DISCONNECTED)
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
        return NavigationUI.navigateUp(navController, drawerLayout) || super.onSupportNavigateUp();
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
        startStopLoggingMenuItem = menu.findItem(R.id.action_start_stop_logging);

        if (networkSurveyService != null) updateLoggingButton(networkSurveyService.isLoggingEnabled());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_start_stop_logging)
        {
            toggleLogging();
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
     * Setup the navigation drawer.
     *
     * @since 0.0.9
     */
    private void setupNavigation()
    {
        drawerLayout = findViewById(R.id.drawer_layout);
        final NavigationView navigationView = findViewById(R.id.navigation_view);

        navController = Navigation.findNavController(this, R.id.main_content);

        final AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph())
                .setDrawerLayout(drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        NavigationUI.setupWithNavController(navigationView, navController);
    }

    /**
     * Starts or stops writing the log file based on the current state.
     */
    private void toggleLogging()
    {
        new ToggleLoggingTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        if (networkSurveyService != null && networkSurveyService.isLoggingEnabled())
        {
            networkSurveyService.initializePing();
        }
    }

    /**
     * Updates the logging button based on specified logging state.
     *
     * @param enabled True if logging is currently enabled, false otherwise.
     */
    private void updateLoggingButton(boolean enabled)
    {
        if (startStopLoggingMenuItem == null) return;

        final String menuTitle = getString(enabled ? R.string.action_stop_logging : R.string.action_start_logging);
        startStopLoggingMenuItem.setTitle(menuTitle);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        startStopLoggingMenuItem.setIconTintList(colorStateList);
    }

    /**
     * A task to move the action of starting or stopping logging off of the UI thread.
     */
    private class ToggleLoggingTask extends AsyncTask<Void, Void, Boolean>
    {
        protected Boolean doInBackground(Void... nothing)
        {
            if (networkSurveyService != null)
            {
                return networkSurveyService.toggleLogging();
            }

            return null;
        }

        protected void onPostExecute(Boolean enabled)
        {
            if (enabled == null)
            {
                // An exception occurred or something went wrong, so don't do anything
                Toast.makeText(getApplicationContext(), "Error: Could not enable Logging", Toast.LENGTH_LONG).show();
                return;
            }

            updateLoggingButton(enabled);

            final String message = getString(enabled ? R.string.logging_start_toast : R.string.logging_stop_toast);

            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
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

            final boolean loggingEnabled = networkSurveyService.isLoggingEnabled();

            if (turnOnLoggingOnNextServiceConnection && !loggingEnabled)
            {
                toggleLogging();
            } else
            {
                updateLoggingButton(loggingEnabled);
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
