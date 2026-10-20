package com.craxiom.networksurvey.ui.onboarding

import com.craxiom.networksurvey.ui.onboarding.OnboardingDecision.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** When a fresh install sees first run, and when the setup is already decided. */
class OnboardingGateTest {

    private fun decide(
        pending: Boolean = true,
        managed: Boolean = false,
        registered: Boolean = false,
        linkPending: Boolean = false,
    ) = OnboardingGate.decide(pending, { managed }, { registered }, { linkPending })

    @Test
    fun `a fresh install shows first run`() {
        assertEquals(OnboardingDecision.Show, decide())
    }

    @Test
    fun `a managed phone skips it`() {
        assertEquals(OnboardingDecision.Skip(SkipReason.MANAGED), decide(managed = true))
    }

    @Test
    fun `a phone registered with NS Analytics skips it`() {
        assertEquals(OnboardingDecision.Skip(SkipReason.NS_ANALYTICS_REGISTERED), decide(registered = true))
    }

    @Test
    fun `a registration link that opened the app skips it`() {
        assertEquals(OnboardingDecision.Skip(SkipReason.REGISTRATION_LINK), decide(linkPending = true))
    }

    @Test
    fun `nothing pending reads nothing else`() {
        val decision = OnboardingGate.decide(
            pending = false,
            managed = { fail("read the managed configuration"); true },
            registered = { fail("read the registration"); true },
            registrationLinkPending = { fail("read the registration link"); true },
        )
        assertEquals(OnboardingDecision.None, decision)
    }
}
