package com.craxiom.networksurvey;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.sql.SQLException;

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionallyY) write them to a file.
 *
 * @since 0.0.1
 */
public class NetworkDetailsActivity extends AppCompatActivity
{
    private final String LOG_TAG = NetworkDetailsActivity.class.getSimpleName();

    private static final int ACCESS_LOCATION_PERMISSION_REQUEST_ID = 1;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 1000;

    private SurveyRecordWriter surveyRecordWriter;
    private LocationManager locationManager;
    private String bestProvider;
    MenuItem startStopLoggingMenuItem;
    private volatile boolean loggingEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(this, new String[]{
                        //Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                ACCESS_LOCATION_PERMISSION_REQUEST_ID);

        // TODO Delete me
        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == ACCESS_LOCATION_PERMISSION_REQUEST_ID)
        {
            for (int index = 0; index < permissions.length; index++)
            {
                switch (permissions[index])
                {
                    /*case Manifest.permission.ACCESS_COARSE_LOCATION:
                        if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                        {
                            refreshCellDetails();
                        } else
                        {
                            Log.w(LOG_TAG, "The ACCESS_COARSE_LOCATION Permission was denied.");
                        }
                        break;*/

                    case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                        if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                        {
                            initializeSurveyRecordWriter();
                        } else
                        {
                            Log.w(LOG_TAG, "The ACCESS_FINE_LOCATION Permission was denied.");
                        }
                        break;
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
                    // TODO show a toast to the user
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

    /**
     * Gets the {@link LocationManager} and the {@link TelephonyManager}, and then creates the
     * {@link SurveyRecordWriter} instance.  If something goes wrong getting access to those
     * managers, then the {@link SurveyRecordWriter} instance will not be created.
     */
    private void initializeSurveyRecordWriter()
    {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final Criteria criteria = new Criteria();
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);

        bestProvider = locationManager.getBestProvider(criteria, true);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null)
        {
            Log.w(LOG_TAG, "Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        surveyRecordWriter = new SurveyRecordWriter(locationManager,
                telephonyManager, this, bestProvider);
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
