package com.craxiom.networksurvey.scout

import android.content.Context
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import timber.log.Timber

/**
 * The organization's `allow_ns_scout` restriction. Unset (or an unmanaged phone) means allowed;
 * false hides the More row, the Scout page and every Scout surface, and keeps Wi-Fi on the phone.
 * The restriction is an organization rule only (the user has no matching setting), so it is not one
 * of the `MdmOverlapKeys`.
 */
object ScoutPolicy {
    /** @return True unless the organization set `allow_ns_scout` to false. */
    @JvmStatic
    fun isAllowed(snapshot: RestrictionsSnapshot): Boolean =
        snapshot.getBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_SCOUT) != false

    /** @return True unless this phone's managed configuration sets `allow_ns_scout` to false. */
    @JvmStatic
    fun isAllowed(context: Context): Boolean = isAllowed(AndroidRestrictionsSource(context).snapshot())

    /**
     * Makes the phone the Wi-Fi source when the Scout is and the organization doesn't allow it.
     *
     * @return True when it switched the source, so the caller updates a recording survey.
     */
    @JvmStatic
    fun enforce(context: Context): Boolean {
        val manager = ScoutManager.getInstance(context)
        if (manager.wifiSource.value != WifiSource.SCOUT || isAllowed(context)) return false
        Timber.i("The organization doesn't allow NS Scout; Wi-Fi comes from the phone")
        manager.setWifiSource(WifiSource.PHONE)
        return true
    }
}
