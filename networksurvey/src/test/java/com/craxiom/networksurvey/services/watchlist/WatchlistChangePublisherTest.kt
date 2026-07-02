package com.craxiom.networksurvey.services.watchlist

import com.craxiom.messaging.WatchlistEntryUpdate
import com.craxiom.messaging.WatchlistMatch
import com.craxiom.messaging.watchlist.WatchlistChangeType
import com.craxiom.networksurvey.constants.WatchlistMessageConstants
import com.craxiom.networksurvey.listeners.IWatchlistListener
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.dao.WatchlistDao
import com.craxiom.networksurvey.ui.watchlist.WatchlistEntryFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier

/**
 * Tests for [WatchlistChangePublisher] against a real Room database (Robolectric). Verifies the
 * snapshot-only contract: every published message is a SNAPSHOT carrying the full current list, one is
 * published for every watchlist change, and messageSequence is strictly increasing, including across
 * publisher instances (the reconnect case).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistChangePublisherTest {

    private lateinit var dao: WatchlistDao
    private val publishers = mutableListOf<WatchlistChangePublisher>()

    @Before
    fun setUp() = runBlocking {
        dao = SurveyDatabase.getInstance(RuntimeEnvironment.getApplication()).watchlistDao()
        dao.deleteAll() // the database is a singleton; isolate watchlist state per test
    }

    @After
    fun tearDown() {
        publishers.forEach { it.stop() }
    }

    private class RecordingListener : IWatchlistListener {
        val updates = CopyOnWriteArrayList<WatchlistEntryUpdate>()

        override fun onWatchlistMatch(watchlistMatch: WatchlistMatch) = Unit

        override fun onWatchlistEntryUpdate(watchlistEntryUpdate: WatchlistEntryUpdate) {
            updates.add(watchlistEntryUpdate)
        }
    }

    private fun publisher(
        listener: RecordingListener,
        missionId: String? = "mission-1"
    ): WatchlistChangePublisher {
        val publisher = WatchlistChangePublisher(
            RuntimeEnvironment.getApplication(),
            "test-device",
            Supplier { missionId },
            listener
        )
        publishers.add(publisher)
        return publisher
    }

    /** The publisher works on background dispatchers, so published updates arrive asynchronously. */
    private fun awaitUpdates(listener: RecordingListener, count: Int, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (listener.updates.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(
            "Expected at least $count published updates but got ${listener.updates.size}",
            listener.updates.size >= count
        )
    }

    /** Await until the most recent published update satisfies the condition, and return it. */
    private fun awaitLastUpdate(
        listener: RecordingListener,
        description: String,
        timeoutMs: Long = 10_000,
        condition: (WatchlistEntryUpdate) -> Boolean
    ): WatchlistEntryUpdate {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val last = listener.updates.lastOrNull()
            if (last != null && condition(last)) return last
            Thread.sleep(10)
        }
        throw AssertionError(
            "Timed out waiting for: $description (got ${listener.updates.size} updates)"
        )
    }

    private fun insertEntry(label: String, ssid: String) = runBlocking {
        dao.insert(WatchlistEntryFactory.create(label, ssid, null, 900, 1L))
    }

    @Test
    fun `publishSnapshot publishes the full current list as a snapshot`() {
        insertEntry("HQ", "HQ-Guest")
        insertEntry("Lab", "Lab-Net")
        val listener = RecordingListener()
        val publisher = publisher(listener)

        publisher.publishSnapshot()
        awaitUpdates(listener, 1)

        val update = listener.updates.first()
        assertEquals(
            WatchlistMessageConstants.WATCHLIST_ENTRY_UPDATE_MESSAGE_TYPE,
            update.messageType
        )
        assertEquals(WatchlistChangeType.SNAPSHOT, update.data.changeType)
        assertEquals("test-device", update.data.deviceSerialNumber)
        assertEquals("mission-1", update.data.missionId)
        assertEquals(2, update.data.entriesCount)
        assertTrue(update.data.messageSequence > 0)
        assertTrue(update.version.isNotEmpty())
    }

    @Test
    fun `every watchlist change publishes a fresh snapshot including the empty list`() {
        val listener = RecordingListener()
        publisher(listener).start()

        // A never-used watchlist is suppressed, so the first publish comes from the first insert.
        insertEntry("HQ", "HQ-Guest")
        val afterAdd = awaitLastUpdate(listener, "snapshot with the inserted entry") {
            it.data.entriesCount == 1
        }
        assertEquals(WatchlistChangeType.SNAPSHOT, afterAdd.data.changeType)
        assertEquals("HQ", afterAdd.data.getEntries(0).label)

        // Once the device has published, a transition back to empty MUST publish an empty snapshot.
        runBlocking { dao.deleteAll() }
        val afterClear = awaitLastUpdate(listener, "empty snapshot after clearing the watchlist") {
            it.data.entriesCount == 0
        }
        assertEquals(WatchlistChangeType.SNAPSHOT, afterClear.data.changeType)
    }

    @Test
    fun `a device that has never had a watchlist entry publishes nothing`() {
        val listener = RecordingListener()
        val publisher = publisher(listener)
        publisher.start()
        publisher.publishSnapshot()

        // Both the initial observation and the explicit connect-time snapshot request must be
        // suppressed for a never-used watchlist; give the background work time to (not) publish.
        Thread.sleep(500)
        assertEquals(
            "A never-used watchlist must stay silent",
            0,
            listener.updates.size
        )
    }

    @Test
    fun `message sequences strictly increase including across publisher instances`() {
        insertEntry("HQ", "HQ-Guest")

        val firstListener = RecordingListener()
        val firstPublisher = publisher(firstListener)
        firstPublisher.publishSnapshot()
        firstPublisher.publishSnapshot()
        awaitUpdates(firstListener, 2)
        firstPublisher.stop()

        // A new publisher instance (the reconnect case) must continue the persisted sequence.
        val secondListener = RecordingListener()
        publisher(secondListener).publishSnapshot()
        awaitUpdates(secondListener, 1)

        val sequences =
            (firstListener.updates + secondListener.updates).map { it.data.messageSequence }
        assertEquals(3, sequences.size)
        for (i in 1 until sequences.size) {
            assertTrue(
                "messageSequence must strictly increase: $sequences",
                sequences[i] > sequences[i - 1]
            )
        }
    }

    @Test
    fun `a null mission id publishes an empty missionId`() {
        insertEntry("HQ", "HQ-Guest")
        val listener = RecordingListener()
        publisher(listener, missionId = null).publishSnapshot()
        awaitUpdates(listener, 1)

        assertEquals("", listener.updates.first().data.missionId)
    }
}
