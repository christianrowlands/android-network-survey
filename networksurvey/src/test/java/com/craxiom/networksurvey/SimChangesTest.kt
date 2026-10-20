package com.craxiom.networksurvey

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The in-process SIM change signal. */
@OptIn(ExperimentalCoroutinesApi::class)
class SimChangesTest {

    @Test
    fun listenersRunInOrder_beforeCollectors_andStopWhenRemoved() = runTest(UnconfinedTestDispatcher()) {
        val calls = mutableListOf<String>()
        val collecting = SimChanges.changes.onEach { calls += "collector" }.launchIn(this)
        val first = SimChanges.Listener { calls += "first" }
        val second = SimChanges.Listener { calls += "second" }
        SimChanges.addListener(first)
        SimChanges.addListener(second)

        SimChanges.notifyChanged()
        SimChanges.removeListener(first)
        SimChanges.notifyChanged()

        collecting.cancel()
        SimChanges.removeListener(second)
        SimChanges.notifyChanged()

        assertEquals(listOf("first", "second", "collector", "second", "collector"), calls)
    }

    @Test
    fun aListenerAddedTwice_runsOnce() {
        var calls = 0
        val listener = SimChanges.Listener { calls++ }
        SimChanges.addListener(listener)
        SimChanges.addListener(listener)

        SimChanges.notifyChanged()
        SimChanges.removeListener(listener)

        assertEquals(1, calls)
    }
}
