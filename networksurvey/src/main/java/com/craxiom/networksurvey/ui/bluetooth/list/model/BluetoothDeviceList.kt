package com.craxiom.networksurvey.ui.bluetooth.list.model

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.bluetooth.Technology

/** The sort options, by their index in `R.array.bluetooth_sort_options`. */
object BluetoothSort {
    const val SIGNAL = 0
    const val ADDRESS = 1
    const val NAME = 2
    const val TECHNOLOGY = 3
}

/**
 * The Bluetooth list's pure logic: folding records into devices, dropping devices no longer heard,
 * and ordering rows.
 */
object BluetoothDeviceList {

    /**
     * Folds [records] into [devices], keyed by address, field by field: the signal and the time seen
     * always take the newest record, while the name, UUIDs, company, technology and Find My kind keep
     * the last record that carried them. Records from Classic discovery carry no scan record (no
     * company or manufacturer data), and Android reports LE devices through that path too, so a
     * whole-record replace would wipe what the LE scanner learned.
     *
     * @param now The receipt time, which is what ages a device out.
     */
    fun merge(
        devices: Map<String, BluetoothDeviceState>,
        records: List<BluetoothRecord>,
        now: Long,
    ): Map<String, BluetoothDeviceState> {
        if (records.isEmpty()) return devices
        val merged = devices.toMutableMap()
        for (record in records) {
            val data = record.data
            val address = data.sourceAddress
            if (address.isBlank()) continue
            val old = merged[address]
            val rssi = if (data.hasSignalStrength()) data.signalStrength.value.toInt() else old?.rssi
            val hasManufacturerData = data.companyId.isNotBlank() && data.mfgData.isNotBlank()
            val fromLeScanner = data.technology == Technology.LE
            val tech = BluetoothTech.from(data.supportedTechnologies)
            merged[address] = BluetoothDeviceState(
                address = address,
                name = data.otaDeviceName.ifBlank { old?.name.orEmpty() },
                rssi = rssi,
                serviceUuids = data.serviceUuidsList.ifEmpty { old?.serviceUuids.orEmpty() },
                companyId = data.companyId.ifBlank { old?.companyId.orEmpty() },
                tech = widen(old?.tech, tech),
                findMy = if (hasManufacturerData) FindMy.device(data.companyId, data.mfgData) else old?.findMy,
                lastSeenMillis = now,
                detailsRecord = if (old == null || fromLeScanner || old.detailsRecord.technology != Technology.LE) {
                    data
                } else {
                    old.detailsRecord
                },
            )
        }
        return merged
    }

    /**
     * @return What a device supports once [reported] is heard after [known]. Support only widens: the
     * LE scanner reports a dual-mode device as Dual, while Classic discovery reports the stack's
     * cached BR/EDR type for it, and LE plus BR/EDR is Dual.
     */
    private fun widen(known: BluetoothTech?, reported: BluetoothTech): BluetoothTech = when {
        known == null || known == BluetoothTech.UNKNOWN -> reported
        reported == BluetoothTech.UNKNOWN || reported == known -> known
        else -> BluetoothTech.DUAL
    }

    /** @return [devices] without those not heard from for [retentionMs] before [now]. */
    fun pruneStale(
        devices: Map<String, BluetoothDeviceState>,
        now: Long,
        retentionMs: Long,
    ): Map<String, BluetoothDeviceState> {
        val cutoff = now - retentionMs
        if (devices.values.none { it.lastSeenMillis < cutoff }) return devices
        return devices.filterValues { it.lastSeenMillis >= cutoff }
    }

    /**
     * @return [rows] in the order of [sortIndex] (see [BluetoothSort]). Rows that lack the sorted
     * value (no signal, no name, unknown technology) go last, and ties fall back to the address so
     * equal rows do not swap places between updates.
     */
    fun sorted(rows: List<BluetoothDeviceDisplay>, sortIndex: Int): List<BluetoothDeviceDisplay> =
        rows.sortedWith(comparatorFor(sortIndex).thenBy { it.address })

    private fun comparatorFor(sortIndex: Int): Comparator<BluetoothDeviceDisplay> = when (sortIndex) {
        BluetoothSort.ADDRESS -> compareBy { it.address }
        BluetoothSort.NAME -> compareBy<BluetoothDeviceDisplay> { !it.hasName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        BluetoothSort.TECHNOLOGY -> compareBy { it.tech.ordinal }
        else -> compareBy<BluetoothDeviceDisplay> { it.rssi == null }.thenByDescending { it.rssi }
    }
}
