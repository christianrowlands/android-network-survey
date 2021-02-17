package com.craxiom.networksurvey;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.craxiom.networksurvey.helpers.TestUtils;

import org.junit.Before;
import org.junit.Rule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;

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

    public static Context getContext()
    {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Before
    public void setUp()
    {
        if (TestUtils.System.areSystemAnimationsEnabled())
        {
            Log.w(LOG_TAG, "For best test stability, please disable animations. " +
                    "https://developer.android.com/training/testing/espresso/setup#set-up-environment");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        testRunDate = formatter.format(testRunStartTime);
    }
}
