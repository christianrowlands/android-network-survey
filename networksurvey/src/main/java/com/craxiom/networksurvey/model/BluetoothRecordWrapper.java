package com.craxiom.networksurvey.model;

import android.util.SparseArray;

import com.craxiom.messaging.BluetoothRecord;

import java.io.Serializable;

/**
 * Wraps the {@link BluetoothRecord} so that we can include the
 * manufacturer data from the BLE advertisement. This allows us to detect specific device types like
 * Apple AirTags in the UI layer.
 */
public record BluetoothRecordWrapper(BluetoothRecord bluetoothRecord,
                                     SparseArray<byte[]> manufacturerData) implements Serializable
{
    /**
     * @param bluetoothRecord  The protobuf defined Bluetooth record object.
     * @param manufacturerData The manufacturer specific data from {@link android.bluetooth.le.ScanRecord#getManufacturerSpecificData()}
     */
    public BluetoothRecordWrapper
    {
    }

    /**
     * Checks if this device is an Apple AirTag based on manufacturer data.
     * AirTags are identified by Apple's manufacturer ID (0x004C) with specific data prefixes:
     * - 0x12: Registered AirTag
     * - 0x07: Unregistered AirTag
     *
     * @return true if this device appears to be an Apple AirTag, false otherwise
     */
    public boolean isAppleAirTag()
    {
        if (manufacturerData == null || manufacturerData.size() == 0) return false;

        for (int i = 0; i < manufacturerData.size(); i++)
        {
            int manufacturerId = manufacturerData.keyAt(i);
            if (manufacturerId == 0x004C) // Apple's manufacturer ID
            {
                byte[] data = manufacturerData.valueAt(i);
                if (data != null && data.length > 0)
                {
                    // Check for AirTag data prefixes
                    return data[0] == 0x12 || data[0] == 0x07;
                }
            }
        }
        return false;
    }
}