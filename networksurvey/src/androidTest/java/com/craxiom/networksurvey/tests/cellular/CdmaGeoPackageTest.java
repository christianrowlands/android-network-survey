package com.craxiom.networksurvey.tests.cellular;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

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
        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);

        ArrayList<MessageTableSchema> results = SchemaDao.getTableSchema(geoPackage, CdmaMessageConstants.CDMA_RECORDS_TABLE_NAME);

        //Then
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

        assertWithMessage("Validate SID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='SID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate NID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='NID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate BSID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='BSID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Channel column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Channel', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate PN Offset column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='PN Offset', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Signal Strength column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Signal Strength', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Ec/Io column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Ec/Io', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Base Latitude column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Base Latitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Base Longitude column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Base Longitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");
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
