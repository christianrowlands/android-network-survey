package com.craxiom.networksurvey.tests.cellular;

import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.helpers.AndroidFiles;
import com.craxiom.networksurvey.models.SurveyTypes;
import com.craxiom.networksurvey.screens.BottomMenuBar;
import com.craxiom.networksurvey.screens.TopMenuBar;
import mil.nga.geopackage.factory.GeoPackageFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.*;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.*;

public class CellularGeoPackageTest extends TestBase
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
        MONKEY-T70
     */
    @Test
    public void cellularSurveyDataGeneratedUponTestRun()
    {
        //Given
        Long fileDate = AndroidFiles
                .getLatestSurveyFile(testRunDate, SurveyTypes.CELLULAR_SURVEY.getValue())
                .lastModified();
        //Then
        assertWithMessage("Latest cellular survey file is newer than the beginning of the test run")
                .that(fileDate)
                .isGreaterThan(testRunStartTime.toEpochDay());
    }
}
