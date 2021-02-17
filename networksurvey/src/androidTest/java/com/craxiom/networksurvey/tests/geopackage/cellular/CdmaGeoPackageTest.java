package com.craxiom.networksurvey.tests.geopackage.cellular;

import androidx.test.filters.RequiresDevice;

import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.CdmaMessageConstants;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.dao.cellular.CdmaDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import com.craxiom.networksurvey.screens.BottomMenuBar;
import com.craxiom.networksurvey.screens.TopMenuBar;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import mil.nga.geopackage.factory.GeoPackageFactory;

import static com.google.common.truth.Truth.*;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.*;

public class CdmaGeoPackageTest extends TestBase
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
        MONKEY-T77
     */
    @Test
    public void validateCdmaMessageTableSchema()
    {
        //Given
        ArrayList<MessageTableSchema> results;

        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);

        results = SchemaDao.getTableSchema(geoPackage, CdmaMessageConstants.CDMA_RECORDS_TABLE_NAME);

        //Then
        assertWithMessage("Validate the results are not empty.")
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

        assertWithMessage("Validate Serving Cell column schema")
                .that(results.get(5).toString())
                .isEqualTo("MessageTableSchemaModel{cid=5, name='Serving Cell', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Provider column schema")
                .that(results.get(6).toString())
                .isEqualTo("MessageTableSchemaModel{cid=6, name='Provider', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate SID column schema")
                .that(results.get(7).toString())
                .isEqualTo("MessageTableSchemaModel{cid=7, name='SID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate NID column schema")
                .that(results.get(8).toString())
                .isEqualTo("MessageTableSchemaModel{cid=8, name='NID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate BSID column schema")
                .that(results.get(9).toString())
                .isEqualTo("MessageTableSchemaModel{cid=9, name='BSID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Channel column schema")
                .that(results.get(10).toString())
                .isEqualTo("MessageTableSchemaModel{cid=10, name='Channel', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate PN Offset column schema")
                .that(results.get(11).toString())
                .isEqualTo("MessageTableSchemaModel{cid=11, name='PN Offset', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Signal Strength column schema")
                .that(results.get(12).toString())
                .isEqualTo("MessageTableSchemaModel{cid=12, name='Signal Strength', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Ec/Io column schema")
                .that(results.get(13).toString())
                .isEqualTo("MessageTableSchemaModel{cid=13, name='Ec/Io', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Base Latitude column schema")
                .that(results.get(14).toString())
                .isEqualTo("MessageTableSchemaModel{cid=14, name='Base Latitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Base Longitude column schema")
                .that(results.get(15).toString())
                .isEqualTo("MessageTableSchemaModel{cid=15, name='Base Longitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T78
     */
    @Test
    @RequiresDevice
    public void cdmaNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);
        //Then
        assertWithMessage("All Non-Null columns are populated")
                .that(CdmaDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }
}
