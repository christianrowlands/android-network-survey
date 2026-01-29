package com.craxiom.networksurvey.mqtt;

import android.content.Context;

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
import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.mqttlibrary.connection.DefaultMqttConnection;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * Class for creating a connection to an MQTT server.
 *
 * @since 0.1.1
 */
public class MqttConnection extends DefaultMqttConnection implements ICellularSurveyRecordListener, IWifiSurveyRecordListener,
        IBluetoothSurveyRecordListener, IGnssSurveyRecordListener, IDeviceStatusListener
{
    /**
     * When true, incoming survey records will be dropped instead of being queued for MQTT publishing.
     * This is used when the MQTT queue is full but other outputs (file logging, gRPC, etc.) are active
     * and should continue receiving data. This is the single source of truth for drop mode state.
     */
    private final AtomicBoolean dropMessages = new AtomicBoolean(false);

    private String effectiveDeviceName;
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
    public void connect(Context context, BrokerConnectionInfo brokerConnectionInfo)
    {
        super.connect(context, brokerConnectionInfo);

        // Extract device name from MqttConnectionInfo if available and compute effective device name once
        String deviceName = null;
        if (brokerConnectionInfo instanceof MqttConnectionInfo)
        {
            deviceName = ((MqttConnectionInfo) brokerConnectionInfo).getDeviceName();
        }

        // Determine effective device name once during connection
        if (deviceName != null && !deviceName.isEmpty())
        {
            effectiveDeviceName = deviceName;
        } else
        {
            effectiveDeviceName = mqttClientId;
        }
    }

    /**
     * Sets whether MQTT messages should be dropped instead of queued.
     * This is used when the MQTT queue is full but other data outputs (file logging, gRPC, etc.)
     * are active and should continue receiving data.
     *
     * @param drop true to drop messages, false to resume normal operation
     */
    public void setDropMessages(boolean drop)
    {
        dropMessages.set(drop);
        if (drop)
        {
            Timber.d("MQTT drop mode enabled - messages will be dropped");
        } else
        {
            Timber.d("MQTT drop mode disabled - messages will be queued normally");
        }
    }

    /**
     * Atomically sets the drop mode state and returns the previous value.
     * This is used by the service to coordinate state transitions.
     *
     * @param drop the new drop mode state
     * @return the previous drop mode state
     */
    public boolean getAndSetDropMessages(boolean drop)
    {
        boolean wasDropping = dropMessages.getAndSet(drop);
        if (drop && !wasDropping)
        {
            Timber.d("MQTT drop mode enabled - messages will be dropped");
        } else if (!drop && wasDropping)
        {
            Timber.d("MQTT drop mode disabled - messages will be queued normally");
        }
        return wasDropping;
    }

    /**
     * @return true if messages are currently being dropped due to queue backpressure
     */
    public boolean isDropping()
    {
        return dropMessages.get();
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        if (dropMessages.get()) return;

        // Set the device name using the pre-computed effective device name
        if (effectiveDeviceName != null)
        {
            final GsmRecord.Builder recordBuilder = gsmRecord.toBuilder();
            gsmRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_GSM_MESSAGE_TOPIC, gsmRecord);
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        if (dropMessages.get()) return;

        // Set the device name using the pre-computed effective device name
        if (effectiveDeviceName != null)
        {
            final CdmaRecord.Builder recordBuilder = cdmaRecord.toBuilder();
            cdmaRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_CDMA_MESSAGE_TOPIC, cdmaRecord);
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        if (dropMessages.get()) return;

        // Set the device name using the pre-computed effective device name
        if (effectiveDeviceName != null)
        {
            final UmtsRecord.Builder recordBuilder = umtsRecord.toBuilder();
            umtsRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_UMTS_MESSAGE_TOPIC, umtsRecord);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        if (dropMessages.get()) return;

        // Set the device name using the pre-computed effective device name
        if (effectiveDeviceName != null)
        {
            final LteRecord.Builder recordBuilder = lteRecord.toBuilder();
            lteRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_LTE_MESSAGE_TOPIC, lteRecord);
    }

    @Override
    public void onNrSurveyRecord(NrRecord nrRecord)
    {
        if (dropMessages.get()) return;

        if (effectiveDeviceName != null)
        {
            final NrRecord.Builder recordBuilder = nrRecord.toBuilder();
            nrRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_NR_MESSAGE_TOPIC, nrRecord);
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (dropMessages.get()) return;

        wifiBeaconRecords.forEach(wifiRecord -> {
            WifiBeaconRecord wifiBeaconRecord = wifiRecord.getWifiBeaconRecord();
            if (effectiveDeviceName != null)
            {
                final WifiBeaconRecord.Builder recordBuilder = wifiBeaconRecord.toBuilder();
                wifiBeaconRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
            }
            publishMessage(MQTT_WIFI_BEACON_MESSAGE_TOPIC, wifiBeaconRecord);
        });
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        if (dropMessages.get()) return;

        // Set the device name using the pre-computed effective device name
        if (effectiveDeviceName != null)
        {
            final BluetoothRecord.Builder recordBuilder = bluetoothRecord.toBuilder();
            bluetoothRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_BLUETOOTH_MESSAGE_TOPIC, bluetoothRecord);
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        if (dropMessages.get()) return;

        bluetoothRecords.forEach(bluetoothRecord -> {
            if (effectiveDeviceName != null)
            {
                final BluetoothRecord.Builder recordBuilder = bluetoothRecord.toBuilder();
                bluetoothRecord = recordBuilder.setData(recordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
            }
            publishMessage(MQTT_BLUETOOTH_MESSAGE_TOPIC, bluetoothRecord);
        });
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        if (dropMessages.get()) return;

        if (effectiveDeviceName != null)
        {
            final GnssRecord.Builder gnssRecordBuilder = gnssRecord.toBuilder();
            gnssRecord = gnssRecordBuilder.setData(gnssRecordBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_GNSS_MESSAGE_TOPIC, gnssRecord);
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        if (dropMessages.get()) return;

        if (effectiveDeviceName != null)
        {
            final DeviceStatus.Builder deviceStatusBuilder = deviceStatus.toBuilder();
            deviceStatus = deviceStatusBuilder.setData(deviceStatusBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_DEVICE_STATUS_MESSAGE_TOPIC, deviceStatus);
    }

    @Override
    public void onPhoneState(PhoneState phoneState)
    {
        if (dropMessages.get()) return;

        if (effectiveDeviceName != null)
        {
            final PhoneState.Builder messageBuilder = phoneState.toBuilder();
            phoneState = messageBuilder.setData(messageBuilder.getDataBuilder().setDeviceName(effectiveDeviceName)).build();
        }

        publishMessage(MQTT_DEVICE_STATUS_MESSAGE_TOPIC, phoneState);
    }
}