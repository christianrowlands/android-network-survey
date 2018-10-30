package com.craxiom.networksurvey;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.List;

public class NetworkDetailsActivity extends AppCompatActivity
{
    private final String LOG_TAG = NetworkDetailsActivity.class.getSimpleName();

    private static final int ACCESS_LOCATION_PERMISSION_REQUEST_ID = 1;
    private static final int NETWORK_DATA_REFRESH_RATE_MS = 1000;

    LocationManager locationManager;
    String bestProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(this, new String[]{
                        //Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION},
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

        final Handler handler = new Handler();

        handler.postDelayed(new Runnable()
        {
            public void run()
            {
                refreshCellDetails();

                handler.postDelayed(this, NETWORK_DATA_REFRESH_RATE_MS);
            }
        }, NETWORK_DATA_REFRESH_RATE_MS);
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
                    case Manifest.permission.ACCESS_COARSE_LOCATION:
                        if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                        {
                            refreshCellDetails();
                        } else
                        {
                            Log.w(LOG_TAG, "The ACCESS_COARSE_LOCATION Permission was denied.");
                        }
                        break;

                    case Manifest.permission.ACCESS_FINE_LOCATION:
                        if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                        {
                            initializeLocation();
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
        if (id == R.id.action_settings)
        {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeLocation()
    {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        final Criteria criteria = new Criteria();
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);

        bestProvider = locationManager.getBestProvider(criteria, true);
    }

    private void refreshCellDetails() throws SecurityException
    {
        //Context.CONNECTIVITY_SERVICE;
        //Context.NETWORK_STATS_SERVICE;
        //Context.TELEPHONY_SUBSCRIPTION_SERVICE;

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager == null)
        {
            Log.w(LOG_TAG, "Unable to get access to the Telephony Manager.  No network information will be displayed");
            return;
        }

        final List<CellInfo> allCellInfo = telephonyManager.getAllCellInfo();
        if (allCellInfo.size() > 0)
        {
            for (CellInfo cellInfo : allCellInfo)
            {
                // For now, just look for LTE towers
                if (cellInfo instanceof CellInfoLte)
                {
                    if (cellInfo.isRegistered())
                    {
                        // This record is for the serving cell
                        parseServingCellInfo((CellInfoLte) cellInfo);
                    } else
                    {
                        // This represents a neighbor record
                        parseNeighborCellInfo((CellInfoLte) cellInfo);
                    }
                }
            }
        }
    }

    /**
     * Convert the serving cell {@link CellInfoLte} object into an {@link LteSurveyRecord}, and update the UI with
     * the latest cell details.
     *
     * @param cellInfoLte The LTE serving cell details.
     */
    private void parseServingCellInfo(CellInfoLte cellInfoLte)
    {
        final LteSurveyRecord lteSurveyRecord = generateLteSurveyRecord(cellInfoLte);

        updateUi(lteSurveyRecord);
    }

    /**
     * Convert the neighbor cell {@link CellInfoLte} object into an {@link LteSurveyRecord}.
     *
     * @param cellInfoLte The LTE neighbor cell details.
     */
    private void parseNeighborCellInfo(CellInfoLte cellInfoLte)
    {
        Log.i(LOG_TAG, "LTE Neighbor Cell : " + cellInfoLte.getCellIdentity().toString() +
                "\n LTE Neighbor Signal Values: " + cellInfoLte.getCellSignalStrength().toString());

        generateLteSurveyRecord(cellInfoLte);
    }

    /**
     * Given a {@link CellInfoLte} object, pull out the values and generate an {@link LteSurveyRecord}.
     *
     * @param cellInfoLte The object that contains the LTE Cell info.  This can be a serving cell,
     *                    or a neighbor cell.
     * @return The survey record.
     */
    private LteSurveyRecord generateLteSurveyRecord(CellInfoLte cellInfoLte)
    {
        final CellIdentityLte cellIdentity = cellInfoLte.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int tac = cellIdentity.getTac();
        final int ci = cellIdentity.getCi();
        final int earfcn = cellIdentity.getEarfcn();
        final int pci = cellIdentity.getPci();

        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
        final int rsrp = cellSignalStrengthLte.getRsrp();
        final int rsrq = cellSignalStrengthLte.getRsrq();
        final int timingAdvance = cellSignalStrengthLte.getTimingAdvance();

        final LteSurveyRecordBuilder lteSurveyRecordBuilder = new LteSurveyRecordBuilder();

        if (locationManager != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = locationManager.getLastKnownLocation(bestProvider);
            lteSurveyRecordBuilder.setLocation(lastKnownLocation);
        }

        lteSurveyRecordBuilder.setTime(System.currentTimeMillis());
        lteSurveyRecordBuilder.setMcc(mcc);
        lteSurveyRecordBuilder.setMnc(mnc);
        lteSurveyRecordBuilder.setTac(tac);
        lteSurveyRecordBuilder.setCi(ci);
        lteSurveyRecordBuilder.setEarfcn(earfcn);
        lteSurveyRecordBuilder.setPci(pci);
        lteSurveyRecordBuilder.setRsrp(rsrp);
        lteSurveyRecordBuilder.setRsrq(rsrq);
        lteSurveyRecordBuilder.setTa(timingAdvance);

        return lteSurveyRecordBuilder.createLteSurveyRecord();
    }

    private void updateUi(LteSurveyRecord lteSurveyRecord)
    {
        checkAndSetValue(lteSurveyRecord.getMcc(), (TextView) findViewById(R.id.mccValue));
        checkAndSetValue(lteSurveyRecord.getMnc(), (TextView) findViewById(R.id.mncValue));
        checkAndSetValue(lteSurveyRecord.getTac(), (TextView) findViewById(R.id.tacValue));

        final int ci = lteSurveyRecord.getCi();
        if (ci != Integer.MAX_VALUE)
        {
            checkAndSetValue(ci, (TextView) findViewById(R.id.cidValue));

            // The Cell Identity is 28 bits long. The first 20 bits represent the Macro eNodeB ID. The last 8 bits
            // represent the sector.  Strip off the last 8 bits to get the Macro eNodeB ID.
            int eNodebId = ci >> 8;
            ((TextView) findViewById(R.id.enbIdValue)).setText(String.valueOf(eNodebId));

            int sectorId = ci & 0xFF;
            ((TextView) findViewById(R.id.sectorIdValue)).setText(String.valueOf(sectorId));
        }

        checkAndSetValue(lteSurveyRecord.getEarfcn(), (TextView) findViewById(R.id.earfcnValue));
        checkAndSetValue(lteSurveyRecord.getPci(), (TextView) findViewById(R.id.pciValue));

        checkAndSetLocation(lteSurveyRecord.getLocation());

        checkAndSetValue(lteSurveyRecord.getRsrp(), (TextView) findViewById(R.id.rsrpValue));
        checkAndSetValue(lteSurveyRecord.getRsrq(), (TextView) findViewById(R.id.rsrqValue));
        checkAndSetValue(lteSurveyRecord.getTa(), (TextView) findViewById(R.id.taValue));
    }

    /**
     * Checks to make sure the value is valid, and then sets it to the provided {@link TextView}.
     *
     * @param valueToCheck The value to check.  If the value is equal to {@link Integer#MAX_VALUE},
     *                     it is ignored, otherwise it is set to the text view.
     * @param textView     The text view to set the value on.
     */
    private void checkAndSetValue(int valueToCheck, TextView textView)
    {
        if (valueToCheck != Integer.MAX_VALUE)
        {
            textView.setText(String.valueOf(valueToCheck));
        } else
        {
            textView.setText("");
        }
    }

    /**
     * Checks to make sure the location is not null, and then updates the appropriate UI elements.
     *
     * @param location The location to check and use if it is valid.
     */
    private void checkAndSetLocation(Location location)
    {
        if (location != null)
        {
            ((TextView) findViewById(R.id.latitudeValue)).setText(String.valueOf(location.getLatitude()));
            ((TextView) findViewById(R.id.longitudeValue)).setText(String.valueOf(location.getLongitude()));
        } else
        {
            ((TextView) findViewById(R.id.latitudeValue)).setText("");
            ((TextView) findViewById(R.id.longitudeValue)).setText("");
        }
    }
}
