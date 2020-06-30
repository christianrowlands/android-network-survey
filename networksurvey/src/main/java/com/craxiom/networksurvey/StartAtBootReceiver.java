package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;

/**
 * Starts the Network Survey Service when Android is booted if the {@link NetworkSurveyConstants#PROPERTY_MQTT_START_ON_BOOT}
 * property has been set by the MDM managed configuration.  The {@link NetworkSurveyService} will then try to start the
 * connection to an MQTT Broker using the MDM provided connection information.
 *
 * @since 0.1.1
 */
public class StartAtBootReceiver extends BroadcastReceiver
{
    private static final String LOG_TAG = StartAtBootReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Log.d(LOG_TAG, "Received the boot completed broadcast message in the Network Survey broadcast receiver");

        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null) return;

        final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
        if (mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, false))
        {
            Log.i(LOG_TAG, "Auto starting the Network Survey Service based on the MDM MQTT auto start preference");

            startNetworkSurveyService(context);
        } else
        {
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

            if (preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT, false))
            {
                Log.i(LOG_TAG, "Auto starting the Network Survey Service based on the settings MQTT auto start preference");

                startNetworkSurveyService(context);
            }
        }
    }

    /**
     * Kick off the {@link NetworkSurveyService} using an intent. The {@link NetworkSurveyConstants#EXTRA_STARTED_AT_BOOT}
     * flag is used so that the {@link NetworkSurveyService} can handle being started at boot instead of when the app is
     * opened by the user.
     *
     * @since 0.1.3
     */
    private void startNetworkSurveyService(Context context)
    {
        final Context applicationContext = context.getApplicationContext();
        final Intent startServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        startServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, true);
        context.startForegroundService(startServiceIntent);
    }
}
