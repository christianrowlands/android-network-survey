package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.HelloResult
import com.craxiom.networksurvey.scout.link.ScoutGatt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The OTA state machine against [FakeOtaBoard] on virtual time: the transfer with its window and
 * acks, lost acks and chunks, resume after a dropped link, the restart and the version check that
 * catches a rollback, and cancel. Mirrors the iOS app's `FirmwareInstallerTests`.
 */
class FirmwareInstallerTest {
    private val image = FirmwareTestData.image(size = 20_000, version = "0.2.0")
    private val pkg = FirmwareTestData.releasePackage(image)

    private fun TestScope.board(version: String = "0.1.0") = FakeOtaBoard(this, version = version).apply { connectNow() }

    private suspend fun install(board: FakeOtaBoard, use: FirmwarePackage = pkg, phases: MutableList<FirmwareInstallPhase>? = null) =
        FirmwareInstaller(board, use, APP_VERSION).run { phases?.add(it) }

    private suspend fun installFails(board: FakeOtaBoard, use: FirmwarePackage = pkg): FirmwareUpdateError = try {
        install(board, use)
        fail("The install should have failed")
        throw IllegalStateException()
    } catch (e: FirmwareUpdateError) {
        e
    }

    @Test
    fun installsAndConfirmsTheNewVersion() = runTest {
        val board = board()
        val phases = mutableListOf<FirmwareInstallPhase>()
        val result = install(board, phases = phases)

        assertEquals("0.1.0", result.previousVersion)
        assertEquals("0.2.0", result.newVersion)
        assertTrue(result.imageConfirmed)
        assertTrue(result.compatible)
        assertArrayEquals(image, board.installed)
        assertTrue("never more than the window ahead", board.maxAhead <= 2048)
        assertEquals(FirmwareInstallPhase.Connecting, phases.first())
        assertEquals(FirmwareInstallPhase.Transferring(image.size, image.size), phases.filterIsInstance<FirmwareInstallPhase.Transferring>().last())
        assertEquals(
            listOf(FirmwareInstallPhase.Verifying, FirmwareInstallPhase.Restarting, FirmwareInstallPhase.Confirming),
            phases.filter { it !is FirmwareInstallPhase.Transferring && it != FirmwareInstallPhase.Connecting },
        )
    }

    @Test
    fun aLostAck_isRecoveredByTheResendAfterTheTimeout_withoutStartingOver() = runTest {
        val board = board()
        board.faults.dropAcks += 2

        val result = install(board)

        assertEquals("0.2.0", result.newVersion)
        assertEquals(listOf(0), board.begins)
        assertArrayEquals(image, board.installed)
    }

    @Test
    fun aLostChunk_isResentAfterOutOfOrder() = runTest {
        val board = board()
        board.faults.dropWrites += 3

        install(board)

        assertEquals(listOf(0), board.begins)
        assertArrayEquals(image, board.installed)
    }

    @Test
    fun aDroppedLinkMidTransfer_resumesFromTheBoardsOffset() = runTest {
        val board = board()
        board.faults.disconnectOnWrites += 40

        val phases = mutableListOf<FirmwareInstallPhase>()
        val result = install(board, phases = phases)

        assertEquals("0.2.0", result.newVersion)
        assertEquals(2, board.begins.size)
        assertTrue("resumed past 0: ${board.begins}", board.begins[1] > 0)
        assertTrue(phases.contains(FirmwareInstallPhase.Reconnecting(1)))
        assertArrayEquals(image, board.installed)
    }

    @Test
    fun aBoardRestartDuringTheTransfer_startsOverFromZero() = runTest {
        val board = board()
        board.faults.disconnectOnWrites += 40
        board.faults.rebootOnDisconnect = true

        install(board)

        assertEquals(listOf(0, 0), board.begins)
        assertArrayEquals(image, board.installed)
    }

    @Test
    fun tooManyInterruptions_giveUpAsConnectionLost_whichCanResume() = runTest {
        val board = board()
        board.faults.disconnectOnWrites += (1..200 step 3).toSet()

        val error = installFails(board)

        assertTrue(error is FirmwareUpdateError.ConnectionLost)
        assertTrue(error.isResumable)
        // The partial image stays on the board for the resume
        assertEquals(0, board.aborts)
    }

    @Test
    fun aShaMismatch_isReportedAsVerifyFailed() = runTest {
        val board = board()
        board.faults.corruptOffset = 5_000

        assertTrue(installFails(board) is FirmwareUpdateError.VerifyFailed)
    }

    @Test
    fun aFlashWriteFailure_isReported() = runTest {
        val board = board()
        board.faults.writeFailedAtOffset = 3_000

        assertTrue(installFails(board) is FirmwareUpdateError.FlashWriteFailed)
        // The board leaves its OTA state at once rather than after its 5 minute timeout
        assertEquals(1, board.aborts)
    }

    @Test
    fun aRollback_isReported_whenTheBoardComesBackOnItsOldVersion() = runTest {
        val board = board()
        board.faults.rollBack = true

        val error = installFails(board)

        assertTrue(error is FirmwareUpdateError.RolledBack)
        assertEquals("0.1.0", (error as FirmwareUpdateError.RolledBack).version)
    }

    @Test
    fun aBoardThatNeverReturns_isReported() = runTest {
        val board = board()
        board.faults.staysOffAfterRestart = true

        assertTrue(installFails(board) is FirmwareUpdateError.BoardDidNotReturn)
    }

    @Test
    fun anUnansweredOtaEnd_stillConfirmsTheNewVersionByHello() = runTest {
        val board = board()
        board.faults.silentOtaEnd = true

        assertEquals("0.2.0", install(board).newVersion)
    }

    @Test
    fun aWrongChip_isRefusedBeforeOtaBegin() = runTest {
        val board = board()
        val c6 = FirmwareTestData.image(size = 4_000, chipId = FirmwareTestData.CHIP_ID_C6)

        assertTrue(installFails(board, FirmwareTestData.releasePackage(c6)) is FirmwareUpdateError.WrongChip)
        assertTrue(board.begins.isEmpty())
    }

    @Test
    fun anotherBoardModel_isRefusedBeforeOtaBegin_evenOnTheSameChip() = runTest {
        val board = board()
        val forXiao = FirmwareTestData.releasePackage(image, boardModel = "XIAO_ESP32_C5")

        assertTrue(installFails(board, forXiao) is FirmwareUpdateError.WrongBoard)
        assertTrue(board.begins.isEmpty())
    }

    @Test
    fun aReconnectToAnotherBoardModel_stopsTheTransfer() = runTest {
        val board = board()
        // The first link was made before this; the one after the drop reports the other model
        board.model = BoardModel.BOARD_MODEL_XIAO_ESP32_C5
        board.faults.disconnectOnWrites += 40

        assertTrue(installFails(board) is FirmwareUpdateError.WrongBoard)
        assertEquals("no OtaBegin on the other board", 1, board.begins.size)
    }

    @Test
    fun noLink_isNotConnected() = runTest {
        // A board that is never connected and drops every connect attempt
        val board = FakeOtaBoard(this)
        board.faults.failedConnectsAfterDrop = Int.MAX_VALUE
        board.dropLink()

        val error = installFails(board)

        assertTrue(error is FirmwareUpdateError.NotConnected)
        assertTrue(error.isResumable)
    }

    @Test
    fun cancelDuringTheTransfer_sendsOtaAbort() = runTest {
        val board = board()
        val run = async(start = CoroutineStart.UNDISPATCHED) { runCatching { install(board) } }
        // Let a few chunks go out, then cancel
        while (board.writes < 10) runCurrent().also { testScheduler.advanceTimeBy(10) }
        run.cancel()
        advanceUntilIdle()

        assertEquals(1, board.aborts)
        assertTrue(board.installed == null)
    }

    @Test
    fun reinstallingTheSameVersion_needsTheBoardsConfirmation() = runTest {
        val same = FirmwareTestData.image(size = 4_000, version = "0.1.0")
        val board = FakeOtaBoard(this, version = "0.1.0", imageVersion = "0.1.0").apply { connectNow() }

        val result = install(board, FirmwareTestData.releasePackage(same, "0.1.0"))

        assertEquals("0.1.0", result.newVersion)
        assertTrue(result.imageConfirmed)
    }

    @Test
    fun aConfirmationOnALaterHello_isAwaited() = runTest {
        val same = FirmwareTestData.image(size = 4_000, version = "0.1.0")
        val board = FakeOtaBoard(this, version = "0.1.0", imageVersion = "0.1.0").apply { connectNow() }
        board.faults.unconfirmedHellos = 2

        val result = install(board, FirmwareTestData.releasePackage(same, "0.1.0"))

        assertTrue(result.imageConfirmed)
        assertTrue("Hello repeated: ${board.hellos}", board.hellos >= 4)
    }

    @Test
    fun aSameVersionReinstallThatRollsBack_isNotConfirmed() = runTest {
        val same = FirmwareTestData.image(size = 4_000, version = "0.1.0")
        val board = FakeOtaBoard(this, version = "0.1.0", imageVersion = "0.1.0").apply { connectNow() }
        board.faults.rollBack = true

        val error = installFails(board, FirmwareTestData.releasePackage(same, "0.1.0"))

        assertTrue(error is FirmwareUpdateError.NotConfirmed)
    }

    @Test
    fun aNewVersion_countsEvenWhenTheConfirmationWasLost() = runTest {
        val board = board()
        board.faults.forgetsConfirmation = true

        val result = install(board)

        assertEquals("0.2.0", result.newVersion)
        assertFalse(result.imageConfirmed)
    }

    @Test
    fun aNewImageOfAnotherProtocolMajor_isJudgedByItsVersion() = runTest {
        val board = board()
        board.faults.incompatibleAfterRestart = true

        val result = install(board)

        assertEquals("0.2.0", result.newVersion)
        assertFalse(result.compatible)
    }

    @Test
    fun restartChecks_followProtocolSection10Step5() {
        fun hello(version: String, confirmed: Boolean = false, compatible: Boolean = true) = HelloResult.newBuilder()
            .setFirmwareVersion(version).setOtaImageConfirmed(confirmed).setCompatible(compatible).build()

        assertEquals(FirmwareInstaller.RestartVerdict.INSTALLED, FirmwareInstaller.checkRestart(hello("0.2.0"), "0.2.0", "0.1.0", true))
        assertThrows<FirmwareUpdateError.RolledBack> { FirmwareInstaller.checkRestart(hello("0.1.0"), "0.2.0", "0.1.0", true) }
        // Without OtaEnd's OK the old version may just mean the transfer never finished
        assertThrows<FirmwareUpdateError.ConnectionLost> { FirmwareInstaller.checkRestart(hello("0.1.0"), "0.2.0", "0.1.0", false) }
        assertThrows<FirmwareUpdateError.UnexpectedVersion> { FirmwareInstaller.checkRestart(hello("0.3.0"), "0.2.0", "0.1.0", true) }
        assertEquals(
            FirmwareInstaller.RestartVerdict.AWAITING_CONFIRMATION,
            FirmwareInstaller.checkRestart(hello("0.1.0"), "0.1.0", "0.1.0", true),
        )
        assertEquals(FirmwareInstaller.RestartVerdict.INSTALLED, FirmwareInstaller.checkRestart(hello("0.1.0", confirmed = true), "0.1.0", "0.1.0", true))
        assertEquals(FirmwareInstaller.RestartVerdict.INSTALLED, FirmwareInstaller.checkRestart(hello("0.1.0", compatible = false), "0.1.0", "0.1.0", true))
    }

    @Test
    fun theChunkLayout_neverExceedsTheWindow_orTheWriteLimit() {
        assertEquals(240 to 1920, OtaChunkSender.layout(maxChunkSize = 240, windowBytes = 2048, writeLimit = 514))
        assertEquals(176 to 1936, OtaChunkSender.layout(maxChunkSize = 506, windowBytes = 2048, writeLimit = 180))
        assertEquals(100 to 100, OtaChunkSender.layout(maxChunkSize = 506, windowBytes = 100, writeLimit = 514))
        assertEquals(0 to 0, OtaChunkSender.layout(maxChunkSize = 506, windowBytes = 2048, writeLimit = 4))
        // MTU 517: the board offers 510-byte chunks, but Android writes at most 512 bytes (offset included)
        assertEquals(512, ScoutGatt.maxWriteBytes(517))
        assertEquals(182, ScoutGatt.maxWriteBytes(185))
        assertEquals(508 to 2032, OtaChunkSender.layout(maxChunkSize = 510, windowBytes = 2048, writeLimit = ScoutGatt.maxWriteBytes(517)))
    }

    @Test
    fun aChunk_isTheLittleEndianOffsetThenTheBytes() {
        val data = ByteArray(0x20000) { it.toByte() }
        val chunk = OtaChunkSender.chunk(data, 0x10203, 0x10206)

        assertArrayEquals(byteArrayOf(0x03, 0x02, 0x01, 0x00, 0x03, 0x04, 0x05), chunk)
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }

    private companion object {
        const val APP_VERSION = "2.1.0"
    }
}
