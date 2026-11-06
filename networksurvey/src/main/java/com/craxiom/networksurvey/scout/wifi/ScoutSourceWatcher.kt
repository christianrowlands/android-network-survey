package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.model.WifiSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.function.Consumer

/**
 * Tells the Wi-Fi controller when the Scout manager changes the Wi-Fi source on its own: pairing
 * a Scout selects it, removing the Scout selects the phone. A Java-friendly wrapper over
 * [ScoutManager.wifiSource].
 */
class ScoutSourceWatcher(private val manager: ScoutManager) {
    private var scope: CoroutineScope? = null

    /** Calls [onChange] with the current source and then with every change, off the main thread. */
    @Synchronized
    fun start(onChange: Consumer<WifiSource>) {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        manager.wifiSource.onEach { onChange.accept(it) }.launchIn(newScope)
        scope = newScope
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
    }
}
