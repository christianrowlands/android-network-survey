package com.craxiom.networksurvey.util;

import android.content.Context;

import com.google.firebase.analytics.FirebaseAnalytics;

import timber.log.Timber;

/**
 * Utils to help with logging Firebase Analytics events.
 *
 * @since 1.9.0
 */
public class AnalyticsUtils {
    public static final String EVENT_MDM_OVERRIDE_ON = "mdm_override_turned_on";
    public static final String EVENT_MDM_OVERRIDE_OFF = "mdm_override_turned_off";

    private AnalyticsUtils() {
    }

    /**
     * Sends an event to Firebase Analytics.
     *
     * @param context   The App's context.
     * @param eventName The name of the event.
     */
    public static void logEvent(Context context, String eventName) {
        try {
            final FirebaseAnalytics firebaseAnalytics = FirebaseAnalytics.getInstance(context);

            firebaseAnalytics.logEvent(eventName, null);
        } catch (Throwable t) {
            Timber.e(t, "Could not log an event to Firebase Analytics.");
        }
    }
}
