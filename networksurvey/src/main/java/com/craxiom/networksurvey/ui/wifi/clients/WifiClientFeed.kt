package com.craxiom.networksurvey.ui.wifi.clients

import com.craxiom.networksurvey.listeners.IWifiClientLiveListener
import com.craxiom.networksurvey.scout.clients.ScoutClientSnapshot
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.signals.ScanListenerRegistration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One screen's live Wi-Fi clients: an [IWifiClientLiveListener] registered with the bound survey
 * service only while the screen wants clients, and the latest snapshot as state. Registering is
 * what makes the NS Scout capture clients (when it is the Wi-Fi source), so each screen registers
 * only while it shows them. Shared by the Clients list, Client details and an access point's
 * Connected clients card, each with its own feed.
 *
 * Snapshots arrive on the Scout thread and are applied on [mainDispatcher]; call the other methods
 * from the main thread.
 *
 * @param onSnapshot Runs on the main thread after each applied snapshot.
 */
class WifiClientFeed(
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val onSnapshot: (ScoutClientSnapshot) -> Unit = {},
) : IWifiClientLiveListener {
    private val mutableSnapshot = MutableStateFlow(ScoutClientSnapshot.EMPTY)

    /** The latest snapshot, [ScoutClientSnapshot.EMPTY] before the first. */
    val snapshot: StateFlow<ScoutClientSnapshot> = mutableSnapshot.asStateFlow()

    private val registration = ScanListenerRegistration(
        register = { it.registerWifiClientLiveListener(this) },
        unregister = { it.unregisterWifiClientLiveListener(this) },
    )

    /** Called with the bound service, and with null before the screen unbinds. */
    fun onServiceChanged(service: NetworkSurveyService?, wanted: Boolean) = registration.onServiceChanged(service, wanted)

    /** Registers or unregisters to match whether the screen wants clients now. */
    fun update(wanted: Boolean) = registration.update(wanted)

    /** Unregisters; the owning view model calls it from onCleared. */
    fun release() = registration.release()

    override fun onWifiClientSnapshot(snapshot: ScoutClientSnapshot) {
        scope.launch(mainDispatcher) {
            // A snapshot in flight when the screen unregistered is not news
            if (!registration.isRegistered) return@launch
            mutableSnapshot.value = snapshot
            onSnapshot(snapshot)
        }
    }
}
