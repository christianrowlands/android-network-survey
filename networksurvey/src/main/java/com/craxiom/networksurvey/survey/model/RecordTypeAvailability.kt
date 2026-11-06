package com.craxiom.networksurvey.survey.model

import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState

/**
 * The one check for which record types the app offers at all. Every place that lists record types
 * (the Survey tiles, the Change sheet, What goes where, the destination rows, the organization line)
 * goes through it, so a type only an NS Scout can record ([RecordType.needsScout]) never shows for
 * someone who has not paired one.
 *
 * Offered means listed, not recorded: a Scout-only type stays listed while this phone is the Wi-Fi
 * source (its tile says it needs the Scout), and the survey leaves it out with
 * [com.craxiom.networksurvey.survey.engine.SkipReason.NEEDS_SCOUT].
 */
object RecordTypeAvailability {

    /**
     * @return True if [type] can be offered with a Scout paired ([scoutPaired]) or not, before the
     * organization's policy.
     */
    @JvmStatic
    fun isCandidate(type: RecordType, scoutPaired: Boolean): Boolean = !type.needsScout || scoutPaired

    /** @return The record types that can be offered, before the organization's policy, in display order. */
    @JvmStatic
    fun candidates(scoutPaired: Boolean): List<RecordType> = RecordType.entries.filter { isCandidate(it, scoutPaired) }

    /** @return True if [type] is offered: a candidate the organization did not turn off. */
    @JvmStatic
    fun isAvailable(type: RecordType, policy: SurveyPolicyState, scoutPaired: Boolean): Boolean =
        isCandidate(type, scoutPaired) && policy.isRecordTypeAvailable(type)

    /** @return The offered record types, in display order. */
    @JvmStatic
    fun available(policy: SurveyPolicyState, scoutPaired: Boolean): List<RecordType> =
        RecordType.entries.filter { isAvailable(it, policy, scoutPaired) }

    /**
     * @return True if the survey can record [type] now: a Scout-only type needs a paired Scout that
     * is the Wi-Fi source ([scoutIsWifiSource]); every other type always can, as far as this check goes.
     */
    @JvmStatic
    fun canRecord(type: RecordType, scoutIsWifiSource: Boolean): Boolean = !type.needsScout || scoutIsWifiSource

    /**
     * @return True if another app's Intent API request may start [type]. A type only an NS Scout
     * records never starts that way (it captures what nearby devices send, which is the choice of
     * the person using the phone), unless the organization set it to on: that is the organization's
     * decision, and it applies to every survey on the phone, not just this request.
     */
    @JvmStatic
    fun canIntentStart(type: RecordType, policy: SurveyPolicyState): Boolean =
        !type.needsScout || policy.lockFor(type) == ManagedState.ON
}
