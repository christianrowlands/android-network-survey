package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.listeners.ILiveScanView
import com.craxiom.networksurvey.listeners.IPrivacyZoneExempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one delivery path: while records are held for a privacy zone only the live views and the
 * exempt listeners get them, and the caller learns whether to count them.
 */
class RecordListenersTest {

    private open class Recorder {
        val got = mutableListOf<String>()
        open fun take(record: String) {
            got += record
        }
    }

    private class Sink : Recorder()
    private class LiveView : Recorder(), ILiveScanView
    private class Exempt : Recorder(), IPrivacyZoneExempt
    private class Broken : Recorder() {
        override fun take(record: String) = throw IllegalStateException("broken")
    }

    private var holding = false
    private val listeners = RecordListeners<Recorder> { holding }
    private val sink = Sink()
    private val live = LiveView()
    private val exempt = Exempt()

    init {
        listeners.add(sink)
        listeners.add(live)
        listeners.add(exempt)
    }

    @Test
    fun `outside a zone every listener gets the record and it is counted`() {
        assertTrue(listeners.deliver("test") { it.take("r") })
        assertEquals(listOf("r"), sink.got)
        assertEquals(listOf("r"), live.got)
        assertEquals(listOf("r"), exempt.got)
    }

    @Test
    fun `inside a zone only live views and exempt listeners get it, and it is not counted`() {
        holding = true
        assertFalse(listeners.deliver("test") { it.take("r") })
        assertTrue(sink.got.isEmpty())
        assertEquals(listOf("r"), live.got)
        assertEquals(listOf("r"), exempt.got)
    }

    @Test
    fun `live only delivery skips sinks and the exempt listener`() {
        listeners.deliverToLiveViews("test") { it.take("r") }
        assertTrue(sink.got.isEmpty())
        assertTrue(exempt.got.isEmpty())
        assertEquals(listOf("r"), live.got)
    }

    @Test
    fun `a leaving sink gets nothing while held`() {
        holding = true
        val leaving = Sink()
        assertFalse(listeners.deliverTo(leaving, "test") { it.take("r") })
        assertTrue(leaving.got.isEmpty())

        holding = false
        assertTrue(listeners.deliverTo(leaving, "test") { it.take("r") })
        assertEquals(listOf("r"), leaving.got)
    }

    @Test
    fun `a listener that throws does not stop the others`() {
        listeners.add(Broken())
        listeners.deliver("test") { it.take("r") }
        assertEquals(listOf("r"), sink.got)
        assertEquals(listOf("r"), live.got)
    }

    @Test
    fun `add and remove keep a set`() {
        assertFalse(listeners.add(sink))
        assertTrue(listeners.remove(sink))
        assertFalse(listeners.remove(sink))
        assertEquals(2, listeners.count())
        assertTrue(listeners.isNotEmpty())
    }
}
