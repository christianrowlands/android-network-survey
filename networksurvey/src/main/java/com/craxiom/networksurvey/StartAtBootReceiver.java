package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.PreferenceUtils;

import timber.log.Timber;

/**
 * Starts the Network Survey Service when Android is booted if the {@link NetworkSurveyConstants#PROPERTY_MQTT_START_ON_BOOT}
 * property has been set by the MDM managed configuration.  The {@link NetworkSurveyService} will then try to start the
 * connection to an MQTT Broker using the MDM provided connection information.
 *
 * @since 0.1.1
 */
public class StartAtBootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Timber.d("Received the boot completed broadcast message in the Network Survey broadcast receiver");

        if (PreferenceUtils.getMqttStartOnBootPreference(context))
        {
            Timber.i("Auto starting the Network Survey Service based on the user or MDM MQTT auto start preference");

            startNetworkSurveyService(context);
        } else
        {
            // Finally, check to see if we want to auto-start any of the log files.
            if (PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING, false, context))
            {
                Timber.i("Auto starting the Network Survey Service based on one of the auto start logging preferences");

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
