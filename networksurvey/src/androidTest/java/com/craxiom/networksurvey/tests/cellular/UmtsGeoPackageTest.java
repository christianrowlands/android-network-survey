package com.craxiom.networksurvey.tests.cellular;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

import androidx.test.filters.RequiresDevice;

import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.UmtsMessageConstants;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.dao.cellular.UmtsDao;
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

public class UmtsGeoPackageTest extends TestBase
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
        MONKEY-T80
     */
    @Test
    public void validateUmtsMessageTableSchema()
    {
        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);

        ArrayList<MessageTableSchema> results = SchemaDao.getTableSchema(geoPackage, UmtsMessageConstants.UMTS_RECORDS_TABLE_NAME);

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

        assertWithMessage("Validate MCC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='MCC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate MNC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='MNC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate LAC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='LAC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Cell ID column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Cell ID', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate UARFCN column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='UARFCN', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate PSC column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='PSC', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate Signal Strength column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='Signal Strength', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate RSCP column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='RSCP', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    /*
        MONKEY-T81
     */
    @Test
    @RequiresDevice
    public void umtsNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                        .getAbsolutePath(), false);
        //Then
        assertWithMessage("All Non-Null columns are populated")
                .that(UmtsDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }
}
