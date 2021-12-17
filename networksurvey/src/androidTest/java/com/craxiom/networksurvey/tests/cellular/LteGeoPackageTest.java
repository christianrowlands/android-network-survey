package com.craxiom.networksurvey.tests.cellular;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

import androidx.test.filters.RequiresDevice;

import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.dao.cellular.LteDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.message.cellular.LteModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import com.craxiom.networksurvey.screens.BottomMenuBar;
import com.craxiom.networksurvey.screens.TopMenuBar;
import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import mil.nga.geopackage.factory.GeoPackageFactory;

public class LteGeoPackageTest extends TestBase
{
    @Before
    public void setUpCellularTest()
    {
        BottomMenuBar.clickCellularMenuOption();
        assertWithMessage("Cellular logging is enabled")
                .that(TopMenuBar.isCellularLoggingEnabled())
                .isTrue();
        //Gather Cellular data
        sleep(10, TimeUnit.SECONDS);
        TopMenuBar.clickCellLoggingEnableDisable();
        geoPackageManager = GeoPackageFactory.getManager(getContext());
    }

    /*
        MONKEY-T71
     */
    @Test
    public void validateLteMessageTableSchema()
    {
        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);

        ArrayList<MessageTableSchema> results = SchemaDao.getTableSchema(geoPackage, LteMessageConstants.LTE_RECORDS_TABLE_NAME);

        int index = validateCommonTableSchema(results);

        assertWithMessage("Validate Group Number column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='GroupNumber', type='3', notNull=1, defaultValue=-1, primaryKey=0}");

        assertWithMessage("Validate Serving Cell column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Serving Cell', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Provider column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Provider', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate MCC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='MCC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate MNC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='MNC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate TAC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='TAC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate ECI column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='ECI', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate DL_EARFCN column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='DL_EARFCN', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Phys_Cell_ID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Phys_Cell_ID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate RSRP column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='RSRP', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate RSRQ column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='RSRQ', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate RSRQ column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='TA', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate RSRQ column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='DL_Bandwidth', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T73
     */
    @Test
    @RequiresDevice
    public void lteDataValuesAreOfExpectedTypesAndRanges()
    {
        /*
            We do not consistently get TA and DL_Bandwidth values
         */

        //Given
        ArrayList<LteModel> results;

        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //When
        results = LteDao.getRecordsWithAllColumnsPopulated(geoPackage);

        //Then
        assertWithMessage("We have results to use.")
                .that(results)
                .isNotEmpty();

        for (LteModel row : results)
        {
            assertWithMessage("ID column in within range")
                    .that(row.getId())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Time column is within range")
                    .that(row.getTime())
                    .isIn(Range.closed(Long.MIN_VALUE, Long.MAX_VALUE));

            assertWithMessage("Record Number column is within range")
                    .that(row.getRecordNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Group Number column is within range")
                    .that(row.getGroupNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Serving Cell column is within range")
                    .that(row.getServingCell())
                    .isIn(Range.closed(0, 1));

            assertWithMessage("MCC column is within range")
                    .that(row.getMcc())
                    .isIn(Range.closed(0, Integer.MAX_VALUE));

            assertWithMessage("MNC column is within range")
                    .that(row.getMnc())
                    .isIn(Range.closed(0, Integer.MAX_VALUE));

            assertWithMessage("TAC column is within range")
                    .that(row.getTac())
                    .isIn(Range.closed(0, 65535));

            assertWithMessage("ECI column is within range")
                    .that(row.getEci())
                    .isIn(Range.closed(0, 268435455));

            assertWithMessage("DL_EARFCN column is within range")
                    .that(row.getDlEarfcn())
                    .isIn(Range.closed(0, 262143));

            assertWithMessage("Phys_Cell_Id column is within range")
                    .that(row.getPhysCellId())
                    .isIn(Range.closed(0, 503));

            assertWithMessage("RSRP column is within range")
                    .that(row.getRsrp())
                    .isIn(Range.closed(-140f, 140f));

            assertWithMessage("RSRQ column is within range")
                    .that(row.getRsrq())
                    .isIn(Range.closed(-19.5f, -3f));

            assertWithMessage("DL Bandwidth column is within range")
                    .that(row.getDlBandwidth())
                    .isIn(Arrays.asList("1.4", "3", "5", "10", "15", "20"));
        }
    }

    /*
        MONKEY-T72
     */
    @Test
    @RequiresDevice
    public void lteNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);
        //Then
        assertWithMessage("All Non-Null columns are populated")
                .that(LteDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }
}
