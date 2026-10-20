package com.craxiom.networksurvey.ui.onboarding

/** Whether first run shows, and why it was skipped. */
sealed interface OnboardingDecision {
    /** Nothing to do: first run was finished or skipped before, or this phone was upgraded. */
    data object None : OnboardingDecision

    /** Show the first run cards. */
    data object Show : OnboardingDecision

    /** Skip first run for good, because [reason] already decided the setup. */
    data class Skip(val reason: SkipReason) : OnboardingDecision

    enum class SkipReason { MANAGED, NS_ANALYTICS_REGISTERED, REGISTRATION_LINK }
}

/**
 * Decides whether a fresh install sees first run. It is skipped when the setup is already decided:
 * an organization manages the phone (any managed configuration, the zero-touch fleet case), the
 * phone is registered with NS Analytics, or a registration link opened the app.
 *
 * The checks after [pending] are lambdas so a phone that has finished first run never reads the
 * managed configuration or the encrypted NS Analytics storage at startup.
 */
object OnboardingGate {

    fun decide(
        pending: Boolean,
        managed: () -> Boolean,
        registered: () -> Boolean,
        registrationLinkPending: () -> Boolean,
    ): OnboardingDecision = when {
        !pending -> OnboardingDecision.None
        managed() -> OnboardingDecision.Skip(OnboardingDecision.SkipReason.MANAGED)
        registered() -> OnboardingDecision.Skip(OnboardingDecision.SkipReason.NS_ANALYTICS_REGISTERED)
        registrationLinkPending() -> OnboardingDecision.Skip(OnboardingDecision.SkipReason.REGISTRATION_LINK)
        else -> OnboardingDecision.Show
    }
}
