package com.craxiom.networksurvey.tests;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.sleep;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;

import com.craxiom.messaging.phonestate.SimState;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.dao.PhoneStateDao;
import com.craxiom.networksurvey.dao.SchemaDao;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.models.message.PhoneStateModel;
import com.craxiom.networksurvey.models.tableschemas.MessageTableSchema;
import com.craxiom.networksurvey.screens.TopMenuBar;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import mil.nga.geopackage.factory.GeoPackageFactory;

@RunWith(AndroidJUnit4.class)
public class PhoneStateGeoPackageTest extends TestBase
{
    @Before
    public void setUpPhoneStateTest()
    {
        // Phone state logging is tied to cellular logging which is enabled by default
        assertWithMessage("Cell and Phone State logging is enabled")
                .that(TopMenuBar.isCellularLoggingEnabled())
                .isTrue();
        //Gather Phone state data
        sleep(30, TimeUnit.SECONDS);
        TopMenuBar.clickCellLoggingEnableDisable();
        geoPackageManager = GeoPackageFactory.getManager(getContext());
    }

    @Test
    public void validatePhoneStateMessageTableSchema()
    {
        //When
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.PHONE_STATE_SURVEY.getValue())
                        .getAbsolutePath(), false);

        ArrayList<MessageTableSchema> results = SchemaDao.getTableSchema(geoPackage, DeviceStatusMessageConstants.PHONE_STATE_TABLE_NAME);

        //Then
        int index = validateCommonTableSchema(results);

        assertWithMessage("Validate latitude column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='latitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate longitude column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='longitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate altitude column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='altitude', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate simState column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='simState', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate simOperator column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='simOperator', type='3', notNull=0, defaultValue=0, primaryKey=0}");

        assertWithMessage("Validate networkRegistrationInfo column schema")
                .that(results.get(index).toString())
                .isEqualTo("MessageTableSchemaModel{cid=" + index++ + ", name='networkRegistrationInfo', type='3', notNull=0, defaultValue=0, primaryKey=0}");
    }

    @Test
    public void phoneStateSurveyDataGeneratedUponTestRun()
    {
        //Given
        Long fileDate = AndroidFiles.getLatestSurveyFile(testRunDate, SurveyTypes.PHONE_STATE_SURVEY.getValue()).lastModified();

        //Then
        assertWithMessage("Latest PhoneState survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }

    @Test
    @RequiresDevice
    public void phoneStateNotNullDataIsNotNull()
    {
        //Given
        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.PHONE_STATE_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //Then
        assertWithMessage("All Non-Null columns are populated")
                .that(PhoneStateDao.allNonNullColumnsArePopulated(geoPackage))
                .isTrue();
    }

    /*
        MONKEY-T65
     */
    @Test
    @RequiresDevice
    public void phonestateDataValuesAreOfExpectedTypesAndRanges() throws JsonProcessingException
    {
        //Given
        ArrayList<PhoneStateModel> results;

        geoPackage = geoPackageManager
                .open(AndroidFiles
                        .getLatestSurveyFile(testRunDate, SurveyTypes.PHONE_STATE_SURVEY.getValue())
                        .getAbsolutePath(), false);

        //When
        results = PhoneStateDao.getRecordsWithAllColumnsPopulated(geoPackage);

        //Then
        assertWithMessage("Results are not empty")
                .that(results)
                .isNotEmpty();

        for (PhoneStateModel row : results)
        {
            assertWithMessage("ID column is within range")
                    .that(row.getId())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("Time column is within range")
                    .that(row.getTime())
                    .isIn(Range.closed(Long.MIN_VALUE, Long.MAX_VALUE));

            assertWithMessage("recordNumber column is within range")
                    .that(row.getRecordNumber())
                    .isIn(Range.closed(1, Integer.MAX_VALUE));

            assertWithMessage("missionID column is within range")
                    .that(row.getMissionId())
                    .isNotEmpty();

            assertWithMessage("latitude value is within range")
                    .that(row.getLatitude())
                    .isIn(Range.open(-90.0, 90.0));

            assertWithMessage("longitude value is within range")
                    .that(row.getLongitude())
                    .isIn(Range.open(-180.0, 180.0));

            assertWithMessage("altitude value is within range")
                    .that(row.getAltitude())
                    .isNotNaN();

            assertWithMessage("simState column is within range")
                    .that(row.getSimState())
                    .isIn(getAllowableSimStates());

            assertWithMessage("simOperator column is within range")
                    .that(row.getSimOperator())
                    .isNotEmpty();

            assertWithMessage("networkRegistrationInfo is within range")
                    .that(row.getNetworkRegistration())
                    .isNotEmpty();
        }
    }

    private List<String> getAllowableSimStates() throws JsonProcessingException
    {
        List<String> simStates = new ArrayList<>();

        for (SimState state : SimState.values())
        {
            simStates.add(new ObjectMapper().writeValueAsString(state).replace("\"", ""));
        }

        return simStates;
    }
}
