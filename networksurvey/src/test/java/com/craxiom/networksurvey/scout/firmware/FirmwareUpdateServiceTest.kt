package com.craxiom.networksurvey.scout.firmware

import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

/** The foreground service lasts as long as the install: notification and wake lock, then gone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirmwareUpdateServiceTest {
    private val pkg = FirmwareTestData.releasePackage(FirmwareTestData.image())
    private val run = MutableStateFlow<FirmwareUpdateRun>(FirmwareUpdateRun.Idle)
    private val realState = FirmwareUpdateService.runState

    @Before
    fun setUp() {
        FirmwareUpdateService.runState = { run }
    }

    @After
    fun tearDown() {
        FirmwareUpdateService.runState = realState
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun itStaysForegroundWithAWakeLock_whileTheInstallRuns_andStopsWhenItEnds() {
        run.value = FirmwareUpdateRun.Installing(pkg, FirmwareInstallPhase.Transferring(500, 1_000))
        val controller = Robolectric.buildService(FirmwareUpdateService::class.java).create().startCommand(0, 1)
        val service = shadowOf(controller.get())
        idle()

        assertEquals(FirmwareConstants.NOTIFICATION_ID, service.lastForegroundNotificationId)
        assertFalse(service.isStoppedBySelf)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertTrue(wakeLock.isHeld)

        run.value = FirmwareUpdateRun.Failed(pkg, FirmwareUpdateError.ConnectionLost())
        idle()

        assertTrue(service.isForegroundStopped)
        assertTrue(service.isStoppedBySelf)
        controller.destroy()
        assertFalse(wakeLock.isHeld)
    }

    @Test
    fun anInstallThatAlreadyEnded_stopsAtOnce() {
        run.value = FirmwareUpdateRun.Failed(pkg, FirmwareUpdateError.ConnectionLost())

        val controller = Robolectric.buildService(FirmwareUpdateService::class.java).create().startCommand(0, 1)
        idle()

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun start_startsTheService() {
        val application = RuntimeEnvironment.getApplication()

        FirmwareUpdateService.start(application)

        val started = generateSequence { shadowOf(application).nextStartedService }.map { it.component?.className }.toList()
        assertTrue(started.toString(), FirmwareUpdateService::class.java.name in started)
    }

    @Test
    fun theNotification_saysTheStep_andThePercentWhileSending() {
        val resources = RuntimeEnvironment.getApplication().resources

        val sending = FirmwareUpdateNotification.content(resources, FirmwareInstallPhase.Transferring(500, 1_000))
        val restarting = FirmwareUpdateNotification.content(resources, FirmwareInstallPhase.Restarting)

        assertEquals("Sending firmware to the Scout", sending.text)
        assertEquals(50, sending.percent)
        assertEquals("The Scout is restarting", restarting.text)
        assertNull(restarting.percent)
    }
}
