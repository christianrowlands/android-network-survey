package com.craxiom.networksurvey.scout.link

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.util.size
import com.craxiom.networksurvey.scout.ScoutConstants
import com.craxiom.networksurvey.scout.model.ScoutId
import timber.log.Timber

/** One Scout heard advertising. */
data class ScoutSighting(
    /** The Android address it advertised from. */
    val address: String,
    /** "ABCD" from "NS-ABCD". */
    val shortId: String,
    val rssi: Int,
    val advertisement: ScoutAdvertisement,
    /** Phone elapsed time it was last heard, milliseconds. */
    val lastSeenElapsedMs: Long,
) {
    companion object {
        /**
         * @return A sighting from one advertisement, or null when it carries no Scout
         * manufacturer data. Android merges the scan response (the name) into the advertisement;
         * without a name the short ID comes from [previous] or the address's last two bytes.
         */
        @JvmStatic
        fun from(
            address: String,
            name: String?,
            manufacturerData: Map<Int, ByteArray>,
            rssi: Int,
            nowElapsedMs: Long,
            previous: ScoutSighting? = null,
        ): ScoutSighting? {
            val advertisement = manufacturerData.values.firstNotNullOfOrNull { ScoutAdvertisement.parse(it) } ?: return null
            val shortId = ScoutAdvertisement.shortIdFromName(name) ?: previous?.shortId ?: ScoutId.shortIdFromAddress(address)
            return ScoutSighting(address, shortId, rssi, advertisement, nowElapsedMs)
        }
    }
}

/** Finds nearby Scouts for the pairing sheet; [ScoutScanner] on a phone, a stand-in in tests. */
interface ScoutNearbyScanner {
    /** @return True when the scan started; false without Bluetooth, the permission or a scanner. */
    fun start(): Boolean

    /** Stops the scan and clears the list. */
    fun stop()
}

/**
 * The pairing scan: a low-latency BLE scan filtered by the NS Board Service UUID (no
 * CompanionDeviceManager, whose chooser cannot show the pairing states). Keeps the Scouts heard in
 * the last 10 s and publishes the list, strongest first, on [handler] (the Scout thread).
 *
 * The caller checks BLUETOOTH_SCAN before [start]; the permission lint is suppressed for that.
 */
@SuppressLint("MissingPermission")
internal class ScoutScanner(
    private val context: Context,
    private val handler: Handler,
    private val onSightings: (List<ScoutSighting>) -> Unit,
    private val onFailed: (Int) -> Unit,
) : ScoutNearbyScanner {
    private val sightings = LinkedHashMap<String, ScoutSighting>()
    private var scanning = false

    private val prune = object : Runnable {
        override fun run() {
            if (!scanning) return
            val cutoff = SystemClock.elapsedRealtime() - ScoutConstants.NEARBY_EXPIRY_MS
            sightings.values.removeAll { it.lastSeenElapsedMs < cutoff }
            publish()
            handler.postDelayed(this, ScoutConstants.NEARBY_PUBLISH_INTERVAL_MS)
        }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { onResult(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            handler.post { results.forEach(::onResult) }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.w("The Scout scan failed with error %d", errorCode)
            handler.post {
                scanning = false
                onFailed(errorCode)
            }
        }
    }

    override fun start(): Boolean {
        if (scanning) return true
        if (!ScoutBluetooth.hasScanPermission(context) || !ScoutBluetooth.isEnabled(context)) return false
        val scanner = ScoutBluetooth.adapter(context)?.bluetoothLeScanner ?: return false
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(ScoutGatt.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        sightings.clear()
        scanner.startScan(listOf(filter), settings, callback)
        Timber.i("Scout scan started")
        scanning = true
        handler.postDelayed(prune, ScoutConstants.NEARBY_PUBLISH_INTERVAL_MS)
        publish()
        return true
    }

    override fun stop() {
        if (!scanning) return
        scanning = false
        handler.removeCallbacks(prune)
        runCatching { ScoutBluetooth.adapter(context)?.bluetoothLeScanner?.stopScan(callback) }
        sightings.clear()
        publish()
    }

    private fun onResult(result: ScanResult) {
        if (!scanning) return
        val record = result.scanRecord ?: return
        val data = record.manufacturerSpecificData ?: return
        val manufacturer = (0 until data.size).associate { data.keyAt(it) to data.valueAt(it) }
        val address = result.device.address
        val sighting = ScoutSighting.from(
            address, record.deviceName, manufacturer, result.rssi, SystemClock.elapsedRealtime(), sightings[address],
        ) ?: return
        val previous = sightings.put(address, sighting)
        if (previous == null || previous.advertisement != sighting.advertisement || previous.shortId != sighting.shortId) {
            publish()
        }
    }

    private fun publish() {
        onSightings(sightings.values.sortedWith(compareByDescending<ScoutSighting> { it.rssi }.thenBy { it.shortId }))
    }
}
