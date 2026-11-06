package com.craxiom.networksurvey.services.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craxiom.networksurvey.logging.PcapngFileWriter
import com.craxiom.networksurvey.logging.RawFramesProgress
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutLinkDetails
import com.craxiom.networksurvey.scout.model.ScoutRawFrames
import com.craxiom.networksurvey.scout.model.WifiSource
import com.craxiom.networksurvey.scout.raw.RawFrameTestData.FRAMES
import com.craxiom.networksurvey.scout.raw.ScoutRawSource
import com.craxiom.networksurvey.services.NetworkSurveyService
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Raw frames sessions: a turn on after storage ran out starts the capture again at once (the old
 * session's state is never read), and a close is never refused while frames back up.
 */
@RunWith(RobolectricTestRunner::class)
class WifiRawControllerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val service = mock<NetworkSurveyService>()
    private val manager = mock<ScoutManager>()
    private val executor: ExecutorService = WifiRawController.newWriterExecutor()
    private var freeBytes = Long.MAX_VALUE
    private lateinit var writer: PcapngFileWriter
    private lateinit var controller: WifiRawController

    @Before
    fun setUp() {
        whenever(service.packageName).thenReturn(app.packageName)
        whenever(service.getSharedPreferences(anyOrNull(), any()))
            .thenAnswer { app.getSharedPreferences(it.getArgument<String>(0), it.getArgument(1)) }
        whenever(manager.pairedScout).thenReturn(MutableStateFlow(SCOUT))
        whenever(manager.wifiSource).thenReturn(MutableStateFlow(WifiSource.SCOUT))
        writer = PcapngFileWriter(
            directory = folder.root,
            application = "Network Survey Android test",
            fileTime = { "20261102-180300" },
            freeSpaceBytes = { freeBytes },
        )
        controller = WifiRawController(service, manager, executor, writer)
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    /** Waits until the writer thread ran everything queued so far. */
    private fun drain() {
        executor.submit {}.get(5, TimeUnit.SECONDS)
    }

    @Test
    fun `a turn on after storage ran out captures again`() {
        freeBytes = 0
        controller.toggleLogging(true)
        controller.enqueue(FRAMES)
        drain()
        assertEquals(RawFramesProgress.State.STORAGE_FULL, controller.progress()?.state)
        verify(manager).release(any())

        freeBytes = Long.MAX_VALUE
        controller.toggleLogging(false)
        controller.toggleLogging(true)

        // The new session says saving before the writer thread even started it
        assertEquals(RawFramesProgress.State.SAVING, controller.progress()?.state)
        verify(manager, times(2)).requestCapture(any(), eq(ScoutRawSource.CAPTURE))

        controller.enqueue(FRAMES)
        drain()
        assertEquals(FRAMES.size.toLong(), controller.progress()?.framesSaved)
        assertEquals(1, folder.root.listFiles().orEmpty().size)
    }

    @Test
    fun `frames queued for an earlier session are not saved in the next one`() {
        val gate = CountDownLatch(1)
        controller.toggleLogging(true)
        executor.execute { gate.await() }
        controller.enqueue(FRAMES)
        controller.toggleLogging(false)
        controller.toggleLogging(true)
        gate.countDown()
        drain()

        assertEquals(0L, controller.progress()?.framesSaved)
        assertEquals(0, folder.root.listFiles().orEmpty().size)
    }

    @Test
    fun `a close is never refused while the writer is behind`() {
        controller.toggleLogging(true)
        controller.enqueue(FRAMES)
        drain()
        val gate = CountDownLatch(1)
        executor.execute { gate.await() }
        repeat(MORE_THAN_THE_FRAME_LIMIT) { controller.enqueue(FRAMES) }

        assertEquals(false, controller.toggleLogging(false))
        gate.countDown()
        drain()

        assertNull(controller.progress())
        // The close ran: a late batch reaches a writer whose session has ended, and adds nothing
        val saved = savedBytes()
        writer.write(FRAMES)
        assertEquals(saved, savedBytes())
    }

    @Test
    fun `frames shed while the writer is behind show in the progress`() {
        controller.toggleLogging(true)
        drain()
        val gate = CountDownLatch(1)
        executor.execute { gate.await() }
        repeat(MORE_THAN_THE_FRAME_LIMIT) { controller.enqueue(FRAMES) }

        val shedBatches = MORE_THAN_THE_FRAME_LIMIT - MAX_PENDING_BATCHES
        assertEquals((shedBatches * FRAMES.size).toLong(), controller.progress()?.framesDropped)
        gate.countDown()
        drain()

        // A new session counts from zero
        controller.toggleLogging(false)
        controller.toggleLogging(true)
        drain()
        assertEquals(0L, controller.progress()?.framesDropped)
    }

    @Test
    fun `a capture that starts during a pause stays paused until the resume`() {
        controller.pauseScanning()
        controller.toggleLogging(true)

        // Started and let go at once: the board is not left capturing
        verify(manager).requestCapture(any(), eq(ScoutRawSource.CAPTURE))
        verify(manager).release(any())

        controller.resumeScanning()
        verify(manager, times(2)).requestCapture(any(), eq(ScoutRawSource.CAPTURE))
        verify(manager).release(any())
    }

    @Test
    fun `a raw notification the link lost counts as one lost frame`() {
        whenever(manager.linkDetails).thenReturn(MutableStateFlow(ScoutLinkDetails()))
        var lost = 0
        val source = ScoutRawSource(manager, {}, { lost += it })
        source.start()

        source.onScoutRawFrames(ScoutRawFrames(SCOUT.scoutId, 0L, emptyList(), 0))
        assertEquals(0, lost)
        source.onScoutRawFrames(ScoutRawFrames(SCOUT.scoutId, 0L, emptyList(), 2))
        assertEquals(2, lost)
    }

    private fun savedBytes(): Long = folder.root.listFiles().orEmpty().sumOf { it.length() }

    private companion object {
        val SCOUT = PairedScout("NS-02000000A1B2", "02:00:00:00:A1:B2", "A1B2", 0, "", "0.1.0", 1, emptyList(), 0L)

        const val MORE_THAN_THE_FRAME_LIMIT = 4_200

        /** The controller's limit on batches waiting for the writer. */
        const val MAX_PENDING_BATCHES = 4_096
    }
}
