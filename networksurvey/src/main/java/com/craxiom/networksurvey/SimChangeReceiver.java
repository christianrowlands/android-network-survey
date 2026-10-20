package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import timber.log.Timber;

/**
 * Receives the system's SIM state change broadcast (a SIM inserted or removed, or an eSIM turned on
 * or off) and passes it on through {@link SimChanges}, the app's one in-process SIM change signal.
 * <p>
 * The manifest receiver still fires on Android 16 (checked in October 2026), once per
 * state: NOT_READY, then READY and LOADED when a SIM comes back.
 */
public class SimChangeReceiver extends BroadcastReceiver
{
    private static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
    private static final String EXTRA_SIM_STATE = "ss";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) return;

        String state = "";
        Bundle extras = intent.getExtras();
        if (extras != null)
        {
            state = extras.getString(EXTRA_SIM_STATE);
        }
        Timber.i("SIM State Change Detected %s", state);

        SimChanges.notifyChanged();
    }
}
