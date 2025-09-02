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
     * Called when a new Bluetooth survey record is ready.
     *
     * @param bluetoothRecord the Bluetooth record.
     */
    void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord);

    /**
     * Called when a new collection of Bluetooth survey records are ready.
     *
     * @param bluetoothRecords the list of Bluetooth records.
     */
    void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords);
}
