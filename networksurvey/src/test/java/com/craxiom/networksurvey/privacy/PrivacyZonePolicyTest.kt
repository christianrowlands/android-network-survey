package com.craxiom.networksurvey.privacy

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.MdmOverlapKeys
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The organization's `allow_privacy_zones`: always allowed on an unmanaged phone; on a managed phone
 * (any managed configuration at all) allowed only when the organization sets it to true. The
 * override is not an input, so it can never lift the rule (SettingsModelTest covers the switch).
 */
class PrivacyZonePolicyTest {

    private val key = NetworkSurveyConstants.MDM_PROPERTY_ALLOW_PRIVACY_ZONES
    private val brokerOnly = mapOf<String, Any?>("mqtt_broker_host" to "broker.example.com")

    @Test
    fun `an unmanaged phone allows zones`() {
        assertTrue(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot.EMPTY))
    }

    @Test
    fun `a managed phone that does not set the key turns zones off`() {
        assertFalse(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot(brokerOnly)))
    }

    @Test
    fun `a managed phone allows zones only when the organization sets true`() {
        assertTrue(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot(brokerOnly + (key to true))))
        assertFalse(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot(brokerOnly + (key to false))))
    }

    @Test
    fun `the key alone makes the phone managed`() {
        assertTrue(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot(mapOf(key to true))))
        assertFalse(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot(mapOf(key to false))))
    }

    @Test
    fun `a value of the wrong type counts as unset`() {
        assertFalse(PrivacyZonePolicy.isAllowed(RestrictionsSnapshot(mapOf(key to "true"))))
    }

    @Test
    fun `the key locks the Settings row`() {
        assertTrue(key in MdmOverlapKeys.ALL)
    }
}
