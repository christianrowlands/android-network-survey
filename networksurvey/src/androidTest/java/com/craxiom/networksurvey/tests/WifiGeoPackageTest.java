package com.craxiom.networksurvey.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.helpers.dao.GnssDao;
import com.craxiom.networksurvey.helpers.dao.WifiBeaconDao;
import com.craxiom.networksurvey.helpers.geopackage.SurveyTypes;
import com.craxiom.networksurvey.helpers.models.message.WifiBeaconModel;
import com.craxiom.networksurvey.screens.BottomMenuBar;
import com.craxiom.networksurvey.screens.TopMenuBar;
import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.factory.GeoPackageFactory;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

@RunWith(AndroidJUnit4.class)
public class WifiGeoPackageTest extends TestBase
{

    GeoPackage geoPackage;
    GeoPackageManager geoPackageManager;

    @Before
    public void setUpWifiTest()
    {
        BottomMenuBar.clickWifiMenuOption();
        TopMenuBar.clickWifiLoggingEnableDisable();
        assertWithMessage("Wifi is enabled")
                .that(TopMenuBar.isWifiLoggingEnabled())
                .isTrue();
        //Gather wifi data
        sleep(30, TimeUnit.SECONDS);
        TopMenuBar.clickWifiLoggingEnableDisable();
        sleep(10, TimeUnit.SECONDS);
        geoPackageManager = GeoPackageFactory.getManager(getContext());
    }

    @Test
    public void wifiNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                        .getAbsolutePath(), false);


        assertWithMessage("All Non-Null columns are populated")
                .that(WifiBeaconDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }

    @Test
    public void wifiSurveyDataGeneratedUponTestRun()
    {
        Long fileDate = AndroidFiles
                .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                .lastModified();

        assertWithMessage("Latest Wifi survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }

    @Test
    public void wifiDataValuesAreOfExpectedTypesAndRanges() {
        //Given
        ArrayList<WifiBeaconModel> results;

        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                        .getAbsolutePath(), false);

        results = WifiBeaconDao.getAllWifiBeaconRecordsWithAllColumnsPopulated(geoPackage);

        assertWithMessage("Result set is not empty")
                .that(results)
                .isNotEmpty();;

        //Then
        for (WifiBeaconModel row : results) {
            assertThat(row.getId())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getTime())
                    .isLessThan(Integer.MAX_VALUE);
            assertThat(row.getRecordNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getChannel())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getFrequency())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getEncryptionType())
                    .isIn(Arrays.asList(EncryptionType.WPA.toString(), EncryptionType.WPA2.toString(), EncryptionType.WPA3.toString(), EncryptionType.WEP.toString(), EncryptionType.UNKNOWN.toString(), "WPA/WPA2"));
            assertThat(row.getWps())
                    .isAnyOf(Boolean.TRUE, Boolean.FALSE);
            assertThat(row.getSignalStrength())
                    .isIn(Range.closed(-200f, 200f));
        }
    }
}