package com.craxiom.networksurvey.survey.engine

import android.content.Context
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.model.SurveyTypes
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * Reads the inputs the survey rules need from the app's settings: the user's setup with the
 * organization's locks applied, the "Phone state with Cellular" setting, and the community upload
 * targets.
 */
object SurveyTypeResolver {

    /** @return The organization policy in force right now (managed configuration plus override switch). */
    @JvmStatic
    fun currentPolicy(context: Context): SurveyPolicyState = SurveyPolicy.evaluate(
        AndroidRestrictionsSource(context).snapshot(),
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false),
    )

    /**
     * @return The record types [destination] would receive from the user's working setup (with the
     * organization's locks applied). Used when a destination is turned on outside a survey start,
     * and to fill the record type flags of a shared MQTT QR code for older app versions.
     */
    @JvmStatic
    fun typesForCurrentSetup(context: Context, destination: Destination): Set<RecordType> {
        val policy = currentPolicy(context)
        val setup = policy.effectiveSetup(SurveySetupStore(context).getCurrentSetup())
        val types = DestinationCapabilities.effectiveRecordTypes(
            setup.recordTypes,
            autoIncludePhoneState(context),
            policy.phoneStateForcedOff,
        )
        return DestinationCapabilities.typesFor(destination, types, communityTargets(context))
    }

    /** @return The `auto_include_phone_state_with_cellular` setting, MDM aware. */
    @JvmStatic
    fun autoIncludePhoneState(context: Context): Boolean = PreferenceUtils.getAutoStartPreference(
        NetworkSurveyConstants.PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR, true, context,
    )

    /** @return The record types the selected community upload targets (OpenCelliD, BeaconDB) need. */
    @JvmStatic
    fun communityTargets(context: Context): Set<RecordType> =
        PreferenceUtils.getRequiredProtocolsForUpload(context).mapNotNull {
            when (it) {
                SurveyTypes.CELLULAR -> RecordType.CELLULAR
                SurveyTypes.WIFI -> RecordType.WIFI
                else -> null
            }
        }.toSet()
}
