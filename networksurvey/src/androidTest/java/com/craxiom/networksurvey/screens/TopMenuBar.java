package com.craxiom.networksurvey.screens;

import android.util.Log;

import androidx.test.espresso.NoMatchingViewException;

import com.craxiom.networksurvey.R;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.craxiom.networksurvey.helpers.espressohelpers.ChildAtPosition.childAtPosition;
import static com.schibsted.spain.barista.interaction.BaristaClickInteractions.clickOn;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;
import static org.hamcrest.Matchers.allOf;

public class TopMenuBar {
    private static final String LOG_TAG = TopMenuBar.class.getSimpleName();

    public static void clickKebab() {
        onView(
                allOf(withContentDescription("More options"),
                        childAtPosition(
                                childAtPosition(
                                        withId(R.id.toolbar),
                                        2),
                                2),
                        isDisplayed()))
                .perform(click());
    }

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
        clickKebab();
        sleep(1, TimeUnit.SECONDS);
        try{
            onView(
                    allOf(withId(R.id.title), withText("Start GNSS Logging"),
                            childAtPosition(
                                    childAtPosition(
                                            withId(R.id.content),
                                            0),
                                    0),
                            isDisplayed()))
                    .perform(click());
        } catch (NoMatchingViewException e) {
            onView(
                    allOf(withId(R.id.title), withText("Stop GNSS Logging"),
                            childAtPosition(
                                    childAtPosition(
                                            withId(R.id.content),
                                            0),
                                    0),
                            isDisplayed()))
                    .perform(click());
        }

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

    public static void clickBluetoothLoggingEnableDisable() {
        clickKebab();
        sleep(1, TimeUnit.SECONDS);
        try{
            onView(
                    allOf(withId(R.id.title), withText("Start Bluetooth Logging"),
                            childAtPosition(
                                    childAtPosition(
                                            withId(R.id.content),
                                            0),
                                    0),
                            isDisplayed()))
                    .perform(click());
        } catch (NoMatchingViewException e) {
            onView(
                    allOf(withId(R.id.title), withText("Stop Bluetooth Logging"),
                            childAtPosition(
                                    childAtPosition(
                                            withId(R.id.content),
                                            0),
                                    0),
                            isDisplayed()))
                    .perform(click());
        }

        sleep(3, TimeUnit.SECONDS);
    }

    public static boolean isBlueToothLoggingEnabled() {
        try {
            onView(
                    allOf(withId(R.id.title), withText("Stop Bluetooth Logging"),
                            childAtPosition(
                                    childAtPosition(
                                            withId(R.id.content),
                                            0),
                                    0),
                            isDisplayed()));
            Log.i(LOG_TAG, "GNSS logging is enabled.");
            return true;
        } catch (NoMatchingViewException e) {
            Log.i(LOG_TAG, "GNSS logging is disabled.");
            return false;
        }
    }

    public static boolean isCellularLoggingEnabled() {
        try {
            onView(
                    allOf(withId(R.id.action_start_stop_cellular_logging),
                            withContentDescription("Stop Cellular Logging"),
                            isDisplayed()));
            Log.i(LOG_TAG, "Cellular logging is enabled.");
            return true;
        } catch (NoMatchingViewException e) {
            Log.i(LOG_TAG, "Cellular logging is disabled.");
            return false;
        }
    }
}
