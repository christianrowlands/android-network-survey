package com.craxiom.networksurvey.privacy

import android.content.Context
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot

/**
 * The organization's `allow_privacy_zones` restriction. An unmanaged phone always allows privacy
 * zones. A managed phone (one with any managed configuration at all, the same test as
 * [com.craxiom.networksurvey.survey.mdm.SurveyPolicy.isExternalDataUploadAllowed]) allows them only
 * when the organization sets the key to true, so an app update alone never lets an operator hold back
 * the organization's data. When zones are off, saved zones are kept but ignored, so nothing is held
 * back and no Paused state shows.
 *
 * It is an organization rule: the user's "Override organization settings" switch never lifts it
 * (like `allow_ns_scout`). It is listed in the `MdmOverlapKeys` because the user has a matching
 * feature (the zones list), but a Settings row must read [isAllowed] rather than the overlap lock,
 * which the override does lift.
 */
object PrivacyZonePolicy {
    /**
     * @return True on an unmanaged phone, and on a managed phone only when the organization sets
     * `allow_privacy_zones` to true.
     */
    @JvmStatic
    fun isAllowed(snapshot: RestrictionsSnapshot): Boolean {
        if (!snapshot.isManaged) return true
        return snapshot.getBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_PRIVACY_ZONES) == true
    }

    /** @return [isAllowed] for this phone's current managed configuration. */
    @JvmStatic
    fun isAllowed(context: Context): Boolean = isAllowed(AndroidRestrictionsSource(context).snapshot())
}
