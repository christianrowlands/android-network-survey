package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import timber.log.Timber;

public class SurveyControlReceiver extends BroadcastReceiver
{
    public static final String ACTION_START_SURVEY = "com.craxiom.networksurvey.START_SURVEY";
    public static final String ACTION_STOP_SURVEY = "com.craxiom.networksurvey.STOP_SURVEY";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (intent != null && intent.getAction() != null)
        {
            boolean allowIntentControl = PreferenceUtils.getAllowIntentControlPreference(context);
            if (!allowIntentControl)
            {
                Timber.w("Received a survey control intent, but the user has disabled intent control");
                return;
            }

            switch (intent.getAction())
            {
                case ACTION_START_SURVEY:
                    startSurvey(context, intent);
                    break;
                case ACTION_STOP_SURVEY:
                    stopSurvey(context, intent);
                    break;
            }
        }
    }

    /**
     * Start the Network Survey Service with the appropriate logging flags and MQTT configuration.
     */
    private void startSurvey(Context context, Intent incomingIntent)
    {
        Intent serviceIntent = new Intent(context, NetworkSurveyService.class);

        boolean startFileLogging = checkForAndHandleFileLogging(incomingIntent, serviceIntent);

        boolean startMqtt = checkForAndHandleMqttConfig(context, incomingIntent, serviceIntent);

        if (startFileLogging || startMqtt)
        {
            serviceIntent.putExtra(NetworkSurveyConstants.EXTRA_STARTED_VIA_EXTERNAL_INTENT, true);
            context.startForegroundService(serviceIntent);
        }
    }

    private void stopSurvey(Context context, Intent intent)
    {
        Intent serviceIntent = new Intent(context, NetworkSurveyService.class);
        context.stopService(serviceIntent);
    }

    /**
     * Check to see if the intent contains any file logging flags, and if so, start the appropriate
     * file logging.
     */
    private boolean checkForAndHandleFileLogging(Intent incomingIntent, Intent networkSurveyServiceIntent)
    {
        boolean startCellular = incomingIntent.getBooleanExtra(NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING, false);
        boolean startWifi = incomingIntent.getBooleanExtra(NetworkSurveyConstants.EXTRA_WIFI_FILE_LOGGING, false);
        boolean startBluetooth = incomingIntent.getBooleanExtra(NetworkSurveyConstants.EXTRA_BLUETOOTH_FILE_LOGGING, false);
        boolean startGnss = incomingIntent.getBooleanExtra(NetworkSurveyConstants.EXTRA_GNSS_FILE_LOGGING, false);
        boolean startCdr = incomingIntent.getBooleanExtra(NetworkSurveyConstants.EXTRA_CDR_FILE_LOGGING, false);

        Timber.i("Starting the Network Survey Service with the file logging flags: cellular=%b, wifi=%b, bluetooth=%b, gnss=%b, cdr=%b",
                startCellular, startWifi, startBluetooth, startGnss, startCdr);

        if (startCellular)
        {
            networkSurveyServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_CELLULAR_FILE_LOGGING, true);
        }
        if (startWifi)
        {
            networkSurveyServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_WIFI_FILE_LOGGING, true);
        }
        if (startBluetooth)
        {
            networkSurveyServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_BLUETOOTH_FILE_LOGGING, true);
        }
        if (startGnss)
        {
            networkSurveyServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_GNSS_FILE_LOGGING, true);
        }
        if (startCdr)
        {
            networkSurveyServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_CDR_FILE_LOGGING, true);
        }

        return startCellular || startWifi || startBluetooth || startGnss || startCdr;
    }

    /**
     * Check to see if the intent contains an MQTT configuration JSON string, and if so, start the
     * MQTT connection with that configuration.
     */
    private boolean checkForAndHandleMqttConfig(Context context, Intent incomingIntent, Intent networkSurveyServiceIntent)
    {
        try
        {
            String mqttConfigJsonString = incomingIntent.getStringExtra("mqtt_config_json");
            if (mqttConfigJsonString != null)
            {
                Timber.i("Starting the MQTT connection with the intent provided configuration");

                // Deserialize the JSON string into an MqttConnectionSettings object just as a validation step
                new Gson().fromJson(mqttConfigJsonString, MqttConnectionSettings.class);

                networkSurveyServiceIntent.putExtra("mqtt_config_json", mqttConfigJsonString);

                return true;
            }
        } catch (JsonSyntaxException e)
        {
            Toast.makeText(context, "Invalid MQTT Config JSON string provided with the intent", Toast.LENGTH_SHORT).show();
            Timber.e(e, "Could not read the intent provided MQTT config JSON string");
        } catch (Exception e)
        {
            Toast.makeText(context, "Could not start the MQTT connection", Toast.LENGTH_SHORT).show();
            Timber.e(e, "Failed to start the MQTT connection with the intent provided configuration");
        }

        return false;
    }
}

