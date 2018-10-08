package com.craxiom.networksurvey;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

    private static final int ACCESS_COARSE_LOCATION_PERMISSION_REQUEST_ID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_COARSE_LOCATION_PERMISSION_REQUEST_ID);

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

        if (requestCode == ACCESS_COARSE_LOCATION_PERMISSION_REQUEST_ID &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            refreshCellDetails();
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

    private void refreshCellDetails() throws SecurityException
    {
        //Context.CONNECTIVITY_SERVICE;
        //Context.NETWORK_STATS_SERVICE;
        //Context.TELEPHONY_SUBSCRIPTION_SERVICE;

        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);

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
                // For now, just look for the serving cell
                if (cellInfo.isRegistered())
                {
                    if (cellInfo instanceof CellInfoLte)
                    {
                        CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                        final CellIdentityLte cellIdentity = ((CellInfoLte) cellInfo).getCellIdentity();

                        checkAndSetValue(cellIdentity.getMcc(), (TextView) findViewById(R.id.mccValue));
                        checkAndSetValue(cellIdentity.getMnc(), (TextView) findViewById(R.id.mncValue));
                        checkAndSetValue(cellIdentity.getTac(), (TextView) findViewById(R.id.tacValue));
                        checkAndSetValue(cellIdentity.getCi(), (TextView) findViewById(R.id.cidValue));

                        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
                        cellSignalStrengthLte.getDbm();
                    }

                }
            }
        }
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
        }
    }
}
