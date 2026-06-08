package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.fragments.SettingsFragment;

/**
 * Utilities for MDM properties.
 *
 * @since 0.4.0
 */
public class MdmUtils
{
    /**
     * @return True, if the restrictions manager is non-null, and at least one MDM property is set.
     */
    public static boolean isUnderMdmControl(Context context, String... propertyKeys)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
            for (String key : propertyKeys)
            {
                if (mdmProperties.containsKey(key)) return true;
            }
        }
        return false;
    }

    /**
     * @return True, if the restrictions manager is non-null, and if the MDM property exist for the specified key.
     */
    public static boolean isUnderMdmControl(Context context, String propertyKey)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
            return mdmProperties.containsKey(propertyKey);
        }

        return false;
    }

    /**
     * @return True, if the restrictions manager is non-null, if the MDM property exist for the specified key, AND if
     * MDM Override is not toggled on.
     */
    public static boolean isUnderMdmControlAndEnabled(Context context, String propertyKey)
    {
        final boolean mdmOverride = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false);
        if (mdmOverride) return false;

        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
            return mdmProperties.containsKey(propertyKey);
        }

        return false;
    }

    /**
     * @return True, if the MDM configuration allows external data uploads or if this device
     * is not under MDM control, false otherwise.
     */
    public static boolean isExternalDataUploadAllowed(Context context)
    {
        if (isUnderMdmControl(context, SettingsFragment.MDM_OVERLAP_PROPERTY_KEYS))
        {
            final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
            if (restrictionsManager != null)
            {
                final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
                return mdmProperties.getBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD, false);
            }
        }

        return true;
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
            // uploads. Reading the property directly (rather than gating on MDM_OVERLAP_PROPERTY_KEYS)
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
