package com.craxiom.networksurvey.survey.mdm

import android.content.SharedPreferences
import androidx.core.content.edit
import com.craxiom.networksurvey.survey.engine.SurveyCoordinator
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.setup.SurveyPreferenceKeys
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import timber.log.Timber

/**
 * Handles the two ways the organization's control over the survey changes at runtime: the user
 * turning "Override organization settings" on or off, and the organization pushing a new managed
 * configuration.
 *
 * - **Override on:** nothing running changes and the Mission ID is kept. The user's setup is seeded
 *   with what the organization had in force, so the screen looks the same, just unlocked.
 * - **Override off (Restore):** the user's setup goes back to the organization's, and a running
 *   survey is changed in place (no Mission ID roll). An idle survey is never started.
 * - **Organization push while overridden:** nothing changes; a flag tells the screen to offer
 *   Review.
 * - **Organization push otherwise:** applied to the running survey in place. An idle survey is
 *   never started by a push.
 *
 * Pushes are recognized by a fingerprint of the restriction values, so routine identical re-pushes
 * do nothing.
 *
 * @param sendDeviceStatus             Sends one device status message right away, so a broker sees
 *                                     the override change.
 * @param copyOrganizationBrokerToUser Copies the organization's MQTT broker into the user's broker
 *                                     settings, so turning the override on keeps streaming to it.
 * @param reconnectMqttIfBrokerChanged Reconnects a running MQTT stream when the broker it should use
 *                                     changed.
 */
class MdmOverrideController(
    private val coordinator: SurveyCoordinator,
    private val store: SurveySetupStore,
    private val preferences: SharedPreferences,
    private val restrictions: RestrictionsSource,
    private val sendDeviceStatus: () -> Unit,
    private val copyOrganizationBrokerToUser: () -> Unit,
    private val reconnectMqttIfBrokerChanged: () -> Unit,
) {

    /** True when the organization changed its configuration while the override was on. */
    val organizationUpdatedWhileOverridden: Boolean
        get() = preferences.getBoolean(SurveyPreferenceKeys.ORG_UPDATED_WHILE_OVERRIDDEN, false)

    /**
     * Reads the current policy (so the next change can be compared against it) and records the
     * current configuration fingerprint if none is stored yet, without acting on it.
     */
    fun initialize() {
        coordinator.refreshPolicy()
        if (!preferences.contains(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT)) {
            preferences.edit {
                putString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, restrictions.snapshot().fingerprint())
            }
        }
    }

    /** Called when the user turns "Override organization settings" on or off. */
    fun onOverrideChanged(overrideOn: Boolean) {
        val organizationPolicy = SurveyPolicy.evaluate(restrictions.snapshot(), overrideOn = false)
        coordinator.refreshPolicy()
        sendDeviceStatus()

        if (overrideOn) {
            Timber.i("Override turned on; seeding the user's setup from the organization's")
            store.setCurrentSetup(organizationPolicy.effectiveSetup(store.getCurrentSetup()))
            copyOrganizationBrokerToUser()
            return
        }

        Timber.i("Override turned off; restoring the organization's setup")
        organizationPolicy.managedSeedSetup(store.getCurrentSetup())?.let { store.setCurrentSetup(it) }
        preferences.edit {
            putBoolean(SurveyPreferenceKeys.ORG_UPDATED_WHILE_OVERRIDDEN, false)
            putString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, restrictions.snapshot().fingerprint())
        }
        if (coordinator.orchestrator.isRecording()) {
            coordinator.applySetup(coordinator.effectiveCurrentSetup(), SurveyOrigin.ORGANIZATION)
        }
        // Queued behind the reconcile so a stream it stops is not reconnected.
        coordinator.submit { reconnectMqttIfBrokerChanged() }
    }

    /**
     * Called when the managed configuration may have changed.
     *
     * @return True if this was a real change that the app acted on (or flagged for Review).
     */
    fun onRestrictionsChanged(): Boolean {
        val snapshot = restrictions.snapshot()
        val fingerprint = snapshot.fingerprint()
        val previousPolicy = coordinator.policy()
        val policy = coordinator.refreshPolicy()
        if (fingerprint == preferences.getString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, null)) return false

        Timber.i("The organization's managed configuration changed")
        preferences.edit { putString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, fingerprint) }
        store.stoppedByUser = false

        if (policy.overrideOn) {
            preferences.edit { putBoolean(SurveyPreferenceKeys.ORG_UPDATED_WHILE_OVERRIDDEN, true) }
            return true
        }

        // A phone that just became managed takes the organization's setup as its starting point,
        // so the organization's stream does not inherit the user's previous picks.
        if (!previousPolicy.hasSetupLocks && previousPolicy.seedSetup == null) {
            policy.managedSeedSetup(store.getCurrentSetup())?.let { store.setCurrentSetup(it) }
        }

        if (coordinator.orchestrator.isRecording()) {
            coordinator.applySetup(coordinator.effectiveCurrentSetup(), SurveyOrigin.ORGANIZATION)
        }
        coordinator.submit { reconnectMqttIfBrokerChanged() }
        return true
    }
}
