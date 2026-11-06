package com.craxiom.networksurvey.scout.link

import android.bluetooth.BluetoothGatt
import timber.log.Timber

/**
 * How the link settles its ATT MTU, around a collision seen on a Pixel 6 (Android 16).
 *
 * Android sends every bonded device a Service Changed indication on connect once its own GATT
 * database changed (another app added or removed a GATT service, as Google Play services does
 * when Bluetooth restarts). The board's NimBLE host has no GATT client, so it answers the
 * indication with an ATT Error Response instead of a confirmation, and Android takes that error
 * as the answer to whatever request is outstanding. When that is the MTU request, the board's
 * real MTU answer arrives late and is ignored: the board sends PDUs for MTU 517 while the phone
 * stays at 23, every read longer than 20 bytes fails with status 133, and Android refuses another
 * MTU exchange on that connection. It keeps sending the indication on every connection until one
 * stays up 30 s (then it assumes a server-only device and stops), so the Scout never reconnects.
 *
 * So the MTU request waits [SETTLE_MS] after connecting, until the board's answer to the
 * indication has gone by (about 110 ms in the captures), and a link that still gets an error
 * answer is held for [SERVICE_CHANGED_TIMEOUT_MS] before it fails, so the next attempt is clean.
 */
internal object ScoutMtu {
    /** The ATT MTU before any exchange. */
    const val DEFAULT_ATT_MTU = 23

    /** The wait between connecting and the MTU request. */
    const val SETTLE_MS = 1_000L

    /** Longer than the 30 s Android waits for a Service Changed confirmation. */
    const val SERVICE_CHANGED_TIMEOUT_MS = 31_000L

    /** One exchange's answer: Android's status and the MTU it reports. */
    class Answer(val status: Int, val mtu: Int)

    /**
     * @param exchange Runs the MTU exchange; null when it could not start or timed out.
     * @param hold Waits the given milliseconds with the link up.
     * @return The MTU to use.
     * @throws ScoutLinkException When the exchange was answered with an error: this connection
     * stays at MTU 23 whatever the board uses, so it cannot carry the link.
     */
    suspend fun negotiate(exchange: suspend () -> Answer?, hold: suspend (Long) -> Unit): Int {
        val answer = exchange() ?: return DEFAULT_ATT_MTU
        if (answer.status == BluetoothGatt.GATT_SUCCESS) return answer.mtu
        Timber.w("The MTU request was answered with status %d; holding the link so Android stops resending", answer.status)
        hold(SERVICE_CHANGED_TIMEOUT_MS)
        throw ScoutLinkException(ScoutLinkFailure.GATT_ERROR, "MTU answered with status ${answer.status}")
    }
}
