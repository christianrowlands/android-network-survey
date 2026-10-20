package com.craxiom.networksurvey.ui.signals

import android.annotation.SuppressLint
import com.craxiom.networksurvey.services.NetworkSurveyService

/**
 * Keeps one survey record listener registered with the bound service only while a Signals view can
 * use it. Registering a listener starts that radio's scanning in the service, which without a
 * permission or with the radio off either fails silently, toasts, or gives up for good; so a view
 * registers only once it is bound and nothing is in the way, and registers again when the obstacle
 * clears.
 *
 * Not thread safe: call it from the main thread. [isRegistered] may be read from any thread, so a
 * record already in flight when the view unregisters can be dropped.
 *
 * @param register Adds the listener to the service.
 * @param unregister Removes the listener from the service.
 * @param onRegistered Runs after each registration (start a ticker, ask for a first scan).
 * @param onUnregistered Runs after each unregistration.
 */
class ScanListenerRegistration(
    private val register: (NetworkSurveyService) -> Unit,
    private val unregister: (NetworkSurveyService) -> Unit,
    private val onRegistered: (NetworkSurveyService) -> Unit = {},
    private val onUnregistered: () -> Unit = {},
) {
    // Held only between a bind and the unbind (onServiceChanged(null) clears it).
    @SuppressLint("StaticFieldLeak")
    private var service: NetworkSurveyService? = null

    /** True while the listener is registered with the service. */
    @Volatile
    var isRegistered = false
        private set

    /**
     * Called with the bound service, and with null before the view unbinds; null always
     * unregisters, since the service stops itself only once no listener is left.
     *
     * @param ready Whether the view can scan right now.
     */
    fun onServiceChanged(newService: NetworkSurveyService?, ready: Boolean) {
        // A different service (a rebind without the null in between) gets the listener afresh.
        if (newService == null || newService !== service) release()
        service = newService
        update(ready)
    }

    /** Registers or unregisters to match whether the view can scan right now. */
    fun update(ready: Boolean) {
        val bound = service
        if (bound != null && ready && !isRegistered) {
            isRegistered = true
            register(bound)
            onRegistered(bound)
        } else if (isRegistered && !ready) {
            release()
        }
    }

    /** Unregisters if registered; the view model calls it from onCleared too. */
    fun release() {
        if (!isRegistered) return
        isRegistered = false
        service?.let(unregister)
        onUnregistered()
    }
}
