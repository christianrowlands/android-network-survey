package com.craxiom.networksurvey.ui.bluetooth


import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.ui.AChartDetailsViewModel

const val MAX_BLUETOOTH_RSSI = -20f
const val MIN_BLUETOOTH_RSSI = -100f

/**
 * The view model for the Bluetooth Details screen.
 */
internal class BluetoothDetailsViewModel : AChartDetailsViewModel() {

    lateinit var bluetoothData: BluetoothRecordData

    override fun getMaxRssi(): Float {
        return MAX_BLUETOOTH_RSSI
    }

    override fun getMinRssi(): Float {
        return MIN_BLUETOOTH_RSSI
    }
}