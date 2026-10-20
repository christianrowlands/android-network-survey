package com.craxiom.networksurvey.ui.bluetooth.list.model

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.bluetooth.SupportedTechnologies
import com.craxiom.messaging.bluetooth.Technology
import com.google.protobuf.FloatValue

/** Builds Bluetooth records and display rows for the Bluetooth list tests. */
object BluetoothRecords {
    /** A record as the LE scanner builds it: a name, company and manufacturer data. */
    fun le(
        address: String,
        rssi: Float? = -60f,
        name: String = "",
        companyId: String = "",
        mfgData: String = "",
        uuids: List<String> = emptyList(),
        supported: SupportedTechnologies = SupportedTechnologies.LE,
    ): BluetoothRecord = record(address, rssi, name, companyId, mfgData, uuids, supported, Technology.LE)

    /** A record from Classic discovery: no scan record, so no company or manufacturer data. */
    fun classic(
        address: String,
        rssi: Float? = -70f,
        name: String = "",
        supported: SupportedTechnologies = SupportedTechnologies.UNKNOWN,
    ): BluetoothRecord = record(address, rssi, name, "", "", emptyList(), supported, null)

    private fun record(
        address: String,
        rssi: Float?,
        name: String,
        companyId: String,
        mfgData: String,
        uuids: List<String>,
        supported: SupportedTechnologies,
        technology: Technology?,
    ): BluetoothRecord {
        val data = BluetoothRecordData.newBuilder()
            .setSourceAddress(address)
            .setOtaDeviceName(name)
            .setCompanyId(companyId)
            .setMfgData(mfgData)
            .addAllServiceUuids(uuids)
            .setSupportedTechnologies(supported)
        rssi?.let { data.setSignalStrength(FloatValue.of(it)) }
        technology?.let { data.setTechnology(it) }
        return BluetoothRecord.newBuilder().setData(data).build()
    }

    /** A display row with only what a test cares about set. */
    fun row(
        address: String,
        rssi: Int? = -60,
        name: String = "",
        company: String = "",
        tech: BluetoothTech = BluetoothTech.LE,
        tracker: FindMyDevice? = null,
    ) = BluetoothDeviceDisplay(address, name, rssi, company, tech, tracker)
}
