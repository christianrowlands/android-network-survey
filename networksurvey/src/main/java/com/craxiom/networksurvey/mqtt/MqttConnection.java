package com.craxiom.networksurvey.mqtt;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.PhoneState;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.mqttlibrary.connection.DefaultMqttConnection;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;

import java.util.List;

/**
 * Class for creating a connection to an MQTT server.
 *
 * @since 0.1.1
 */
public class MqttConnection extends DefaultMqttConnection implements ICellularSurveyRecordListener, IWifiSurveyRecordListener,
        IBluetoothSurveyRecordListener, IGnssSurveyRecordListener, IDeviceStatusListener
{
    private static final String MQTT_GSM_MESSAGE_TOPIC = "gsm_message";
    private static final String MQTT_CDMA_MESSAGE_TOPIC = "cdma_message";
    private static final String MQTT_UMTS_MESSAGE_TOPIC = "umts_message";
    private static final String MQTT_LTE_MESSAGE_TOPIC = "lte_message";
    private static final String MQTT_NR_MESSAGE_TOPIC = "nr_message";
    private static final String MQTT_WIFI_BEACON_MESSAGE_TOPIC = "80211_beacon_message";
    private static final String MQTT_BLUETOOTH_MESSAGE_TOPIC = "bluetooth_message";
    private static final String MQTT_GNSS_MESSAGE_TOPIC = "gnss_message";
    private static final String MQTT_DEVICE_STATUS_MESSAGE_TOPIC = "device_status_message";

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        // Set the device name to the user entered value in the MQTT connection UI (or the value provided via MDM)
        if (mqttClientId != null)
        {
            final GsmRecord.Builder recordBuilder = gsmRecord.toBuilder();
            gsmRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_GSM_MESSAGE_TOPIC, gsmRecord);
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        // Set the device name to the user entered value in the MQTT connection UI (or the value provided via MDM)
        if (mqttClientId != null)
        {
            final CdmaRecord.Builder recordBuilder = cdmaRecord.toBuilder();
            cdmaRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_CDMA_MESSAGE_TOPIC, cdmaRecord);
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        // Set the device name to the user entered value in the MQTT connection UI (or the value provided via MDM)
        if (mqttClientId != null)
        {
            final UmtsRecord.Builder recordBuilder = umtsRecord.toBuilder();
            umtsRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_UMTS_MESSAGE_TOPIC, umtsRecord);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        // Set the device name to the user entered value in the MQTT connection UI (or the value provided via MDM)
        if (mqttClientId != null)
        {
            final LteRecord.Builder recordBuilder = lteRecord.toBuilder();
            lteRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_LTE_MESSAGE_TOPIC, lteRecord);
    }

    @Override
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
        if (mqttClientId != null)
        {
            final NrRecord.Builder recordBuilder = nrRecord.toBuilder();
            nrRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_NR_MESSAGE_TOPIC, nrRecord);
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        wifiBeaconRecords.forEach(wifiRecord -> {
            WifiBeaconRecord wifiBeaconRecord = wifiRecord.getWifiBeaconRecord();
            if (mqttClientId != null)
            {
                final WifiBeaconRecord.Builder recordBuilder = wifiBeaconRecord.toBuilder();
                wifiBeaconRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
            }
            publishMessage(MQTT_WIFI_BEACON_MESSAGE_TOPIC, wifiBeaconRecord);
        });
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        // Set the device name to the user entered value in the MQTT connection UI (or the value provided via MDM)
        if (mqttClientId != null)
        {
            final BluetoothRecord.Builder recordBuilder = bluetoothRecord.toBuilder();
            bluetoothRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_BLUETOOTH_MESSAGE_TOPIC, bluetoothRecord);
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        bluetoothRecords.forEach(bluetoothRecord -> {
            if (mqttClientId != null)
            {
                final BluetoothRecord.Builder recordBuilder = bluetoothRecord.toBuilder();
                bluetoothRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
            }
            publishMessage(MQTT_BLUETOOTH_MESSAGE_TOPIC, bluetoothRecord);
        });
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        if (mqttClientId != null)
        {
            final GnssRecord.Builder gnssRecordBuilder = gnssRecord.toBuilder();
            gnssRecord = gnssRecordBuilder.setData(gnssRecordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_GNSS_MESSAGE_TOPIC, gnssRecord);
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (mqttClientId != null)
        {
            final DeviceStatus.Builder deviceStatusBuilder = deviceStatus.toBuilder();
            deviceStatus = deviceStatusBuilder.setData(deviceStatusBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_DEVICE_STATUS_MESSAGE_TOPIC, deviceStatus);
    }

    @Override
    public void onPhoneState(PhoneState phoneState)
    {
        if (mqttClientId != null)
        {
            final PhoneState.Builder messageBuilder = phoneState.toBuilder();
            phoneState = messageBuilder.setData(messageBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_DEVICE_STATUS_MESSAGE_TOPIC, phoneState);
    }
}