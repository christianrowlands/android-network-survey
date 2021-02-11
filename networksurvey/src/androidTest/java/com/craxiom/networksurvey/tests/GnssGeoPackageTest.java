package com.craxiom.networksurvey.tests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.dao.BaseDao;
import com.craxiom.networksurvey.dao.GnssDao;
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

@RunWith(AndroidJUnit4.class)
public class GnssGeoPackageTest extends TestBase
{

    @Before
    public void setUpGnssTest()
    {
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

        results = BaseDao.getTableSchema(geoPackage, GnssMessageConstants.GNSS_RECORDS_TABLE_NAME);

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

        assertWithMessage("Validate Group Number column schema")
                .that(results.get(4).toString())
                .isEqualTo("MessageTableSchemaModel{cid=4, name='GroupNumber', type='3', notNull=1, defaultValue=-1, primaryKey=0}");

        assertWithMessage("Validate Constellation column schema")
                .that(results.get(5).toString())
                .isEqualTo("MessageTableSchemaModel{cid=5, name='Constellation', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Space Vehicle column schema")
                .that(results.get(6).toString())
                .isEqualTo("MessageTableSchemaModel{cid=6, name='Space Vehicle Id', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Carrier Frequency HZ column schema")
                .that(results.get(7).toString())
                .isEqualTo("MessageTableSchemaModel{cid=7, name='Carrier Frequency Hz', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Latitude Standard Deviation column schema")
                .that(results.get(8).toString())
                .isEqualTo("MessageTableSchemaModel{cid=8, name='Latitude Standard Deviation (m)', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Longitude Standard Deviation column schema")
                .that(results.get(9).toString())
                .isEqualTo("MessageTableSchemaModel{cid=9, name='Longitude Standard Deviation (m)', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Altitude Standard Deviation column schema")
                .that(results.get(10).toString())
                .isEqualTo("MessageTableSchemaModel{cid=10, name='Altitude Standard Deviation (m)', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate AGC dB column schema")
                .that(results.get(11).toString())
                .isEqualTo("MessageTableSchemaModel{cid=11, name='AGC dB', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate C/N0 (dB-Hz) column schema")
                .that(results.get(12).toString())
                .isEqualTo("MessageTableSchemaModel{cid=12, name='C/N0 (dB-Hz)', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T63
     */
    @Test
    public void gnssSurveyDataGeneratedUponTestRun()
    {
        Long fileDate = AndroidFiles.getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue()).lastModified();

        assertWithMessage("Latest GNSS survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }

    /*
        MONKEY-T64
     */
    @Test
    public void gnssNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue())
                        .getAbsolutePath(), false);

        assertWithMessage("All Non-Null columns are populated")
                .that(GnssDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }

    /*
        MONKEY-T65
     */
    @Test
    public void gnssDataValuesAreOfExpectedTypesAndRanges()
    {
        //Given
        ArrayList<GnssModel> results;

        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.GNSS_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //When
        results = GnssDao.getAllGnssRecordsWithAllColumnsPopulated(geoPackage);

        assertWithMessage("We have results to use.")
                .that(results)
                .isNotEmpty();

        for (GnssModel row : results)
        {
            assertThat(row.getId())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getTime())
                    .isLessThan(Integer.MAX_VALUE);
            assertThat(row.getRecordNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getGroupNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));
            assertThat(row.getConstellation())
                    .isIn(Arrays.asList(Constellation.BEIDOU.toString(), Constellation.GPS.toString(), Constellation.GALILEO.toString(), Constellation.GLONASS.toString(), Constellation.IRNSS.toString(), Constellation.QZSS.toString(), Constellation.SBAS.toString(), Constellation.UNKNOWN.toString(), Constellation.UNRECOGNIZED.toString()));
            assertThat(row.getSpaceVehicleId())
                    .isIn(Range.closed(0, Integer.MAX_VALUE));
            assertThat(row.getCarrierFrequencyHz())
                    .isIn(Range.closed(Integer.MIN_VALUE, Integer.MAX_VALUE));
            assertThat(row.getLatitudeStandardDeviation())
                    .isAtLeast(0);
            assertThat(row.getLongitudeStandardDeviation())
                    .isAtLeast(0);
            assertThat(row.getAltitudeStandardDeviation())
                    .isAtLeast(0);
            assertThat(row.getAgcDb())
                    .isIn(Range.closed(-50f, 50f));
            assertThat(row.getcN0())
                    .isIn(Range.closed(-100f, 100f));
        }
    }
}
