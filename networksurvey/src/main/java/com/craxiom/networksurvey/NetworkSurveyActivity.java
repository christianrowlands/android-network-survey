package com.craxiom.networksurvey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.craxiom.networksurvey.fragments.CalculatorFragment;
import com.craxiom.networksurvey.fragments.NetworkDetailsFragment;
import com.craxiom.networksurvey.fragments.SectionsPagerAdapter;
import com.craxiom.networksurvey.messaging.DeviceStatus;

import java.sql.SQLException;

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 *
 * @since 0.0.1
 */
public class NetworkSurveyActivity extends AppCompatActivity implements
        NetworkDetailsFragment.OnFragmentInteractionListener, CalculatorFragment.OnFragmentInteractionListener
{
    private static final String LOG_TAG = NetworkSurveyActivity.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;
    private static final int DEVICE_STATUS_REFRESH_RATE_MS = 30_000;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 2_000;

    private SurveyRecordWriter surveyRecordWriter;
    private MenuItem startStopLoggingMenuItem;
    private boolean loggingEnabled = false;
    private SectionsPagerAdapter sectionsPagerAdapter;

    private IDeviceStatusListener deviceStatusListener;

    private String deviceId = "Device";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Find the view pager that will allow the user to swipe between fragments
        ViewPager viewPager = findViewById(R.id.viewpager);

        // Create an adapter that knows which fragment should be shown on each page
        sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this);

        // Set the adapter onto the view pager
        viewPager.setAdapter(sectionsPagerAdapter);

        // Give the TabLayout the ViewPager
        TabLayout tabLayout = findViewById(R.id.sliding_tabs);
        tabLayout.setupWithViewPager(viewPager);

        ActivityCompat.requestPermissions(this, new String[]{
                        //Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE},
                ACCESS_PERMISSION_REQUEST_ID);
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
                        deviceId = getDeviceId();  // Need to set the Device ID before initializing any of the services.
                        initializeDeviceStatusReport();
                        initializeSurveyRecordWriter();
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_start_stop_logging)
        {
            if (surveyRecordWriter != null)
            {
                // Stop writing to a log file, and update the menu option text.
                try
                {
                    surveyRecordWriter.enableLogging(!loggingEnabled);
                } catch (SQLException e)
                {
                    Log.e(LOG_TAG, "Could not setup the logging file database.  No logging will occur", e);
                    Toast.makeText(getApplicationContext(), "Error: Could not enable Logging", Toast.LENGTH_LONG).show();
                    return true;
                }

                final String menuTitle = getString(loggingEnabled ? R.string.action_start_logging : R.string.action_stop_logging);
                startStopLoggingMenuItem.setTitle(menuTitle);

                loggingEnabled = !loggingEnabled;
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(Uri uri)
    {

    }

    public void registerDeviceStatusListener(IDeviceStatusListener deviceStatusListener)
    {
        this.deviceStatusListener = deviceStatusListener;
    }

    public void unregisterDeviceStatusListener(IDeviceStatusListener deviceStatusListener)
    {
        this.deviceStatusListener = null;
    }

    public void registerSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        surveyRecordWriter.registerSurveyRecordListener(surveyRecordListener);
    }

    public void unregisterSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        surveyRecordWriter.unregisterSurveyRecordListener(surveyRecordListener);
    }

    /**
     * @return Returns true if the Network Details UI is visible to the user, false otherwise.
     * @since 0.0.2
     */
    boolean isNetworkDetailsVisible()
    {
        return sectionsPagerAdapter.isNetworkDetailsVisible();
    }

    /**
     * Attempts to get the device's IMEI if the user has granted the permission.  If not, then a default ID it used.
     *
     * @return The IMEI if it can be found, otherwise a random UUID
     */
    @SuppressLint("HardwareIds")
    private String getDeviceId()
    {
        String deviceId;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && getSystemService(Context.TELEPHONY_SERVICE) != null)
        {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                deviceId = telephonyManager.getImei();
            } else
            {
                //noinspection deprecation
                deviceId = telephonyManager.getDeviceId();
            }
        } else
        {
            Toast.makeText(getApplicationContext(), "Error: Could not get the device IMEI", Toast.LENGTH_SHORT).show();
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }

        return deviceId;
    }

    /**
     * Initialize the handler that generates a periodic Device Status Message.
     */
    private void initializeDeviceStatusReport()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        final Criteria criteria = new Criteria();
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        final String bestProvider = locationManager.getBestProvider(criteria, true);

        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    if (deviceStatusListener != null)
                    {
                        deviceStatusListener.onDeviceStatus(generateDeviceStatus(locationManager, bestProvider));
                    }

                    handler.postDelayed(this, DEVICE_STATUS_REFRESH_RATE_MS);
                } catch (SecurityException e)
                {
                    Log.e(LOG_TAG, "Could not get the required permissions to generate a device status message", e);
                }
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
    }

    /**
     * Generate a device status message that can be sent to any remote servers.
     *
     * @param locationManager The {@link LocationManager} that is used to get the current location.
     * @param bestProvider    The location provider that is used to get the location.
     * @return A Device Status message that can be sent to a remote server.
     */
    private DeviceStatus generateDeviceStatus(LocationManager locationManager, String bestProvider)
    {
        final DeviceStatus.Builder builder = DeviceStatus.newBuilder();
        builder.setDeviceSerialNumber(deviceId)
                .setDeviceName("Test Phone") // TODO Add a user setting to name the device and use it here
                .setDeviceTime(System.currentTimeMillis());

        @SuppressLint("MissingPermission") final Location lastKnownLocation = locationManager.getLastKnownLocation(bestProvider);
        if (lastKnownLocation != null)
        {
            builder.setLatitude(lastKnownLocation.getLatitude());
            builder.setLongitude(lastKnownLocation.getLongitude());
            builder.setAltitude((float) lastKnownLocation.getAltitude());
        }

        final IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = registerReceiver(null, intentFilter);
        if (batteryStatus != null)
        {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final float batteryPercent = level / (float) scale;
            builder.setBatteryLevelPercent((int) batteryPercent);
        }

        return builder.build();
    }

    /**
     * Gets the {@link LocationManager} and the {@link TelephonyManager}, and then creates the
     * {@link SurveyRecordWriter} instance.  If something goes wrong getting access to those
     * managers, then the {@link SurveyRecordWriter} instance will not be created.
     */
    private void initializeSurveyRecordWriter()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final Criteria criteria = new Criteria();
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);

        final String bestProvider = locationManager.getBestProvider(criteria, true);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null)
        {
            Log.w(LOG_TAG, "Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        surveyRecordWriter = new SurveyRecordWriter(locationManager, telephonyManager, this, bestProvider, deviceId);
        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    surveyRecordWriter.logSurveyRecord();

                    handler.postDelayed(this, NETWORK_DATA_REFRESH_RATE_MS);
                } catch (SecurityException e)
                {
                    Log.e(LOG_TAG, "Could not get the required permissions to get the network details", e);
                }
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
    }
}
