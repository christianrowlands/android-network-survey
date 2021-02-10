package com.craxiom.networksurvey;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.craxiom.networksurvey.screens.TopMenuBar;

import org.junit.Before;
import org.junit.Rule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;

import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

public class TestBase
{
    private static final String LOG_TAG = TestBase.class.getSimpleName();
    public static LocalDate testRunStartTime = LocalDate.now();
    public static String testRunDate;
    public static GeoPackage geoPackage;
    public static GeoPackageManager geoPackageManager;

    @Rule
    public ActivityScenarioRule<NetworkSurveyActivity> activityScenarioRule = new ActivityScenarioRule<>(NetworkSurveyActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule =
            GrantPermissionRule.grant(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_PHONE_STATE");

    @Before
    public void setUp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        testRunDate = formatter.format(testRunStartTime);
        int SLEEP_TIME = 5;
        /*
            The app crashes and logs ASYNC errors when you try to interact with the application
            without a pause.
            TODO: Find a better way to accomplish this
         */

        Log.i(LOG_TAG, "Sleeping for " + SLEEP_TIME + " seconds to allow app to start.");
        sleep(SLEEP_TIME, TimeUnit.SECONDS);
        TopMenuBar.clickCellLoggingEnableDisable();
    }

    public Context getContext()
    {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
