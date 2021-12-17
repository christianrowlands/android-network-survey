package com.craxiom.networksurvey.tests;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;

import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.dao.GnssDao;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.message.GnssModel;
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
public class GnssGeoPackageTest extends TestBase
{

    @Before
    public void setUpGnssTest()
    {
        TopMenuBar.clickCellLoggingEnableDisable();
        BottomMenuBar.clickGnssMenuOption();
        TopMenuBar.clickGnssLoggingEnableDisable();
        assertWithMessage("GNSS is enabled")
                .that(TopMenuBar.isGnssLoggingEnabled())
                .isTrue();
        //Gather GNSS data
        sleep(30, TimeUnit.SECONDS);
        TopMenuBar.clickGnssLoggingEnableDisable();
        geoPackageManager = GeoPackageFactory.getManager(getContext());
    }

    /*
        MONKEY-T62
     */
    @Test
    public void validateGnssMessageTableSchema()
    {
        //Given
        ArrayList<MessageTableSchema> results;

        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue())
                        .getAbsolutePath(), false);

        results = SchemaDao.getTableSchema(geoPackage, GnssMessageConstants.GNSS_RECORDS_TABLE_NAME);

        //Then
        int index = validateCommonTableSchema(results);

        assertWithMessage("Validate Group Number column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='GroupNumber', type='3', notNull=1, defaultValue=-1, primaryKey=0}");

        assertWithMessage("Validate Constellation column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Constellation', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Space Vehicle column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Space Vehicle Id', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Carrier Frequency HZ column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Carrier Frequency Hz', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Latitude Standard Deviation column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Latitude Standard Deviation (m)', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Longitude Standard Deviation column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Longitude Standard Deviation (m)', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Altitude Standard Deviation column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Altitude Standard Deviation (m)', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate AGC dB column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='AGC dB', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate C/N0 (dB-Hz) column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='C/N0 (dB-Hz)', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T63
     */
    @Test
    public void gnssSurveyDataGeneratedUponTestRun()
    {
        //Given
        Long fileDate = AndroidFiles.getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue()).lastModified();

        //Then
        assertWithMessage("Latest GNSS survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }

    /*
        MONKEY-T64
     */
    @Test
    @RequiresDevice
    public void gnssNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //Then
        assertWithMessage("All Non-Null columns are populated")
                .that(GnssDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }

    /*
        MONKEY-T65
     */
    @Test
    @RequiresDevice
    public void gnssDataValuesAreOfExpectedTypesAndRanges()
    {
        //Given
        ArrayList<GnssModel> results;

        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //When
        results = GnssDao.getRecordsWithAllColumnsPopulated(geoPackage);

        //Then
        assertWithMessage("Results are not empty")
                .that(results)
                .isNotEmpty();

        for (GnssModel row : results)
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

            assertWithMessage("Group number column is within range")
                    .that(row.getGroupNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Constellation value is within range")
                    .that(row.getConstellation())
                    .isIn(Arrays.asList(Constellation.BEIDOU.toString(), Constellation.GPS.toString(), Constellation.GALILEO.toString(), Constellation.GLONASS.toString(), Constellation.IRNSS.toString(), Constellation.QZSS.toString(), Constellation.SBAS.toString(), Constellation.UNKNOWN.toString(), Constellation.UNRECOGNIZED.toString()));

            assertWithMessage("Space Vehicle ID is within range")
                    .that(row.getSpaceVehicleId())
                    .isIn(Range.closed(0, Integer.MAX_VALUE));

            assertWithMessage("Carrier Frequency Signal is within range")
                    .that(row.getCarrierFrequencyHz())
                    .isIn(Range.closed(Integer.MIN_VALUE, Integer.MAX_VALUE));

            assertWithMessage("Latitude Standard Deviation value is within range")
                    .that(row.getLatitudeStandardDeviation())
                    .isAtLeast(0);

            assertWithMessage("Longitude Standard Deviation value is within range")
                    .that(row.getLongitudeStandardDeviation())
                    .isAtLeast(0);

            assertWithMessage("Altitude Standard Deviation value is within range")
                    .that(row.getAltitudeStandardDeviation())
                    .isAtLeast(0);

            assertWithMessage("Agc dB is within range")
                    .that(row.getAgcDb())
                    .isIn(Range.closed(-50f, 50f));

            assertWithMessage("CN0 value is within range")
                    .that(row.getcN0())
                    .isIn(Range.closed(-100f, 100f));
        }
    }
}
