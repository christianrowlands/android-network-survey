package com.craxiom.networksurvey.scout.firmware

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The updater holds the Scout for one install at a time, reports each phase, resumes, and forgets on reset. */
class ScoutFirmwareUpdaterTest {
    private val image = FirmwareTestData.image(size = 8_000, version = "0.2.0")
    private val pkg = FirmwareTestData.releasePackage(image)

    @Test
    fun anInstall_holdsTheScout_reportsItsPhases_andFinishes() = runTest {
        val board = FakeOtaBoard(this).apply { connectNow() }
        val holds = mutableListOf<Boolean>()
        val updater = ScoutFirmwareUpdater(this, holds::add) { FirmwareInstaller(board, it, "2.1.0") }

        assertTrue(updater.install(pkg))
        assertFalse("one install at a time", updater.install(pkg))
        runCurrent()
        assertTrue(updater.state.value is FirmwareUpdateRun.Installing)
        advanceUntilIdle()

        val finished = updater.state.value as FirmwareUpdateRun.Finished
        assertEquals("0.2.0", finished.result.newVersion)
        assertEquals(listOf(true, false), holds)
        updater.clear()
        assertEquals(FirmwareUpdateRun.Idle, updater.state.value)
    }

    @Test
    fun cancel_beforeOtaEnd_endsAsCanceled_andAborts() = runTest {
        val board = FakeOtaBoard(this).apply { connectNow() }
        val updater = ScoutFirmwareUpdater(this, {}) { FirmwareInstaller(board, it, "2.1.0") }

        updater.install(pkg)
        while (board.writes < 5) {
            runCurrent()
            testScheduler.advanceTimeBy(5)
        }
        assertTrue((updater.state.value as FirmwareUpdateRun.Installing).cancelable)
        updater.cancel()
        advanceUntilIdle()

        assertTrue((updater.state.value as FirmwareUpdateRun.Failed).error is FirmwareUpdateError.Canceled)
        assertEquals(1, board.aborts)
    }

    @Test
    fun anInterruptedInstall_resumesWhereTheBoardStopped() = runTest {
        val board = FakeOtaBoard(this).apply { connectNow() }
        board.faults.disconnectOnWrites += (10..200).toSet()
        val updater = ScoutFirmwareUpdater(this, {}) { FirmwareInstaller(board, it, "2.1.0") }

        updater.install(pkg)
        advanceUntilIdle()
        val failed = updater.state.value as FirmwareUpdateRun.Failed
        assertTrue(failed.error.isResumable)

        board.faults.disconnectOnWrites.clear()
        updater.install(failed.pkg)
        advanceUntilIdle()

        assertTrue(updater.state.value is FirmwareUpdateRun.Finished)
        assertTrue("the resume began past 0: ${board.begins}", board.begins.last() > 0)
    }

    @Test
    fun reset_forgetsAFailedPackage() = runTest {
        val board = FakeOtaBoard(this).apply { connectNow() }
        board.faults.disconnectOnWrites += (10..200).toSet()
        val updater = ScoutFirmwareUpdater(this, {}) { FirmwareInstaller(board, it, "2.1.0") }
        updater.install(pkg)
        advanceUntilIdle()
        assertTrue(updater.state.value is FirmwareUpdateRun.Failed)

        updater.reset()

        assertEquals(FirmwareUpdateRun.Idle, updater.state.value)
    }

    @Test
    fun reset_stopsARunningInstall_andEndsIdle() = runTest {
        val board = FakeOtaBoard(this).apply { connectNow() }
        val updater = ScoutFirmwareUpdater(this, {}) { FirmwareInstaller(board, it, "2.1.0") }
        updater.install(pkg)
        while (board.writes < 5) {
            runCurrent()
            testScheduler.advanceTimeBy(5)
        }

        updater.reset()
        advanceUntilIdle()

        assertEquals(FirmwareUpdateRun.Idle, updater.state.value)
        assertFalse(updater.isInstalling)
        assertEquals(1, board.aborts)
    }
}
