package com.craxiom.networksurvey.scout.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * [ScoutTransport] over Android's BluetoothGatt, in the order Android needs: connect over LE,
 * `requestMtu(517)` first, after a short settle ([ScoutMtu]; the board never sends Records below MTU 185 and drops Status and
 * Control messages that do not fit), `requestConnectionPriority(HIGH)`, service discovery, the
 * unencrypted Info read, an explicit bond, then the CCCD writes the session asks for.
 *
 * Android allows one GATT operation at a time, so every operation goes through [runOperation],
 * which waits for its callback. GATT callbacks run on [handler] (the Scout thread, through the
 * `connectGatt` handler overload), the same thread the session's coroutines run on.
 *
 * The caller checks BLUETOOTH_CONNECT before [open]; the permission lint is suppressed for that.
 */
@SuppressLint("MissingPermission")
internal class GattScoutTransport(
    private val context: Context,
    private val handler: Handler,
) : ScoutTransport {

    private enum class Operation { MTU, DISCOVER, READ, WRITE_DESCRIPTOR, WRITE_CHARACTERISTIC }

    private class Pending(val operation: Operation, val result: CompletableDeferred<OperationResult>)

    private class OperationResult(val status: Int, val value: ByteArray? = null, val mtu: Int = 0)

    private val operationLock = Mutex()
    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var bondWatcher: ScoutBondWatcher? = null
    private var listener: ScoutTransportListener? = null
    private var connected: CompletableDeferred<Unit>? = null
    private var pending: Pending? = null
    private var closed = false

    /** Why the link ended, so a step that starts after the drop reports the real reason. */
    private var endedBy: ScoutLinkFailure? = null
    override var reachedScout = false
        private set
    private val characteristics = HashMap<ScoutCharacteristic, BluetoothGattCharacteristic>()

    override suspend fun open(address: String, listener: ScoutTransportListener): Int {
        if (!ScoutBluetooth.hasConnectPermission(context)) {
            throw ScoutLinkException(ScoutLinkFailure.PERMISSION_MISSING, "BLUETOOTH_CONNECT not granted")
        }
        val adapter = ScoutBluetooth.adapter(context)
        if (adapter == null || !adapter.isEnabled) throw ScoutLinkException(ScoutLinkFailure.BLUETOOTH_UNAVAILABLE, "off")
        val remote = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw ScoutLinkException(ScoutLinkFailure.GATT_ERROR, "bad address $address", e)
        }
        // A transport serves one link; never let a second client registration open beside the first.
        check(gatt == null && !closed) { "A GattScoutTransport opens once" }
        this.listener = listener
        device = remote
        bondWatcher = ScoutBondWatcher(context, handler, remote) { fail(ScoutLinkFailure.BOND_MISSING) }.also { it.start() }

        val connection = CompletableDeferred<Unit>()
        connected = connection
        // The board asks for the 2M PHY once connected; allow it from the start.
        val phy = BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_2M_MASK
        gatt = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE, phy, handler)
            ?: throw ScoutLinkException(ScoutLinkFailure.GATT_ERROR, "connectGatt returned null")
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connection.await() }
            ?: throw ScoutLinkException(ScoutLinkFailure.TIMEOUT, "connect timed out")
        connected = null
        reachedScout = true

        val mtu = requestMtu()
        gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

        val discovery = runOperation(Operation.DISCOVER, STEP_TIMEOUT_MS) { it.discoverServices() }
        requireSuccess(discovery, Operation.DISCOVER)
        findCharacteristics()
        return mtu
    }

    /** Asks for the 517-byte MTU once the connect-time traffic went by ([ScoutMtu]). */
    private suspend fun requestMtu(): Int {
        delay(ScoutMtu.SETTLE_MS)
        return ScoutMtu.negotiate(exchange = { exchangeMtu()?.let { ScoutMtu.Answer(it.status, it.mtu) } }, hold = { delay(it) })
    }

    /** @return The MTU exchange's result, or null when it could not start or timed out. */
    private suspend fun exchangeMtu(): OperationResult? = try {
        runOperation(Operation.MTU, MTU_TIMEOUT_MS) { it.requestMtu(ScoutGatt.PREFERRED_MTU) }
    } catch (e: ScoutLinkException) {
        if (e.failure != ScoutLinkFailure.GATT_ERROR && e.failure != ScoutLinkFailure.TIMEOUT) throw e
        Timber.w("The MTU request failed (%s); continuing", e.failure)
        null
    }

    override suspend fun readInfo(): ByteArray {
        val info = characteristic(ScoutCharacteristic.INFO)
        val result = runOperation(Operation.READ, STEP_TIMEOUT_MS) { it.readCharacteristic(info) }
        requireSuccess(result, Operation.READ)
        return result.value ?: ByteArray(0)
    }

    override suspend fun ensureBonded(allowPairing: Boolean) {
        val watcher = bondWatcher ?: throw ScoutLinkException(ScoutLinkFailure.LINK_LOST, "not connected")
        watcher.ensureBonded(allowPairing, PAIRING_TIMEOUT_MS)
    }

    override suspend fun setNotify(characteristic: ScoutCharacteristic, enabled: Boolean) {
        val target = characteristic(characteristic)
        val descriptor = target.getDescriptor(ScoutGatt.CCCD_UUID)
            ?: throw ScoutLinkException(ScoutLinkFailure.SERVICE_MISSING, "$characteristic has no CCCD")
        val value = when {
            !enabled -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            characteristic.indicates -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        // Control is the first encrypted access, which can still wait on the link encrypting.
        val timeout = if (characteristic == ScoutCharacteristic.CONTROL) PAIRING_TIMEOUT_MS else STEP_TIMEOUT_MS
        val result = runOperation(Operation.WRITE_DESCRIPTOR, timeout) { gatt ->
            if (!gatt.setCharacteristicNotification(target, enabled)) return@runOperation false
            writeDescriptor(gatt, descriptor, value)
        }
        requireSuccess(result, Operation.WRITE_DESCRIPTOR)
    }

    override suspend fun writeControl(request: ByteArray) {
        val target = characteristic(ScoutCharacteristic.CONTROL)
        val result = runOperation(Operation.WRITE_CHARACTERISTIC, STEP_TIMEOUT_MS) {
            writeCharacteristic(it, target, request, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
        requireSuccess(result, Operation.WRITE_CHARACTERISTIC)
    }

    override suspend fun writeOta(chunk: ByteArray) {
        val target = characteristic(ScoutCharacteristic.OTA)
        val result = runOperation(Operation.WRITE_CHARACTERISTIC, STEP_TIMEOUT_MS) {
            writeCharacteristic(it, target, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        }
        requireSuccess(result, Operation.WRITE_CHARACTERISTIC)
    }

    override fun close() {
        if (closed) return
        closed = true
        bondWatcher?.stop()
        val cancel = ScoutLinkException(ScoutLinkFailure.CANCELED, "closed")
        connected?.completeExceptionally(cancel)
        pending?.result?.completeExceptionally(cancel)
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        listener = null
    }

    /** Ends the link because of [failure] and tells the listener once. */
    private fun fail(failure: ScoutLinkFailure) {
        if (closed) return
        endedBy = failure
        val owner = listener
        val error = ScoutLinkException(failure, "link ended")
        connected?.completeExceptionally(error)
        pending?.result?.completeExceptionally(error)
        close()
        owner?.onLinkLost(failure)
    }

    private suspend fun runOperation(
        operation: Operation,
        timeoutMs: Long,
        start: (BluetoothGatt) -> Boolean,
    ): OperationResult = operationLock.withLock {
        val current = gatt
        if (closed || current == null) throw ScoutLinkException(endedBy ?: ScoutLinkFailure.LINK_LOST, "not connected")
        val result = CompletableDeferred<OperationResult>()
        pending = Pending(operation, result)
        try {
            val started = try {
                start(current)
            } catch (e: IllegalArgumentException) {
                // Android refuses some requests by throwing (a value over 512 bytes); never let that escape the link
                throw ScoutLinkException(ScoutLinkFailure.GATT_ERROR, "$operation refused: ${e.message}", e)
            }
            if (!started) throw ScoutLinkException(ScoutLinkFailure.GATT_ERROR, "$operation did not start")
            withTimeoutOrNull(timeoutMs) { result.await() }
                ?: throw ScoutLinkException(ScoutLinkFailure.TIMEOUT, "$operation timed out")
        } finally {
            pending = null
        }
    }

    private fun complete(operation: Operation, result: OperationResult) {
        val current = pending ?: return
        if (current.operation == operation) current.result.complete(result)
    }

    private fun requireSuccess(result: OperationResult, operation: Operation) {
        if (result.status == BluetoothGatt.GATT_SUCCESS) return
        val failure = GattStatus.operationFailure(result.status, bondWatcher?.isBonded() == true)
        throw ScoutLinkException(failure, "$operation failed with status ${result.status}")
    }

    private fun findCharacteristics() {
        val service = gatt?.getService(ScoutGatt.SERVICE_UUID)
            ?: throw ScoutLinkException(ScoutLinkFailure.SERVICE_MISSING, "no NS Board Service")
        characteristics.clear()
        ScoutCharacteristic.entries.forEach { kind -> service.getCharacteristic(kind.uuid)?.let { characteristics[kind] = it } }
        val missing = REQUIRED_CHARACTERISTICS - characteristics.keys
        if (missing.isNotEmpty()) throw ScoutLinkException(ScoutLinkFailure.SERVICE_MISSING, "missing $missing")
    }

    private fun characteristic(kind: ScoutCharacteristic): BluetoothGattCharacteristic =
        characteristics[kind] ?: throw ScoutLinkException(ScoutLinkFailure.SERVICE_MISSING, "no $kind characteristic")

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(gatt: BluetoothGatt, target: BluetoothGattCharacteristic, value: ByteArray, writeType: Int): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(target, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            target.writeType = writeType
            target.value = value
            gatt.writeCharacteristic(target)
        }

    private fun deliver(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val kind = ScoutCharacteristic.fromUuid(characteristic.uuid) ?: return
        listener?.onNotification(kind, value.copyOf(), System.currentTimeMillis())
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (closed) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected?.complete(Unit)
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                Timber.i("Scout link ended, status %d", status)
                fail(GattStatus.disconnectFailure(status, bondWatcher?.isBonded() == true))
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) =
            complete(Operation.MTU, OperationResult(status, mtu = mtu))

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) =
            complete(Operation.DISCOVER, OperationResult(status))

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            complete(Operation.READ, OperationResult(status, value.copyOf()))

        @Deprecated("Called before Android 13 only")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            complete(Operation.READ, OperationResult(status, characteristic.value?.copyOf()))

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            complete(Operation.WRITE_CHARACTERISTIC, OperationResult(status))

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
            complete(Operation.WRITE_DESCRIPTOR, OperationResult(status))

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
            deliver(characteristic, value)

        @Deprecated("Called before Android 13 only")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { deliver(characteristic, it) }
        }
    }

    private companion object {
        /** A direct LE connect normally completes in a second or two; Android gives up after about 30 s. */
        const val CONNECT_TIMEOUT_MS = 15_000L

        /** The MTU exchange is quick; a stack that ignores the request should not hold up the link. */
        const val MTU_TIMEOUT_MS = 3_000L

        /** Service discovery, reads and CCCD writes. */
        const val STEP_TIMEOUT_MS = 10_000L

        /** Long enough for the user to answer Android's pairing prompt. */
        const val PAIRING_TIMEOUT_MS = 60_000L

        /** Raw and OTA are optional (third-party boards may leave them out). */
        val REQUIRED_CHARACTERISTICS = setOf(
            ScoutCharacteristic.INFO, ScoutCharacteristic.CONTROL, ScoutCharacteristic.STATUS, ScoutCharacteristic.RECORDS,
        )
    }
}
