package com.craxiom.networksurvey.tests;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.dao.BluetoothDao;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.helpers.TestUtils;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.message.BluetoothModel;
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
public class BluetoothGeoPackageTest extends TestBase
{

    @Before
    public void setUpBluetoothTest()
    {
        TopMenuBar.clickCellLoggingEnableDisable();
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
        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue())
                        .getAbsolutePath(), false);

        ArrayList<MessageTableSchema> results = SchemaDao.getTableSchema(geoPackage, BluetoothMessageConstants.BLUETOOTH_RECORDS_TABLE_NAME);

        //Then
        int index = validateCommonTableSchema(results);

        assertWithMessage("Validate Source Address column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Source Address', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate OTA Device Name column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='OTA Device Name', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Technology column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Technology', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Supported Technologies column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Supported Technologies', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Tx Power column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Tx Power', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Signal Strength column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Signal Strength', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T59
     */
    @Test
    public void bluetoothSurveyDataGeneratedUponTestRun()
    {
        //Given
        Long fileDate = AndroidFiles.getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue()).lastModified();

        //Then
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
        //Then
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
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.BLUETOOTH_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //When
        ArrayList<BluetoothModel> results = BluetoothDao.getRecordsWithAllColumnsPopulated(geoPackage);

        //Then
        assertWithMessage("We have results to use.")
                .that(results)
                .isNotEmpty();

        for (BluetoothModel row : results)
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

            assertWithMessage("Source Address column is within range")
                    .that(row.getSourceAddress())
                    .containsMatch(TestUtils.Regex.getMacAddressPattern());

            assertWithMessage("OTA Device Name column is not empty")
                    .that(row.getOtaDeviceName())
                    .isNotEmpty();

            assertWithMessage("Supported Technologies column is within range")
                    .that(row.getSupportedTechnologies())
                    .isIn(Arrays.asList(BluetoothMessageConstants.getSupportedTechString(SupportedTechnologies.BR_EDR),
                            BluetoothMessageConstants.getSupportedTechString(SupportedTechnologies.LE),
                            BluetoothMessageConstants.getSupportedTechString(SupportedTechnologies.DUAL),
                            BluetoothMessageConstants.getSupportedTechString(SupportedTechnologies.UNKNOWN)));

            assertWithMessage("Signal Strength column value is within range")
                    .that(row.getSignalStrength())
                    .isIn(Range.closed(-200f, 200f));
        }
    }
}
