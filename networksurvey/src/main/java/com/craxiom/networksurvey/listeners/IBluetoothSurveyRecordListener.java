package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.BluetoothRecord;

import java.util.List;

/**
 * Listener interface for those interested in being notified when a new collection of Bluetooth Survey Records are ready.
 *
 * @since 1.0.0
 */
public interface IBluetoothSurveyRecordListener
{
    /**
     * Called when a new collection of Bluetooth Survey Records are ready.
     *
     * @param bluetoothRecords the list of Bluetooth Records.
     */
    void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords);
}
