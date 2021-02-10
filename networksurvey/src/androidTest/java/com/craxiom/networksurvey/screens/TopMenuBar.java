package com.craxiom.networksurvey.screens;

import android.util.Log;

import androidx.test.espresso.NoMatchingViewException;

import com.craxiom.networksurvey.R;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static com.schibsted.spain.barista.interaction.BaristaClickInteractions.clickOn;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;
import static org.hamcrest.Matchers.allOf;

public class TopMenuBar {
    private static final String LOG_TAG = TopMenuBar.class.getSimpleName();


    public static void clickWifiLoggingEnableDisable() {
        clickOn(R.id.action_start_stop_wifi_logging);
        sleep(3, TimeUnit.SECONDS);
    }

    public static boolean isWifiLoggingEnabled() {
        try {
            onView(
                    allOf(withId(R.id.action_start_stop_wifi_logging),
                            withContentDescription("Stop Wi-Fi Logging"),
                            isDisplayed()));
            Log.i(LOG_TAG, "Wi-Fi logging is enabled.");
            return true;
        } catch (NoMatchingViewException e) {
            Log.i(LOG_TAG, "Wi-Fi logging is disabled.");
            return false;
        }
    }

    public static void clickCellLoggingEnableDisable() {
        clickOn(R.id.action_start_stop_cellular_logging);
        sleep(3, TimeUnit.SECONDS);
    }

    public static void clickGnssLoggingEnableDisable() {
        clickOn(R.id.action_start_stop_gnss_logging);
        sleep(3, TimeUnit.SECONDS);
    }

    public static boolean isGnssLoggingEnabled() {
        try {
            onView(
                    allOf(withId(R.id.action_start_stop_gnss_logging),
                            withContentDescription("Stop GNSS Logging"),
                            isDisplayed()));
            Log.i(LOG_TAG, "GNSS logging is enabled.");
            return true;
        } catch (NoMatchingViewException e) {
            Log.i(LOG_TAG, "GNSS logging is disabled.");
            return false;
        }
    }

}
