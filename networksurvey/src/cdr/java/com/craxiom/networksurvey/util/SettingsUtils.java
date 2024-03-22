package com.craxiom.networksurvey.util;

import android.content.Context;

import androidx.preference.Preference;

/**
 * A utility class for working with the Network Survey settings.
 */
public class SettingsUtils
{
    public static void setAppInstanceId(Context context, Preference appInstanceIdPreference)
    {
        if (appInstanceIdPreference == null) return;

        appInstanceIdPreference.setVisible(false);
    }
}
