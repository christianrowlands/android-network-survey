package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.ResultCode

/** Why a Scout link failed or could not be set up. */
enum class ScoutLinkFailure(
    /** True when reconnecting cannot help until the user acts, so the manager stops retrying. */
    val needsUser: Boolean = false,
) {
    /** A step did not finish in time (out of range, or the pairing prompt was ignored). */
    TIMEOUT,

    /** The link dropped: out of range, the board restarted or lost power. */
    LINK_LOST,

    /**
     * The phone and the Scout no longer share a bond: the Scout was erased or paired with another
     * phone, or Android dropped its half. The user removes it in Android's Bluetooth settings and
     * pairs again (no `removeBond` reflection).
     */
    BOND_MISSING(needsUser = true),

    /**
     * The link dropped with a key failure while the phone holds a bond. One can be stray, so it
     * retries like a dropped link; the link controller reads several in a row as [BOND_MISSING].
     */
    KEY_REJECTED,

    /** Pairing was refused on the phone, or the Scout is not pairable (bonded to another phone). */
    PAIRING_REJECTED(needsUser = true),

    /**
     * The board service or a required characteristic is missing. Pairing fails at once; a paired
     * Scout that shows it (a broken image) is retried with backoff like a dropped link.
     */
    SERVICE_MISSING,

    /** Android reported a GATT error that is not one of the above. */
    GATT_ERROR,

    /** A message from the board did not decode, or a response was not the expected kind. */
    MALFORMED,

    /** The board answered a control request with a result other than OK. */
    REJECTED,

    /** Bluetooth is off or the adapter is unavailable. */
    BLUETOOTH_UNAVAILABLE,

    /** The Nearby devices (BLUETOOTH_CONNECT) permission is missing. */
    PERMISSION_MISSING,

    /** The app closed the link on purpose. */
    CANCELED,
}

/** A failed Scout link step. */
class ScoutLinkException(
    val failure: ScoutLinkFailure,
    message: String,
    cause: Throwable? = null,
    /** The board's answer, for [ScoutLinkFailure.REJECTED]. */
    val boardResult: ResultCode? = null,
) : Exception("$failure: $message", cause)

/** Receives what a [ScoutTransport] hears, on the Scout thread. */
interface ScoutTransportListener {
    /** One notification or indication from [characteristic], still framed. */
    fun onNotification(characteristic: ScoutCharacteristic, value: ByteArray, receivedAtMs: Long)

    /** The link is gone; the transport is closed and must not be used again. */
    fun onLinkLost(failure: ScoutLinkFailure)
}

/**
 * One connection to one Scout, the only part of the link that differs between real hardware
 * (`GattScoutTransport`) and the debug-only fake. Everything above it (framing, control requests,
 * the session handshake) is shared, so the fake exercises the same code as a real board.
 *
 * Every method is called on the Scout thread, one at a time per step, and suspends until Android
 * (or the fake) confirms the step. Failures throw [ScoutLinkException]. A transport is used for one
 * connection: after [close] or [ScoutTransportListener.onLinkLost] the session makes a new one.
 */
interface ScoutTransport {
    /**
     * Connects to the Scout at [address], requests the 517-byte MTU first, then the high
     * connection priority, then discovers the board service.
     *
     * @return The negotiated ATT MTU.
     */
    suspend fun open(address: String, listener: ScoutTransportListener): Int

    /**
     * True once the link to the Scout came up on this transport, even when a later step (the MTU,
     * the Info read, the handshake) failed: the Scout is near and answering, so a failure after it
     * is not "out of range".
     */
    val reachedScout: Boolean get() = false

    /** Reads the Info characteristic, which needs no pairing. */
    suspend fun readInfo(): ByteArray

    /**
     * Makes sure the phone holds a bond with the Scout. With [allowPairing] a missing bond is
     * created explicitly (Android shows its pairing prompt); without it a missing bond fails with
     * [ScoutLinkFailure.BOND_MISSING], so a background reconnect never prompts.
     */
    suspend fun ensureBonded(allowPairing: Boolean)

    /** Turns notifications (or, for Control, indications) of [characteristic] on or off. */
    suspend fun setNotify(characteristic: ScoutCharacteristic, enabled: Boolean)

    /** Writes one encoded `ControlRequest` with response; returns once the ATT write is acknowledged. */
    suspend fun writeControl(request: ByteArray)

    /**
     * Writes one OTA chunk (a 4-byte offset and image bytes) to the OTA characteristic without
     * response; returns once Android has taken the write, so the next one can follow.
     */
    suspend fun writeOta(chunk: ByteArray)

    /** Closes the link. Safe to call more than once; no listener callback follows. */
    fun close()
}
