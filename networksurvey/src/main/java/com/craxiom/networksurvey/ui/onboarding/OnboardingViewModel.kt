package com.craxiom.networksurvey.ui.onboarding

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.RestrictionsSource
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.ui.survey.model.SurveyUiStateMapper
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber

/** Where the last first run card sends the data. */
enum class OnboardingChoice { NS_ANALYTICS, LOG_FILES, DECIDE_LATER }

/**
 * The upgraded user's own setup, for the welcome: what it records, where it goes, and whether it
 * starts on its own.
 */
data class UpgradeWelcomeSetup(val types: List<RecordType>, val destinations: List<Destination>, val autoStart: Boolean)

/**
 * Activity-scoped state for first run: whether the cards show (decided by [OnboardingGate] when
 * created and again on every resume, so a managed configuration or a registration link that
 * arrives meanwhile still skips them) and the one-time write of the user's choice.
 *
 * The decision is made synchronously so the first frame is already the cards or the tabs. It only
 * reads more than one preference on a phone where first run is still pending.
 */
class OnboardingViewModel @JvmOverloads constructor(
    application: Application,
    private val store: SurveySetupStore = SurveySetupStore(application),
    private val restrictions: RestrictionsSource = restrictionsOverride ?: AndroidRestrictionsSource(application),
    private val isRegistered: () -> Boolean = { NsAnalyticsSecureStorage.isRegistered(application) },
    private val registrationLinkPending: () -> Boolean = { NsAnalyticsSecureStorage.getQrData(application) != null },
    private val autoIncludePhoneState: () -> Boolean = { SurveyTypeResolver.autoIncludePhoneState(application) },
) : AndroidViewModel(application) {

    private val mutableVisible = MutableStateFlow(evaluate())
    private val mutableWelcome = MutableStateFlow(evaluateWelcome())
    private val finishedEvents = Channel<Unit>(Channel.CONFLATED)

    /** True while the first run cards show instead of the tabs. */
    val visible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    /** True while the one-time welcome for an upgraded phone shows instead of the tabs. */
    val welcome: StateFlow<Boolean> = mutableWelcome.asStateFlow()

    /** True while first run or the welcome covers the tabs, so the launch checks wait. */
    val coversTabs: Boolean
        get() = mutableVisible.value || mutableWelcome.value

    /** Fires when the user finishes or skips the cards, so the activity can run its startup checks. */
    val finished: Flow<Unit> = finishedEvents.receiveAsFlow()

    /**
     * Set once the launch permissions were asked for, by the permission card or by the activity,
     * so they are asked once per activity even across a rotation.
     */
    var permissionsRequested: Boolean = false
        private set

    /** Re-checks the skip rules; the activity calls this first thing in onResume. */
    fun onResume() {
        if (mutableVisible.value) mutableVisible.value = evaluate()
        if (mutableWelcome.value) mutableWelcome.value = evaluateWelcome()
    }

    fun onPermissionsRequested() {
        permissionsRequested = true
    }

    /**
     * Ends first run. [choice] is null for Skip, which leaves the setup as it is.
     */
    fun finish(choice: OnboardingChoice?) {
        val setup = store.getCurrentSetup()
        when (choice) {
            OnboardingChoice.NS_ANALYTICS -> store.setCurrentSetup(SurveySetup.custom(setup.recordTypes, setOf(Destination.NS_ANALYTICS)))
            OnboardingChoice.LOG_FILES -> store.setCurrentSetup(SurveySetup.custom(setup.recordTypes, setOf(Destination.LOG_FILES)))
            OnboardingChoice.DECIDE_LATER, null -> Unit
        }
        store.onboardingPending = false
        mutableVisible.value = false
        finishedEvents.trySend(Unit)
    }

    /** Ends the welcome for good. */
    fun finishWelcome() {
        store.upgradeWelcomePending = false
        mutableWelcome.value = false
        finishedEvents.trySend(Unit)
    }

    /** @return The setup the upgrade carried over, as the Survey tab will show it. */
    fun welcomeSetup(): UpgradeWelcomeSetup {
        val setup = store.getCurrentSetup()
        val types = DestinationCapabilities.effectiveRecordTypes(setup.recordTypes, autoIncludePhoneState())
        return UpgradeWelcomeSetup(
            types = SurveyUiStateMapper.ordered(types),
            destinations = Destination.entries.filter { it in setup.destinations },
            autoStart = store.userAutoStart,
        )
    }

    /**
     * The welcome is for the person who used the earlier version; on a phone an organization
     * manages, the organization decides the survey, so it is skipped like first run.
     */
    private fun evaluateWelcome(): Boolean {
        if (!store.upgradeWelcomePending) return false
        if (safely("managed configuration") { restrictions.snapshot().isManaged }) {
            Timber.i("Skipping the upgrade welcome on a managed phone")
            store.upgradeWelcomePending = false
            return false
        }
        return true
    }

    private fun evaluate(): Boolean {
        val decision = OnboardingGate.decide(
            pending = store.onboardingPending,
            managed = { restrictions.snapshot().isManaged },
            registered = { safely("NS Analytics registration") { isRegistered() } },
            registrationLinkPending = { safely("NS Analytics registration link") { registrationLinkPending() } },
        )
        if (decision is OnboardingDecision.Skip) {
            Timber.i("Skipping first run: %s", decision.reason)
            store.onboardingPending = false
        }
        return decision == OnboardingDecision.Show
    }

    private fun safely(what: String, read: () -> Boolean): Boolean = try {
        read()
    } catch (e: Exception) {
        Timber.e(e, "Could not read the %s for first run", what)
        false
    }

    companion object {
        /**
         * Replaces the managed configuration first run reads. Only the instrumented tests set it,
         * since a phone's managed configuration can only be set by a device policy app.
         */
        @VisibleForTesting
        @Volatile
        var restrictionsOverride: RestrictionsSource? = null
    }
}
