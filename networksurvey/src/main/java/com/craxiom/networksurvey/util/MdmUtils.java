package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import timber.log.Timber;

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
        for (String propertyKey : propertyKeys)
        {
            if (isUnderMdmControl(context, propertyKey))
            {
                return true;
            }
        }
        return true;
    }

    /**
     * @return True, if the restrictions manager is non-null, and if MDM properties exist for certain
     * preferences.
     */
    public static boolean isUnderMdmControl(Context context, String propertyKey)
    {
        Bundle mdmProperties = checkAndGetMdmProperties(context, propertyKey);
        if (mdmProperties != null)
        {
            Timber.i("Network Survey is under MDM control");
            return true;
        }

        return false;
    }

    /**
     * Get the MDM properties if a property specified by propertyKey exists.
     *
     * @param context     The application context
     * @param propertyKey The property key in question
     * @return The MDM properties. Null if the property cannot be found
     */
    public static Bundle getMdmProperties(Context context, String propertyKey)
    {
        Bundle mdmProperties = checkAndGetMdmProperties(context, propertyKey);
        if (mdmProperties != null)
        {
            Timber.i("Property %s found!", propertyKey);
            return mdmProperties;
        }

        return null;
    }

    private static Bundle checkAndGetMdmProperties(Context context, String propertyKey)
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) context.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();
            if (mdmProperties.getInt(propertyKey, 0) != 0)
            {
                return mdmProperties;
            }
        }
        return null;
    }
}
