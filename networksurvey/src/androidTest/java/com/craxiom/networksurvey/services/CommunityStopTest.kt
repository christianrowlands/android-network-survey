package com.craxiom.networksurvey.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stop always stops the Community databases destination, even after the organization's upload
 * gate (`allow_external_data_upload`) turned false during the survey. The gate only refuses a start.
 */
@RunWith(AndroidJUnit4::class)
class CommunityStopTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @get:Rule
    val permissionRule = DestinationTestSupport.permissionRule()

    private lateinit var service: NetworkSurveyService

    @Before
    fun setUp() {
        DestinationTestSupport.resetPreferences {
            it.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, true)
            it.putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_UPLOAD_ENABLED, false)
        }
        service = DestinationTestSupport.bindService(serviceRule)
    }

    @After
    fun tearDown() {
        service.setRestrictionsSourceForTest(null)
        DestinationTestSupport.stop(service)
    }

    @Test
    fun stop_endsCommunity_afterTheUploadGateTurnedOff() {
        DestinationTestSupport.start(service, setOf(RecordType.CELLULAR), setOf(Destination.COMMUNITY))
        assertThat(service.isUploadScanningActive).isTrue()

        // The phone becomes managed by a configuration without allow_external_data_upload.
        service.setRestrictionsSourceForTest {
            RestrictionsSnapshot(mapOf(NetworkSurveyConstants.PROPERTY_MQTT_START_ON_BOOT to false))
        }
        DestinationTestSupport.stop(service)

        assertThat(service.isUploadScanningActive).isFalse()
        assertThat(service.isAnySurveyActive).isFalse()
    }
}
