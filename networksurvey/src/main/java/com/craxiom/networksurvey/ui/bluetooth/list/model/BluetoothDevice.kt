package com.craxiom.networksurvey.ui.bluetooth.list.model

import androidx.compose.runtime.Immutable
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.bluetooth.SupportedTechnologies

/** The Bluetooth radio technologies a device supports, in the order the list sorts them. */
enum class BluetoothTech {
    LE,
    CLASSIC,
    DUAL,
    UNKNOWN;

    companion object {
        /** @return The technology a record reports, or [UNKNOWN] when it does not say. */
        fun from(supported: SupportedTechnologies): BluetoothTech = when (supported) {
            SupportedTechnologies.LE -> LE
            SupportedTechnologies.BR_EDR -> CLASSIC
            SupportedTechnologies.DUAL -> DUAL
            else -> UNKNOWN
        }
    }
}

/**
 * What the list knows about one device, merged across every record heard from it.
 *
 * @property name The broadcast name; blank when the device has not sent one.
 * @property findMy What sent the last Find My advertisement heard; null when none was.
 * @property lastSeenMillis When the list last received a record for it (receipt time).
 * @property detailsRecord The record the details screen opens with: the latest one from the LE
 *                         scanner, which carries the company and UUIDs, else the latest of any kind.
 */
data class BluetoothDeviceState(
    val address: String,
    val name: String,
    val rssi: Int?,
    val serviceUuids: List<String>,
    val companyId: String,
    val tech: BluetoothTech,
    val findMy: FindMyDevice?,
    val lastSeenMillis: Long,
    val detailsRecord: BluetoothRecordData,
)

/**
 * One row of the Bluetooth list.
 *
 * @property name The broadcast name; blank when the device has not sent one.
 * @property companyName The company from the UUIDs or the company ID; blank when unknown.
 * @property tracker The kind of Find My tracker it is, for its tag; null when it is not one.
 */
@Immutable
data class BluetoothDeviceDisplay(
    val address: String,
    val name: String,
    val rssi: Int?,
    val companyName: String,
    val tech: BluetoothTech,
    val tracker: FindMyDevice?,
) {
    /** True when the device broadcast a name. */
    val hasName: Boolean get() = name.isNotBlank()
}
