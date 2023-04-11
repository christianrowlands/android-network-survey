package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;

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
}
