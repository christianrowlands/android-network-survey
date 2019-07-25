package com.craxiom.networksurvey;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import com.craxiom.networksurvey.fragments.CalculatorFragment;
import com.craxiom.networksurvey.fragments.NetworkDetailsFragment;

import java.sql.SQLException;

/**
 * The main activity for the Network Survey App.  This app is used to pull LTE Network Survey
 * details, display them to a user, and also (optionally) write them to a file.
 *
 * @since 0.0.1
 */
public class NetworkDetailsActivity extends AppCompatActivity implements
        NetworkDetailsFragment.OnFragmentInteractionListener, CalculatorFragment.OnFragmentInteractionListener
{
    private static final String LOG_TAG = NetworkDetailsActivity.class.getSimpleName();

    public static final String NETWORK_DETAILS_FRAGMENT_TAG = "NetworkDetailsFragment";
    public static final String CALCULATOR_FRAGMENT_TAG = "CalculatorFragment";
    public static final String NETWORK_DETAILS = "Network Details";
    public static final String E_NODEB_ID_CALCULATOR = "eNodeB ID Calculator";

    private static final int ACCESS_PERMISSION_REQUEST_ID = 1;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 1000;

    private SurveyRecordWriter surveyRecordWriter;
    private MenuItem startStopLoggingMenuItem;
    private boolean loggingEnabled = false;
    private NetworkDetailsFragment networkDetailsFragment;
    private CalculatorFragment calculatorFragment;
    private CollapsingToolbarLayout toolbarLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);
        toolbarLayout = findViewById(R.id.toolbar_layout);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(this, new String[]{
                        //Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE},
                ACCESS_PERMISSION_REQUEST_ID);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener()
        {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item)
            {
                switch (item.getItemId())
                {
                    case R.id.navigation_network_details:
                        openNetworkDetailsFragment();
                        break;
                    case R.id.navigation_calculator:
                        openCalculatorFragment();
                        break;
                }
                return true;
            }
        });

        // Set the Network details fragment as the default
        initializeDefaultFragment();
        //bottomNavigationView.setSelectedItemId(R.id.navigation_network_details);

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

        if (requestCode == ACCESS_PERMISSION_REQUEST_ID)
        {
            for (int index = 0; index < permissions.length; index++)
            {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[index]))
                {
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                    {
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

    @Override
    public void onFragmentInteraction(Uri uri)
    {

    }

    /**
     * @return Returns true if the Network Details UI is visible to the user, false otherwise.
     * @since 0.0.2
     */
    boolean isNetworkDetailsVisible()
    {
        return networkDetailsFragment.isVisible();
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

    /**
     * Sets the Network Details fragment as the active fragment, and creates a new one if necessary.
     *
     * @since 0.0.2
     */
    private void initializeDefaultFragment()
    {
        networkDetailsFragment = new NetworkDetailsFragment();

        updateToolbarTitle(NETWORK_DETAILS);

        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.fragment_container, networkDetailsFragment);
        fragmentTransaction.commit();
    }

    /**
     * Sets the Network Details fragment as the active fragment, and creates a new one if necessary.
     *
     * @since 0.0.2
     */
    private void openNetworkDetailsFragment()
    {
        if (networkDetailsFragment == null)
        {
            networkDetailsFragment = new NetworkDetailsFragment();
        }

        updateToolbarTitle(NETWORK_DETAILS);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, networkDetailsFragment, NETWORK_DETAILS_FRAGMENT_TAG).commit();
    }

    /**
     * Sets the Calculator fragment as the active fragment, and creates a new one if necessary.
     *
     * @since 0.0.2
     */
    private void openCalculatorFragment()
    {
        if (calculatorFragment == null)
        {
            calculatorFragment = new CalculatorFragment();
        }

        updateToolbarTitle(E_NODEB_ID_CALCULATOR);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, calculatorFragment, CALCULATOR_FRAGMENT_TAG).commit();
    }

    /**
     * Updates the title in the Toolbar.
     *
     * @param newTitle The new title to set.
     * @since 0.0.2
     */
    private void updateToolbarTitle(String newTitle)
    {
        toolbarLayout.setTitle(newTitle);
    }
}
