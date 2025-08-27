package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.model.BluetoothRecordWrapper;

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
     * @param bluetoothRecordWrapper the Bluetooth record wrapper containing the record and manufacturer data.
     */
    void onBluetoothSurveyRecord(BluetoothRecordWrapper bluetoothRecordWrapper);

    /**
     * Called when a new collection of Bluetooth survey records are ready.
     *
     * @param bluetoothRecordWrappers the list of Bluetooth record wrappers.
     */
    void onBluetoothSurveyRecords(List<BluetoothRecordWrapper> bluetoothRecordWrappers);
}
