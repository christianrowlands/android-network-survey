package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.craxiom.networksurvey.services.NetworkSurveyService;

import timber.log.Timber;

/**
 * Handles receiving new SMS message events from the Android OS and converting it to a local
 * broadcast message so that the {@link NetworkSurveyService} can add it to the CDR log file if
 * CDR logging is enabled.
 */
public class SimChangeReceiver extends BroadcastReceiver
{
    public static final String SIM_CHANGED_INTENT = "SimChangedIntent";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!"android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) return;

        Timber.d("Received the SIM changed intent action");

        String state = "";
        Bundle extras = intent.getExtras();
        if (extras != null)
        {
            state = extras.getString("ss");
        }
        Timber.i("SIM State Change Detected %s", state);

        Intent simIntent = new Intent(SIM_CHANGED_INTENT);
        LocalBroadcastManager.getInstance(context).sendBroadcast(simIntent);
    }
}
