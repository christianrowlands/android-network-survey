package com.craxiom.networksurvey

import androidx.annotation.MainThread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The one in-process signal that the phone's SIMs changed (inserted, removed, or an eSIM turned
 * on or off). [SimChangeReceiver] feeds it from the system's SIM state broadcast; the cellular
 * controller listens through [addListener] and Kotlin screens collect [changes].
 *
 * Listeners run synchronously on the main thread in the order they were added, and always before
 * the collectors of [changes]. So the cellular controller (a listener) has rebuilt its subscription
 * list before any screen that collects [changes] reads that list again, whichever started first.
 */
object SimChanges {

    /** Called on the main thread after the SIMs changed. */
    fun interface Listener {
        fun onSimChanged()
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val collectors = CopyOnWriteArrayList<Listener>()

    /** Emits once per SIM change while collected, after every [Listener] has run. */
    val changes: Flow<Unit> = callbackFlow {
        val collector = Listener { trySend(Unit) }
        collectors.add(collector)
        awaitClose { collectors.remove(collector) }
    }

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Tells every listener that the SIMs changed. */
    @JvmStatic
    @MainThread
    fun notifyChanged() {
        listeners.forEach { it.onSimChanged() }
        collectors.forEach { it.onSimChanged() }
    }
}
