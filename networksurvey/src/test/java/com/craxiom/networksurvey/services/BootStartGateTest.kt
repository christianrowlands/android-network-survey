package com.craxiom.networksurvey.services

import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A boot start only counts when the app's own boot code sent it: another app can send the extra, but
 * not the one-time token, and can't make Android mark its intent as redelivered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootStartGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun spoofed() = Intent(context, NetworkSurveyService::class.java)
        .putExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, true)

    @Test
    fun `the boot code's intent says it is a boot start and is accepted`() {
        val intent = BootStartGate.newStartIntent(context)

        assertTrue(intent.getBooleanExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, false))
        assertTrue(BootStartGate.accept(intent, 0))
    }

    @Test
    fun `a token only works once`() {
        val intent = BootStartGate.newStartIntent(context)
        BootStartGate.accept(intent, 0)

        assertFalse(BootStartGate.accept(intent, 0))
    }

    @Test
    fun `another app sending only the extra is refused`() {
        assertFalse(BootStartGate.accept(spoofed(), 0))
    }

    @Test
    fun `a made up token is refused`() {
        val guessed = spoofed().putExtra("com.craxiom.networksurvey.extra.BOOT_START_TOKEN", "guess")

        assertFalse(BootStartGate.accept(guessed, 0))
    }

    @Test
    fun `a start Android redelivers after the process died is accepted without its token`() {
        assertTrue(BootStartGate.accept(spoofed(), Service.START_FLAG_REDELIVERY))
    }
}
