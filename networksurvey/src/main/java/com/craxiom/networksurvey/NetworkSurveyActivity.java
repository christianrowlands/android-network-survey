package com.craxiom.networksurvey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
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
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGrpcConnectionStateListener;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.messaging.DeviceStatus;

import java.sql.SQLException;

import static android.location.LocationManager.GPS_PROVIDER;

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 *
 * @since 0.0.1
 */
public class NetworkSurveyActivity extends AppCompatActivity implements
        NetworkDetailsFragment.OnFragmentInteractionListener, CalculatorFragment.OnFragmentInteractionListener, IGrpcConnectionStateListener
{
    private static final String LOG_TAG = NetworkSurveyActivity.class.getSimpleName();

    private static final int LOGGING_NOTIFICATION_ID = 1;
    private static final int CONNECTION_NOTIFICATION_ID = 2;
    public static final String NOTIFICATION_CHANNEL_ID = "network_survey_notification";

    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;
    private static final int DEVICE_STATUS_REFRESH_RATE_MS = 15_000;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 2_000;

    private SurveyRecordWriter surveyRecordWriter;
    private MenuItem startStopLoggingMenuItem;
    private boolean loggingEnabled = false;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private GrpcConnectionController grpcConnectionController;
    private IDeviceStatusListener deviceStatusListener;

    private String deviceId = "Device";
    private GpsListener gpsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Find the view pager that will allow the user to swipe between fragments
        ViewPager viewPager = findViewById(R.id.viewpager);

        grpcConnectionController = new GrpcConnectionController(this);

        // Create an adapter that knows which fragment should be shown on each page
        sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this, grpcConnectionController);

        // Set the adapter onto the view pager
        viewPager.setAdapter(sectionsPagerAdapter);

        // Give the TabLayout the ViewPager
        TabLayout tabLayout = findViewById(R.id.sliding_tabs);
        tabLayout.setupWithViewPager(viewPager);

        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE},
                ACCESS_PERMISSION_REQUEST_ID);
    }

    @Override
    protected void onDestroy()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (gpsListener != null) locationManager.removeUpdates(gpsListener);

        if (grpcConnectionController != null)
        {
            unregisterDeviceStatusListener(grpcConnectionController);
            unregisterSurveyRecordListener(grpcConnectionController);
            grpcConnectionController.unregisterConnectionListener(this);
            grpcConnectionController.disconnectFromGrpcServer();
        }

        super.onDestroy();
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

                        grpcConnectionController.registerConnectionListener(this);
                        registerDeviceStatusListener(grpcConnectionController);
                        registerSurveyRecordListener(grpcConnectionController);
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

                if (loggingEnabled)
                {
                    setupLoggingNotification();
                } else
                {
                    removeNotification(LOGGING_NOTIFICATION_ID);
                }
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(Uri uri)
    {

    }

    @Override
    public void onGrpcConnectionStateChange(ConnectionState newConnectionState)
    {
        // TODO add a server connection status light on the toolbar
        switch (newConnectionState)
        {
            case DISCONNECTED:
                removeNotification(CONNECTION_NOTIFICATION_ID);
                break;

            case CONNECTING:
                break;

            case CONNECTED:
                setupConnectionNotification();
                break;
        }
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
        if (surveyRecordWriter != null) surveyRecordWriter.registerSurveyRecordListener(surveyRecordListener);
    }

    public void unregisterSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        if (surveyRecordWriter != null) surveyRecordWriter.unregisterSurveyRecordListener(surveyRecordListener);
    }

    /**
     * Creates the persistent notification for the server connection.
     */
    private void setupLoggingNotification()
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);

        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.logging_notification_title))
                .setContentText(getText(R.string.logging_notification_text))
                .setOngoing(true)
                .setSmallIcon(R.drawable.logging_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.logging_notification_title))
                .build();

        notificationManager.notify(LOGGING_NOTIFICATION_ID, notification);
    }

    /**
     * Creates the persistent notification for the server connection.
     */
    private void setupConnectionNotification()
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);

        Intent notificationIntent = new Intent(this, NetworkSurveyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.connection_notification_title))
                .setContentText(getText(R.string.connection_notification_text))
                .setOngoing(true)
                .setSmallIcon(R.drawable.connection_icon)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.connection_notification_title))
                .build();

        notificationManager.notify(CONNECTION_NOTIFICATION_ID, notification);
    }

    /**
     * Removes the notification identified by the provided notification ID.
     *
     * @param notificationId The ID of the notification to remove.
     */
    private void removeNotification(int notificationId)
    {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        notificationManager.cancel(notificationId);
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
        checkLocationProvider();
        initializeLocationListener();

        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    if (deviceStatusListener != null && grpcConnectionController.isConnected())
                    {
                        deviceStatusListener.onDeviceStatus(generateDeviceStatus());
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
     * Registers with the LocationManager for location updates.
     */
    private void initializeLocationListener()
    {
        if (gpsListener != null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }

        gpsListener = new GpsListener(this);

        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, (long) NETWORK_DATA_REFRESH_RATE_MS, 0f, gpsListener);
    }

    /**
     * Generate a device status message that can be sent to any remote servers.
     *
     * @return A Device Status message that can be sent to a remote server.
     */
    private DeviceStatus generateDeviceStatus()
    {
        final DeviceStatus.Builder builder = DeviceStatus.newBuilder();
        builder.setDeviceSerialNumber(deviceId)
                .setDeviceName("Test Phone") // TODO Add a user setting to name the device and use it here
                .setDeviceTime(System.currentTimeMillis());

        final Location lastKnownLocation = gpsListener.getLatestLocation();
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
            final float batteryPercent = (level / (float) scale) * 100;
            builder.setBatteryLevelPercent((int) batteryPercent);
        }

        return builder.build();
    }

    /**
     * Gets the {@link TelephonyManager}, and then creates the
     * {@link SurveyRecordWriter} instance.  If something goes wrong getting access to the manager
     * then the {@link SurveyRecordWriter} instance will not be created.
     */
    private void initializeSurveyRecordWriter()
    {
        final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
        {
            Log.w(LOG_TAG, "Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        surveyRecordWriter = new SurveyRecordWriter(gpsListener, this, deviceId);

        /* Seems like this approach was a bust on my phone.  It was rarely called
        final PhoneStateListener listener = new PhoneStateListener()
        {
            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo)
            {
                surveyRecordWriter.logSurveyRecord(cellInfo);
            }
        };
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_CELL_INFO);*/

        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            final NetworkScanRequest networkScanRequest = new NetworkScanRequest(NetworkScanRequest.SCAN_TYPE_ONE_SHOT,
                    new RadioAccessSpecifier());
            final NetworkScan networkScan = telephonyManager.requestNetworkScan(networkScanRequest, AsyncTask.SERIAL_EXECUTOR, new TelephonyScanManager.NetworkScanCallback()
            {
                @Override
                public void onResults(List<CellInfo> results)
                {
                    super.onResults(results);
                }

                @Override
                public void onComplete()
                {
                    super.onComplete();
                }

                @Override
                public void onError(int error)
                {
                    super.onError(error);
                }
            });
        }*/

        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    surveyRecordWriter.logSurveyRecord(telephonyManager.getAllCellInfo());

                    handler.postDelayed(this, NETWORK_DATA_REFRESH_RATE_MS);
                } catch (SecurityException e)
                {
                    Log.e(LOG_TAG, "Could not get the required permissions to get the network details", e);
                }
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
    }
}
