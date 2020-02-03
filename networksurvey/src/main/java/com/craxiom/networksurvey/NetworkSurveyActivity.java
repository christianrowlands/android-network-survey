package com.craxiom.networksurvey;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.services.GrpcConnectionService;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.google.android.material.navigation.NavigationView;

import static android.location.LocationManager.GPS_PROVIDER;

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

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); // Force Dark Mode
        setContentView(R.layout.activity_network_details);
        setSupportActionBar(findViewById(R.id.toolbar));

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

        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE},
                ACCESS_PERMISSION_REQUEST_ID);

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel channel = new NotificationChannel(NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID,
                getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    protected void onResume()
    {
        // Bind to the survey service
        final Context applicationContext = getApplicationContext();
        final Intent startServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        startService(startServiceIntent);

        final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
        Log.i(LOG_TAG, "NetworkSurveyService bound in the NetworkSurveyActivity: " + bound);

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
        }

        applicationContext.unbindService(surveyServiceConnection);

        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        // TODO Figure out any logging cleanup

        super.onDestroy();
    }

    /**
     * Overriding this method so that logging is not stopped if the user hits the back button from the main activity.
     * <p>
     * The desired behavior when hitting the back button is to be the same as hitting the home button.
     */
    @Override
    public void onBackPressed()
    {
        moveTaskToBack(true);
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
                        // FIXME We need to track which permissions are granted and then take action accordingly
                        //surveyRecordLogger = new SurveyRecordLogger(this);
                        //initializeSurveyRecordScanning();

                        // TODO toggleLogging();
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
     * Sets up the GPS location provider.  If GPS location is not enabled on this device, then the settings UI is opened so the user can enable it.
     */
    private void checkLocationProvider()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final LocationProvider locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        if (locationProvider == null)
        {
            final String noGpsMessage = getString(R.string.no_gps_device);
            Log.w(LOG_TAG, noGpsMessage);
            Toast.makeText(getApplicationContext(), noGpsMessage, Toast.LENGTH_LONG).show();
        } else if (!locationManager.isProviderEnabled(GPS_PROVIDER))
        {
            // gps exists, but isn't on
            final String turnOnGpsMessage = getString(R.string.turn_on_gps);
            Log.w(LOG_TAG, turnOnGpsMessage);
            Toast.makeText(getApplicationContext(), turnOnGpsMessage, Toast.LENGTH_LONG).show();

            // Allow the user to turn on the GPS
            final Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            try
            {
                startActivity(myIntent);
            } catch (Exception ex)
            {
                Log.e(LOG_TAG, "An exception occured trying to start the location activity: ", ex);
            }
        }
    }

    /**
     * Starts or stops writing the log file based on the current state.
     */
    private void toggleLogging()
    {
        new ToggleLoggingTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

            final String menuTitle = getString(enabled ? R.string.action_stop_logging : R.string.action_start_logging);
            startStopLoggingMenuItem.setTitle(menuTitle);

            final String message;
            ColorStateList colorStateList = null;
            if (enabled)
            {
                message = getString(R.string.logging_start_toast);
                colorStateList = ColorStateList.valueOf(Color.GREEN);
            } else
            {
                message = getString(R.string.logging_stop_toast);
            }

            startStopLoggingMenuItem.setIconTintList(colorStateList);
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
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Log.i(LOG_TAG, name + " service disconnected");
        }
    }
}
