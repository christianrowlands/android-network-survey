package com.craxiom.networksurvey.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.dao.BluetoothDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.helpers.TestUtils;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.message.BluetoothModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import com.craxiom.networksurvey.screens.BottomMenuBar;
import com.craxiom.networksurvey.screens.TopMenuBar;
import com.google.common.collect.Range;
import mil.nga.geopackage.factory.GeoPackageFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.*;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.*;

@RunWith(AndroidJUnit4.class)
public class BluetoothGeoPackageTest extends TestBase
{

    @Before
    public void setUpBluetoothTest()
    {
        BottomMenuBar.clickBluetoothMenuOption();
        TopMenuBar.clickBluetoothLoggingEnableDisable();
        assertWithMessage("Bluetooth logging is enabled")
                .that(TopMenuBar.isBlueToothLoggingEnabled())
                .isTrue();
        //Gather Bluetooth data
        sleep(30, TimeUnit.SECONDS);
        TopMenuBar.clickBluetoothLoggingEnableDisable();
        geoPackageManager = GeoPackageFactory.getManager(getContext());
    }

    /*
        MONKEY-T58
     */
    @Test
    public void validateBluetoothMessageTableSchema()
    {
        //Given
        ArrayList<MessageTableSchema> results;

        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue())
                        .getAbsolutePath(), false);

        results = BluetoothDao.getBluetoothTableSchema(geoPackage);

        assertWithMessage("Results are not empty.")
                .that(results)
                .isNotEmpty();

        assertWithMessage("Validate ID column schema")
                .that(results.get(0).toString())
                .isEqualTo("MessageTableSchemaModel{cid=0, name='id', type='3', notNull=1, defaultValue=0, primaryKey=1}");

        assertWithMessage("Validate GEOM column schema")
                .that(results.get(1).toString())
                .isEqualTo("MessageTableSchemaModel{cid=1, name='geom', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Time column schema")
                .that(results.get(2).toString())
                .isEqualTo("MessageTableSchemaModel{cid=2, name='Time', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Record Number column schema")
                .that(results.get(3).toString())
                .isEqualTo("MessageTableSchemaModel{cid=3, name='RecordNumber', type='3', notNull=1, defaultValue=-1, primaryKey=0}");

        assertWithMessage("Validate Source Address column schema")
                .that(results.get(4).toString())
                .isEqualTo("MessageTableSchemaModel{cid=4, name='Source Address', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate OTA Device Name column schema")
                .that(results.get(5).toString())
                .isEqualTo("MessageTableSchemaModel{cid=5, name='OTA Device Name', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Technology column schema")
                .that(results.get(6).toString())
                .isEqualTo("MessageTableSchemaModel{cid=6, name='Technology', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Supported Technologies column schema")
                .that(results.get(7).toString())
                .isEqualTo("MessageTableSchemaModel{cid=7, name='Supported Technologies', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Tx Power column schema")
                .that(results.get(8).toString())
                .isEqualTo("MessageTableSchemaModel{cid=8, name='Tx Power', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Signal Strength column schema")
                .that(results.get(9).toString())
                .isEqualTo("MessageTableSchemaModel{cid=9, name='Signal Strength', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T59
     */
    @Test
    public void bluetoothSurveyDataGeneratedUponTestRun()
    {
        Long fileDate = AndroidFiles.getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue()).lastModified();

        assertWithMessage("Latest Bluetooth survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }

    /*
        MONKEY-T60
     */
    @Test
    public void blueToothNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue())
                        .getAbsolutePath(), false);

        assertWithMessage("All Non-Null columns are populated")
                .that(BluetoothDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }

    /*
        MONKEY-T61
    */
    @Test
    public void bluetoothDataValuesAreOfExpectedTypesAndRanges()
    {
        /*
            Note that I am not able to get the Technology or Tx Power columns to be populated
         */

        //Given
        ArrayList<BluetoothModel> results;

        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //When
        results = BluetoothDao.getAllBluetoothRecordsWithAllColumnsPopulated(geoPackage);

        assertWithMessage("We have results to use.")
                .that(results)
                .isNotEmpty();

        for (BluetoothModel row : results)
        {
            assertThat(row.getId())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getTime())
                    .isLessThan(Integer.MAX_VALUE);
            assertThat(row.getRecordNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getSourceAddress())
                    .containsMatch(TestUtils.Regex.getMacAddressPattern());
            assertThat(row.getOtaDeviceName())
                    .isNotEmpty();
            assertThat(row.getSupportedTechnologies())
                    .isIn(Arrays.asList(SupportedTechnologies.BR_EDR.toString(), SupportedTechnologies.LE.toString(), SupportedTechnologies.DUAL.toString(), SupportedTechnologies.UNKNOWN.toString()));
            assertThat(row.getSignalStrength())
                    .isIn(Range.closed(-200f, 200f));
        }
    }
}
