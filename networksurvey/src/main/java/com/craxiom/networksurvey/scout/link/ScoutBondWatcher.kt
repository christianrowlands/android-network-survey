package com.craxiom.networksurvey.scout.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Bonds the phone with one Scout and watches that bond for the life of a link. Pairing is
 * explicit (`createBond()`) rather than left to the first encrypted access, so the prompt appears
 * at a known step. A bond that falls back to none while the link is up means Android dropped the
 * pairing, which [onBondLost] reports as a stale bond.
 *
 * Broadcasts arrive on [handler] (the Scout thread). The caller holds BLUETOOTH_CONNECT.
 */
@SuppressLint("MissingPermission")
internal class ScoutBondWatcher(
    private val context: Context,
    private val handler: Handler,
    private val device: BluetoothDevice,
    private val onBondLost: () -> Unit,
) {
    private var bondResult: CompletableDeferred<Int>? = null
    private var registered = false
    private var everBonded = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val changed = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (changed?.address != device.address) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            Timber.i("Scout bond state is now %d", state)
            when (state) {
                BluetoothDevice.BOND_BONDED -> {
                    everBonded = true
                    bondResult?.complete(state)
                }

                BluetoothDevice.BOND_NONE -> {
                    bondResult?.complete(state)
                    if (everBonded && bondResult == null) {
                        Timber.w("Android dropped the Scout's bond while the link was up")
                        onBondLost()
                    }
                }
            }
        }
    }

    /** Starts listening for bond changes. */
    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), null, handler,
            // Exported: the Bluetooth stack (its own uid, not system_server) sends this protected
            // broadcast, and a not-exported receiver never gets it (seen on a Pixel 6, Android 16).
            ContextCompat.RECEIVER_EXPORTED,
        )
        registered = true
    }

    /** Stops listening. Safe to call more than once. */
    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
        // Fail a pairing still waiting as a link failure (a cancel would end it silently).
        bondResult?.completeExceptionally(ScoutLinkException(ScoutLinkFailure.LINK_LOST, "the link ended while pairing"))
    }

    /**
     * Returns once the phone holds a bond with the Scout, creating one when [allowPairing].
     *
     * @throws ScoutLinkException [ScoutLinkFailure.BOND_MISSING] without a bond when pairing is not
     * allowed, [ScoutLinkFailure.PAIRING_REJECTED] when pairing fails or is refused, and
     * [ScoutLinkFailure.TIMEOUT] when the prompt goes unanswered for [timeoutMs].
     */
    suspend fun ensureBonded(allowPairing: Boolean, timeoutMs: Long) {
        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                everBonded = true
                return
            }

            BluetoothDevice.BOND_NONE -> {
                if (!allowPairing) throw ScoutLinkException(ScoutLinkFailure.BOND_MISSING, "this phone holds no bond")
            }
        }
        val result = CompletableDeferred<Int>()
        bondResult = result
        try {
            Timber.i("Pairing with the Scout, bond state %d", device.bondState)
            if (device.bondState == BluetoothDevice.BOND_NONE && !device.createBond()) {
                throw ScoutLinkException(ScoutLinkFailure.PAIRING_REJECTED, "createBond was refused")
            }
            val state = withTimeoutOrNull(timeoutMs) { result.await() }
                ?: throw ScoutLinkException(ScoutLinkFailure.TIMEOUT, "pairing was not answered")
            if (state != BluetoothDevice.BOND_BONDED) {
                throw ScoutLinkException(ScoutLinkFailure.PAIRING_REJECTED, "pairing did not finish")
            }
        } finally {
            bondResult = null
        }
    }

    /** @return True when the phone holds a bond with the Scout right now. */
    fun isBonded(): Boolean = device.bondState == BluetoothDevice.BOND_BONDED
}
