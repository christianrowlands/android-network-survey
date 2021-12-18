package com.craxiom.networksurvey.tests;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;

import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.dao.WifiBeaconDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.message.WifiBeaconModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import com.craxiom.networksurvey.screens.BottomMenuBar;
import com.craxiom.networksurvey.screens.TopMenuBar;
import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import mil.nga.geopackage.factory.GeoPackageFactory;

@RunWith(AndroidJUnit4.class)
public class WifiGeoPackageTest extends TestBase
{

    @Before
    public void setUpWifiTest()
    {
        TopMenuBar.clickCellLoggingEnableDisable();
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

    /*
        MONKEY-T66
     */
    @Test
    @RequiresDevice
    public void wifiNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                        .getAbsolutePath(), false);
        //Then
        assertWithMessage("All Non-Null columns are populated")
                .that(WifiBeaconDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }

    /*
        MONKEY-T67
     */
    @Test
    public void wifiSurveyDataGeneratedUponTestRun()
    {
        //Given
        Long fileDate = AndroidFiles
                .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                .lastModified();
        //Then
        assertWithMessage("Latest Wifi survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }

    /*
        MONKEY-T68
     */
    @Test
    @RequiresDevice
    public void wifiDataValuesAreOfExpectedTypesAndRanges()
    {
        /*
           Note that I am not able to get the Ciper Suites and AKM suites columns to be populated
         */
        //Given
        ArrayList<WifiBeaconModel> results;

        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                        .getAbsolutePath(), false);

        results = WifiBeaconDao.getRecordsWithAllColumnsPopulated(geoPackage);

        assertWithMessage("Result set is not empty")
                .that(results)
                .isNotEmpty();

        //Then
        for (WifiBeaconModel row : results)
        {
            assertWithMessage("ID column is within range")
                    .that(row.getId())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Time column is within range")
                    .that(row.getTime())
                    .isIn(Range.closed(Long.MIN_VALUE, Long.MAX_VALUE));

            assertWithMessage("Record number column is within range")
                    .that(row.getRecordNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Channel value column is within range")
                    .that(row.getChannel())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Frequency value is within range")
                    .that(row.getFrequency())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Encryption Type value is within range")
                    .that(row.getEncryptionType())
                    .isIn(Arrays.asList(WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA),
                            WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA2),
                            WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA3),
                            WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WEP),
                            WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.UNKNOWN),
                            WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.WPA_WPA2),
                            WifiBeaconMessageConstants.getEncryptionTypeString(EncryptionType.OPEN)));

            assertWithMessage("WPS value is 1 or 0")
                    .that(row.getWps())
                    .isAnyOf(Boolean.TRUE, Boolean.FALSE);

            assertWithMessage("Signal Strength value is within range")
                    .that(row.getSignalStrength())
                    .isIn(Range.closed(-200f, 200f));
        }
    }

    /*
        MONKEY-T69
     */
    @Test
    public void validateWifiMessageTableSchema()
    {
        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.WIFI_SURVEY.getValue())
                        .getAbsolutePath(), false);

        ArrayList<MessageTableSchema> results = SchemaDao.getTableSchema(geoPackage, WifiBeaconMessageConstants.WIFI_BEACON_RECORDS_TABLE_NAME);

        //Then
        int index = validateCommonTableSchema(results);

        assertWithMessage("Validate BSSID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='BSSID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate SSID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='SSID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Channel column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Channel', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Frequency column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Frequency', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Cipher Suites column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Cipher_Suites', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate AKM Suites column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='AKM_Suites', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Encryption Type column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Encryption_Type', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate WPS column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='WPS', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Signal Strength column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Signal Strength', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }
}
