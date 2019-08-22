package com.craxiom.networksurvey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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

import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;
    private static final int DEVICE_STATUS_REFRESH_RATE_MS = 15_000;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 2_000;
    private static final int PING_RATE_MS = 10_000;

    private SurveyRecordWriter surveyRecordWriter;
    private MenuItem startStopLoggingMenuItem;
    private final AtomicBoolean loggingEnabled = new AtomicBoolean(false);
    private SectionsPagerAdapter sectionsPagerAdapter;
    private GrpcConnectionController grpcConnectionController;
    private IDeviceStatusListener deviceStatusListener;

    private String deviceId = "Device";
    private GpsListener gpsListener;
    private ServiceConnection serviceConnection;
    private NetworkSurveyService networkSurveyService;
    private ConnectionState connectionState;

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

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel channel = new NotificationChannel(NetworkSurveyService.NOTIFICATION_CHANNEL_ID,
                getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    protected void onResume()
    {
        // The first time this activity is launched, when this method is called the GPS Listener will be null.  However, this method is also called when
        // the activity goes to the background, and then comes back to the foreground.
        if (gpsListener != null && networkSurveyService == null)
        {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
                final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, (long) NETWORK_DATA_REFRESH_RATE_MS, 0f, gpsListener);
            }
        }
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        // Since we don't need location updates while in the background, remove the GPS Listener.  However, we do want to keep the GPS Listener if we are
        // logging to a file, or have an active connection to a server.  Therefore, if the service is running, then don't unregister the GPS Listener
        if (gpsListener != null && networkSurveyService == null)
        {
            final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.removeUpdates(gpsListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (gpsListener != null) locationManager.removeUpdates(gpsListener);

        if (loggingEnabled.get() && surveyRecordWriter != null)
        {
            loggingEnabled.set(false);
            surveyRecordWriter.enableLogging(false);
        }

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

                        toggleLogging();
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

    @Override
    public void onFragmentInteraction(Uri uri)
    {

    }

    @Override
    public void onGrpcConnectionStateChange(ConnectionState newConnectionState)
    {
        // TODO add a server connection status light on the toolbar
        connectionState = newConnectionState;
        switch (connectionState)
        {
            case DISCONNECTED:
                removeConnectionNotification();
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

    @SuppressWarnings("unused")
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
     * @return Returns true if the Network Details UI is visible to the user, false otherwise.
     * @since 0.0.2
     */
    boolean isNetworkDetailsVisible()
    {
        return sectionsPagerAdapter.isNetworkDetailsVisible();
    }

    /**
     * Creates the persistent notification for the server connection.
     */
    private void setupLoggingNotification()
    {
        startServiceIfNecessary();
        if (networkSurveyService != null) networkSurveyService.addLoggingNotification();
    }

    /**
     * Creates the persistent notification for the server connection.
     */
    private void setupConnectionNotification()
    {
        startServiceIfNecessary();
        if (networkSurveyService != null) networkSurveyService.addConnectionNotification();
    }

    /**
     * Read the current logging and connection states, and trigger the notifications to either appear, or disappear.
     */
    private void refreshNotifications()
    {
        if (loggingEnabled.get())
        {
            if (networkSurveyService != null) networkSurveyService.addLoggingNotification();
        } else
        {
            removeLoggingNotification();
        }

        if (connectionState == ConnectionState.CONNECTED)
        {
            if (networkSurveyService != null) networkSurveyService.addConnectionNotification();
        } else
        {
            removeConnectionNotification();
        }
    }

    /**
     * Removes the logging notification.
     */
    private void removeLoggingNotification()
    {
        if (networkSurveyService != null) networkSurveyService.removeLoggingNotification();

        stopServiceIfNecessary();
    }

    /**
     * Removes the logging notification.
     */
    private void removeConnectionNotification()
    {
        if (networkSurveyService != null) networkSurveyService.removeConnectionNotification();

        stopServiceIfNecessary();
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
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

    /**
     * Sends out a ping to the Google DNS IP Address (8.8.8.8) every n seconds.  This allow the LTE data connection to stay alive, which will enable us to get
     * Timing Advance information.
     */
    private void initializePing()
    {
        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                try
                {
                    sendPing();

                    if (loggingEnabled.get()) handler.postDelayed(this, PING_RATE_MS);
                } catch (Exception e)
                {
                    Log.e(LOG_TAG, "An exception occurred trying to send out a ping", e);
                }
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
    }

    /**
     * Sends out a ping to the Google DNS IP Address (8.8.8.8).
     */
    private void sendPing()
    {
        try
        {
            Runtime runtime = Runtime.getRuntime();
            Process ipAddressProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8");
            int exitValue = ipAddressProcess.waitFor();
            Log.d(LOG_TAG, "Ping Exit Value: " + exitValue);
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred trying to send out a ping ", e);
        }
    }

    /**
     * Looks at the state of the file logging and the server connection, and if either (or both) of them are active, the Network Survey Service is started (or
     * updated) to show a persistent notification to the user.  This also allows for the Location Listener to run in the background.
     */
    private synchronized void startServiceIfNecessary()
    {
        if (!loggingEnabled.get() && connectionState != ConnectionState.CONNECTED) return;

        if (serviceConnection == null || networkSurveyService == null)
        {
            serviceConnection = new ServiceConnection()
            {
                @Override
                public void onServiceConnected(final ComponentName name, final IBinder iBinder)
                {
                    Log.i(LOG_TAG, name + " service connected");
                    final NetworkSurveyService.ConnectionServiceBinder binder = (NetworkSurveyService.ConnectionServiceBinder) iBinder;
                    networkSurveyService = binder.getService();

                    refreshNotifications();
                }

                @Override
                public void onServiceDisconnected(final ComponentName name)
                {
                    Log.i(LOG_TAG, name + " service disconnected");
                }
            };

            final Intent serviceIntent = new Intent(this, NetworkSurveyService.class);

            // have to use the app context to bind to the service, cuz we're in tabs
            // http://code.google.com/p/android/issues/detail?id=2483#c2
            //final Intent serviceIntent = new Intent(getApplicationContext(), GrpcConnectionService.class);
            final boolean bound = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);

            Log.i(LOG_TAG, "service bound: " + bound);
        }
    }

    /**
     * Stops the Service, which will remove all the notifications and prevent location updates while the app is in the background.
     */
    private synchronized void stopServiceIfNecessary()
    {
        if (loggingEnabled.get() || connectionState == ConnectionState.CONNECTED)
        {
            Log.i(LOG_TAG, "Not stopping the service because logging is enabled or the connection is active");
            return;
        }

        if (serviceConnection != null)
        {
            Log.i(LOG_TAG, "Stopping the service");

            unbindService(serviceConnection);
        }

        networkSurveyService = null;
        serviceConnection = null;
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
            if (surveyRecordWriter != null)
            {
                synchronized (loggingEnabled)
                {
                    final boolean enabled = surveyRecordWriter.enableLogging(!loggingEnabled.get());

                    loggingEnabled.set(enabled);

                    return enabled;
                }
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
                setupLoggingNotification();
                initializePing();
                colorStateList = ColorStateList.valueOf(Color.GREEN);
            } else
            {
                message = getString(R.string.logging_stop_toast);
                removeLoggingNotification();
            }

            startStopLoggingMenuItem.setIconTintList(colorStateList);
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
