package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.listeners.ILiveScanView
import com.craxiom.networksurvey.listeners.IPrivacyZoneExempt
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * The listeners of one kind of survey record, and the only way a record reaches them, so no sink
 * can get around the privacy zone hold.
 *
 * While [isHolding] is true (the phone is in a privacy zone) a record goes only to the live screens
 * ([ILiveScanView]) and to the exempt listeners ([IPrivacyZoneExempt], the Watchlist): log files,
 * MQTT, gRPC, NS Analytics and the community databases get nothing. This is the same split the SSID
 * exclusion list makes, decided once per record so the count and the delivery agree.
 *
 * Thread safe: listeners may come and go while records are delivered.
 *
 * @param isHolding True while records must be held back from the sinks.
 */
class RecordListeners<L : Any>(private val isHolding: BooleanSupplier) : Iterable<L> {
    private val listeners = CopyOnWriteArraySet<L>()

    /** @return True if [listener] was not registered yet. */
    fun add(listener: L): Boolean = listeners.add(listener)

    /** @return True if [listener] was registered. */
    fun remove(listener: L): Boolean = listeners.remove(listener)

    fun isEmpty(): Boolean = listeners.isEmpty()

    fun isNotEmpty(): Boolean = listeners.isNotEmpty()

    override fun iterator(): Iterator<L> = listeners.iterator()

    /**
     * Gives a record to every listener, or while records are held, only to those that may see them
     * ([reachesWhileHeld]). A listener that throws is logged and the rest still get the record.
     *
     * @param what Names the listeners in the log when one throws.
     * @return True if the record reached the sinks; count it as recorded only then.
     */
    fun deliver(what: String, call: Consumer<L>): Boolean {
        val held = isHolding.asBoolean
        for (listener in listeners) {
            if (held && !reachesWhileHeld(listener)) continue
            notify(listener, what, call)
        }
        return !held
    }

    /**
     * Gives something only to the live screens, never to a sink: records the survey does not keep
     * (Bluetooth sightings the duplicate filter left out, an NS Scout snapshot). Not held.
     */
    fun deliverToLiveViews(what: String, call: Consumer<L>) {
        for (listener in listeners) {
            if (listener is ILiveScanView) notify(listener, what, call)
        }
    }

    /**
     * Gives a record to one listener, for a sink that is leaving and takes what is pending first.
     * Held like [deliver]; [listener] does not have to be registered.
     *
     * @return True if the listener got it.
     */
    fun deliverTo(listener: L, what: String, call: Consumer<L>): Boolean {
        if (isHolding.asBoolean && !reachesWhileHeld(listener)) return false
        notify(listener, what, call)
        return true
    }

    private fun notify(listener: L, what: String, call: Consumer<L>) {
        try {
            call.accept(listener)
        } catch (e: Exception) {
            Timber.e(e, "Unable to notify a %s because of an exception", what)
        }
    }

    companion object {
        /** @return True if [listener] still gets records while the phone is in a privacy zone. */
        @JvmStatic
        fun reachesWhileHeld(listener: Any): Boolean = listener is ILiveScanView || listener is IPrivacyZoneExempt
    }
}
