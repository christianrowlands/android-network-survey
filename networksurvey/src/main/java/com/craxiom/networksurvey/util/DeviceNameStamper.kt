package com.craxiom.networksurvey.util

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.GnssRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.PhoneState
import com.craxiom.messaging.UmtsRecord
import com.craxiom.messaging.WatchlistEntryUpdate
import com.craxiom.messaging.WatchlistMatch
import com.craxiom.messaging.WifiBeaconRecord

/**
 * Sets the device name on outgoing messages, so the streaming destinations (MQTT and gRPC) name
 * the phone the same way. A null or empty name leaves the message unchanged.
 */
object DeviceNameStamper {

    @JvmStatic
    fun stamp(record: GsmRecord, name: String?): GsmRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: CdmaRecord, name: String?): CdmaRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: UmtsRecord, name: String?): UmtsRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: LteRecord, name: String?): LteRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: NrRecord, name: String?): NrRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: WifiBeaconRecord, name: String?): WifiBeaconRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: BluetoothRecord, name: String?): BluetoothRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(record: GnssRecord, name: String?): GnssRecord =
        if (name.isNullOrEmpty()) record else record.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(message: DeviceStatus, name: String?): DeviceStatus =
        if (name.isNullOrEmpty()) message else message.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(message: PhoneState, name: String?): PhoneState =
        if (name.isNullOrEmpty()) message else message.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(message: WatchlistMatch, name: String?): WatchlistMatch =
        if (name.isNullOrEmpty()) message else message.toBuilder().apply { dataBuilder.deviceName = name }.build()

    @JvmStatic
    fun stamp(message: WatchlistEntryUpdate, name: String?): WatchlistEntryUpdate =
        if (name.isNullOrEmpty()) message else message.toBuilder().apply { dataBuilder.deviceName = name }.build()
}
