package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource;
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy;

/**
 * Utilities for MDM properties.
 *
 * @since 0.4.0
 */
public class MdmUtils
{
    /**
     * @return True if the organization has pushed any managed configuration to this app. A phone
     * configured with only MQTT broker keys is managed too.
     */
    public static boolean isManaged(Context context)
    {
        return new AndroidRestrictionsSource(context).snapshot().isManaged();
    }

    /**
     * @return True, if the MDM configuration allows external data uploads or if this device
     * is not under MDM control, false otherwise. Any managed phone is denied unless the
     * organization explicitly sets {@code allow_external_data_upload} to true.
     */
    public static boolean isExternalDataUploadAllowed(Context context)
    {
        return SurveyPolicy.isExternalDataUploadAllowed(new AndroidRestrictionsSource(context).snapshot());
    }

    /**
     * @return True, if the MDM configuration allows NS Analytics uploads or if this device
     * is not under MDM control, false otherwise.
     */
    public static boolean isNsAnalyticsAllowed(Context context)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
            // Default to allowed: an MDM admin must explicitly set allow_ns_analytics=false to block
            // uploads. Reading the property directly (rather than gating on MdmOverlapKeys.ALL)
            // ensures an explicit block is honored even when no other managed config keys are present,
            // and behaves identically for unmanaged devices (no restrictions returns the default).
            // This deliberately differs from isExternalDataUploadAllowed above, which only reads its
            // property when the device is under MDM control and otherwise defaults to allowed. NS
            // Analytics is separately gated by an explicit user registration/opt-in, so a managed
            // device that never sets allow_ns_analytics should not be blocked just for being managed.
            if (mdmProperties != null)
            {
                return mdmProperties.getBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS, true);
            }
        }

        return true;
    }
}
