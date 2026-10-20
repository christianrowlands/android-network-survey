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
 * do nothing. The fingerprint is stored, so a push that arrived while the app was closed is handled
 * the same way when the service is next created (where nothing is recording yet).
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
     * Reads the current policy (so the next change can be compared against it). On the first run
     * ever it only records the current configuration, without acting on it. Afterwards, a
     * configuration that differs from the stored one was pushed while the app was closed, and is
     * handled like any other push. Called when the service is created, so it never starts or
     * changes a running survey.
     */
    fun initialize() {
        val snapshot = restrictions.snapshot()
        val policy = coordinator.refreshPolicy()
        val stored = preferences.getString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, null)
        if (stored == null) {
            recordConfiguration(snapshot)
            return
        }
        if (stored == snapshot.fingerprint()) return

        Timber.i("The organization's managed configuration changed while the app was closed")
        handleChange(snapshot, policy, preferences.getBoolean(SurveyPreferenceKeys.ORG_SHAPED_SETUP, false))
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
        preferences.edit { putBoolean(SurveyPreferenceKeys.ORG_UPDATED_WHILE_OVERRIDDEN, false) }
        recordConfiguration(restrictions.snapshot())
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
        val previousPolicy = coordinator.policy()
        val policy = coordinator.refreshPolicy()
        if (snapshot.fingerprint() == preferences.getString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, null)) return false

        Timber.i("The organization's managed configuration changed")
        if (handleChange(snapshot, policy, previousPolicy.shapesSetup)) {
            coordinator.submit { reconnectMqttIfBrokerChanged() }
        }
        return true
    }

    /**
     * Acts on a configuration that differs from the stored one: records it, clears the user's
     * stop so app-open auto start follows the organization again, and either flags Review (while
     * overridden) or seeds the user's setup and applies it to a running survey in place.
     *
     * @param snapshot         The new managed configuration.
     * @param policy           The policy it gives, with the override as it is now.
     * @param previouslyShaped True if the organization already shaped the setup before this change.
     * @return True if the change was applied, false if it only flagged Review.
     */
    private fun handleChange(snapshot: RestrictionsSnapshot, policy: SurveyPolicyState, previouslyShaped: Boolean): Boolean {
        recordConfiguration(snapshot)
        store.stoppedByUser = false

        if (policy.overrideOn) {
            preferences.edit { putBoolean(SurveyPreferenceKeys.ORG_UPDATED_WHILE_OVERRIDDEN, true) }
            return false
        }

        // A phone that just became managed takes the organization's setup as its starting point,
        // so the organization's stream does not inherit the user's previous picks.
        if (!previouslyShaped) {
            policy.managedSeedSetup(store.getCurrentSetup())?.let { store.setCurrentSetup(it) }
        }

        if (coordinator.orchestrator.isRecording()) {
            coordinator.applySetup(coordinator.effectiveCurrentSetup(), SurveyOrigin.ORGANIZATION)
        }
        return true
    }

    /**
     * Stores the fingerprint of [snapshot] and whether the organization shapes the setup in it.
     * The second is read from the organization's policy without the override, so it stays right
     * while the user overrides it.
     */
    private fun recordConfiguration(snapshot: RestrictionsSnapshot) {
        val shaped = SurveyPolicy.evaluate(snapshot, overrideOn = false).shapesSetup
        preferences.edit {
            putString(SurveyPreferenceKeys.ORG_CONFIG_FINGERPRINT, snapshot.fingerprint())
            putBoolean(SurveyPreferenceKeys.ORG_SHAPED_SETUP, shaped)
        }
    }
}
