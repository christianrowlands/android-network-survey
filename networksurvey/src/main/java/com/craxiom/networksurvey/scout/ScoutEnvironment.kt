package com.craxiom.networksurvey.scout

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.scout.link.ScoutBluetooth

/**
 * The phone conditions the Scout manager reacts to: Bluetooth on or off and the Nearby devices
 * permission. An interface so the manager runs in tests without a Bluetooth adapter.
 */
interface ScoutEnvironment {
    /** @return True when Bluetooth is on. */
    fun isBluetoothOn(): Boolean

    /** @return True when the app may connect to Bluetooth devices. */
    fun hasConnectPermission(): Boolean

    /** Calls [onChange] (on the Scout thread) whenever Bluetooth turns on or off. */
    fun watchBluetooth(onChange: () -> Unit)
}

/**
 * [ScoutEnvironment] on a phone. Adapter changes arrive on [handler], the Scout thread, which is
 * only asked for once [watchBluetooth] registers the receiver.
 */
internal class AndroidScoutEnvironment(
    private val context: Context,
    private val handler: () -> Handler,
) : ScoutEnvironment {
    override fun isBluetoothOn(): Boolean = ScoutBluetooth.isEnabled(context)

    override fun hasConnectPermission(): Boolean = ScoutBluetooth.hasConnectPermission(context)

    override fun watchBluetooth(onChange: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = onChange()
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), null, handler(),
            // Exported: the Bluetooth stack (its own uid, not system_server) sends this protected
            // broadcast, and a not-exported receiver never gets it (seen on a Pixel 6, Android 16).
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
