package com.craxiom.networksurvey.util;

import android.content.Context;
import android.widget.Toast;

import androidx.preference.Preference;

import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A utility class for working with the Network Survey settings.
 */
public class SettingsUtils
{
    public static void setAppInstanceId(Context context, Preference appInstanceIdPreference)
    {
        if (context == null) return;
        if (appInstanceIdPreference == null) return;

        Task<@Nullable String> appInstanceId = FirebaseAnalytics.getInstance(context).getAppInstanceId();
        appInstanceId.addOnSuccessListener(id -> {
            if (id != null)
            {
                appInstanceIdPreference.setSummary(id);

                appInstanceIdPreference.setOnPreferenceClickListener(preference -> {
                    // Copy the id to the clipboard
                    CharSequence ids = preference.getSummary();
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("App Instance ID", ids);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(context, "App Instance ID copied to clipboard", Toast.LENGTH_SHORT).show();

                    return true;
                });
            }
        });
    }
}
