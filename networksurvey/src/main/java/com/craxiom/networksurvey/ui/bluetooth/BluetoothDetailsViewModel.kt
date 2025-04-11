package com.craxiom.networksurvey.ui.bluetooth


import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.ui.ASignalChartViewModel

const val MAX_BLUETOOTH_RSSI = -20f
const val MIN_BLUETOOTH_RSSI = -100f

/**
 * The view model for the Bluetooth Details screen.
 */
internal class BluetoothDetailsViewModel() : ASignalChartViewModel() {

    lateinit var bluetoothData: BluetoothRecordData

    init {
        setMaxRssi(MAX_BLUETOOTH_RSSI)
        setMinRssi(MIN_BLUETOOTH_RSSI)
    }
}
