package com.craxiom.networksurvey.util

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [MdmUtils.isNsAnalyticsAllowed].
 *
 * NS Analytics uploads default to allowed. An MDM admin must explicitly set
 * allow_ns_analytics=false to block them; merely having the device under MDM control for
 * unrelated reasons must NOT block uploads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MdmUtilsTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun setApplicationRestrictions(restrictions: Bundle) {
        val restrictionsManager =
            context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        shadowOf(restrictionsManager).setApplicationRestrictions(restrictions)
    }

    @Test
    fun `isNsAnalyticsAllowed returns true when device is not managed`() {
        // No application restrictions set (empty bundle by default)
        assertTrue(MdmUtils.isNsAnalyticsAllowed(context))
    }

    @Test
    fun `isNsAnalyticsAllowed returns true when managed but allow_ns_analytics is absent`() {
        // An unrelated managed config key is present, but allow_ns_analytics was never set.
        // This is the regression the default-allow change fixes.
        val restrictions = Bundle().apply {
            putBoolean(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, true)
        }
        setApplicationRestrictions(restrictions)

        assertTrue(MdmUtils.isNsAnalyticsAllowed(context))
    }

    @Test
    fun `isNsAnalyticsAllowed returns true when allow_ns_analytics is explicitly true`() {
        val restrictions = Bundle().apply {
            putBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS, true)
        }
        setApplicationRestrictions(restrictions)

        assertTrue(MdmUtils.isNsAnalyticsAllowed(context))
    }

    @Test
    fun `isNsAnalyticsAllowed returns false when allow_ns_analytics is explicitly false`() {
        // Explicit admin block, with no other managed config keys present.
        val restrictions = Bundle().apply {
            putBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS, false)
        }
        setApplicationRestrictions(restrictions)

        assertFalse(MdmUtils.isNsAnalyticsAllowed(context))
    }
}
