package com.craxiom.networksurvey.mqtt;

import android.content.Context;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IConnectionStateListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import timber.log.Timber;

/**
 * Class for creating a connection to an MQTT server.
 *
 * @since 0.1.1
 */
public class MqttConnection implements ICellularSurveyRecordListener, IWifiSurveyRecordListener, IGnssSurveyRecordListener
{
    private static final String MQTT_GSM_MESSAGE_TOPIC = "gsm_message";
    private static final String MQTT_CDMA_MESSAGE_TOPIC = "cdma_message";
    private static final String MQTT_UMTS_MESSAGE_TOPIC = "umts_message";
    private static final String MQTT_LTE_MESSAGE_TOPIC = "lte_message";
    private static final String MQTT_WIFI_BEACON_MESSAGE_TOPIC = "80211_beacon_message";
    private static final String MQTT_GNSS_MESSAGE_TOPIC = "gnss_message";

    /**
     * The amount of time to wait for a proper disconnection to occur before we force kill it.
     */
    private static final long DISCONNECT_TIMEOUT = 250L;

    private final List<IConnectionStateListener> mqttConnectionListeners = new CopyOnWriteArrayList<>();

    private MqttAndroidClient mqttAndroidClient;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private final JsonFormat.Printer jsonFormatter;
    private String mqttClientId;

    public MqttConnection()
    {
        jsonFormatter = JsonFormat.printer().preservingProtoFieldNames().omittingInsignificantWhitespace();
    }

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
    public void onGnssSurveyRecord(GnssRecord gnssRecord)
    {
        if (mqttClientId != null)
        {
            final GnssRecord.Builder gnssRecordBuilder = gnssRecord.toBuilder();
            gnssRecord = gnssRecordBuilder.setData(gnssRecordBuilder.getDataBuilder().setDeviceName(mqttClientId)).build();
        }

        publishMessage(MQTT_GNSS_MESSAGE_TOPIC, gnssRecord);
    }

    /**
     * @return The current {@link ConnectionState} of the connection to the MQTT Broker.
     */
    public ConnectionState getConnectionState()
    {
        return connectionState;
    }

    /**
     * Adds an {@link IConnectionStateListener} so that it will be notified of all future MQTT connection state changes.
     *
     * @param connectionStateListener The listener to add.
     */
    public void registerMqttConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        mqttConnectionListeners.add(connectionStateListener);
    }

    /**
     * Removes an {@link IConnectionStateListener} so that it will no longer be notified of MQTT connection state changes.
     *
     * @param connectionStateListener The listener to remove.
     */
    public void unregisterMqttConnectionStateListener(IConnectionStateListener connectionStateListener)
    {
        mqttConnectionListeners.remove(connectionStateListener);
    }

    /**
     * Connect to the MQTT Broker.
     * <p>
     * Synchronize so that we don't mess with the connection client while creating a new connection.
     *
     * @param applicationContext The context to use for the MQTT Android Client.
     */
    public synchronized void connect(Context applicationContext, MqttBrokerConnectionInfo connectionInfo)
    {
        try
        {
            mqttClientId = connectionInfo.getMqttClientId();
            mqttAndroidClient = new MqttAndroidClient(applicationContext, connectionInfo.getMqttServerUri(), mqttClientId);
            mqttAndroidClient.setCallback(new MyMqttCallbackExtended(this));

            final String username = connectionInfo.getMqttUsername();
            final String password = connectionInfo.getMqttPassword();

            final MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setCleanSession(false);
            if (username != null) mqttConnectOptions.setUserName(username);
            if (password != null) mqttConnectOptions.setPassword(password.toCharArray());

            mqttAndroidClient.connect(mqttConnectOptions, null, new MyMqttActionListener(this));
        } catch (Exception e)
        {
            Timber.e(e, "Unable to create the connection to the MQTT broker");
        }
    }

    /**
     * Disconnect from the MQTT Broker.
     * <p>
     * This method is synchronized so that we don't try connecting while a disconnect is in progress.
     */
    public synchronized void disconnect()
    {
        if (mqttAndroidClient != null)
        {
            try
            {
                final IMqttToken token = mqttAndroidClient.disconnect(DISCONNECT_TIMEOUT);
                token.waitForCompletion(DISCONNECT_TIMEOUT);  // Wait for completion so that we don't initiate a new connection while waiting for this disconnect.
            } catch (Exception e)
            {
                Timber.e(e, "An exception occurred when disconnecting from the MQTT broker");
            }
        }

        notifyConnectionStateChange(ConnectionState.DISCONNECTED);
    }

    /**
     * Notify all the registered listeners of the new connection state.
     *
     * @param newConnectionState The new MQTT connection state.
     */
    private synchronized void notifyConnectionStateChange(ConnectionState newConnectionState)
    {
        Timber.i("MQTT Connection State Changed.  oldConnectionState=%s, newConnectionState=%s", connectionState, newConnectionState);

        connectionState = newConnectionState;

        for (IConnectionStateListener listener : mqttConnectionListeners)
        {
            try
            {
                listener.onConnectionStateChange(newConnectionState);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a MQTT Connection State Listener because of an exception");
            }
        }
    }

    /**
     * Send the provided Protobuf message to the MQTT Broker.
     * <p>
     * The Protobuf message is formatted as JSON and then published to the specified topic.
     *
     * @param mqttMessageTopic The MQTT Topic to publish the message to.
     * @param message          The Protobuf message to format as JSON and send to the MQTT Broker.
     */
    private synchronized void publishMessage(String mqttMessageTopic, MessageOrBuilder message)
    {
        try
        {
            final String messageJson = jsonFormatter.print(message);

            mqttAndroidClient.publish(mqttMessageTopic, new MqttMessage(messageJson.getBytes()));
        } catch (Exception e)
        {
            Timber.e(e, "Caught an exception when trying to send an MQTT message");
        }
    }

    /**
     * Listener for the overall MQTT client.  This listener gets notified for any events that happen such as connection
     * success or lost events, message delivery receipts, or notifications of new incoming messages.
     */
    private static class MyMqttCallbackExtended implements MqttCallbackExtended
    {
        private final MqttConnection mqttConnection;

        MyMqttCallbackExtended(MqttConnection mqttConnection)
        {
            this.mqttConnection = mqttConnection;
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI)
        {
            mqttConnection.notifyConnectionStateChange(ConnectionState.CONNECTED);
            if (reconnect)
            {
                Timber.i("Reconnect to: %s", serverURI);
            } else
            {
                Timber.i("Connected to: %s", serverURI);
            }
        }

        @Override
        public void connectionLost(Throwable cause)
        {
            Timber.w(cause, "Connection lost: ");

            // As best I can tell, the connection lost method is called for all connection lost scenarios, including
            // when the user manually stops the connection.  If the user manually stopped the connection the cause seems
            // to be null, so don't indicate that we are trying to reconnect.
            if (cause != null)
            {
                mqttConnection.notifyConnectionStateChange(ConnectionState.CONNECTING);
            }
        }

        @Override
        public void messageArrived(String topic, MqttMessage message)
        {
            Timber.i("Message arrived: Topic=%s, MQTT Message=%s", topic, message);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token)
        {
        }
    }

    /**
     * Listener that can be used when connecting to the MQTT Broker.  We will get notified if the connection succeeds
     * or fails.
     * <p>
     * Callbacks occur on the MQTT Client Thread, so don't do any long running operations in the listener methods.
     */
    private static class MyMqttActionListener implements IMqttActionListener
    {
        private final MqttConnection mqttConnection;

        MyMqttActionListener(MqttConnection mqttConnection)
        {
            this.mqttConnection = mqttConnection;
        }

        @Override
        public void onSuccess(IMqttToken asyncActionToken)
        {
            Timber.i("MQTT Broker Connected!!!!");
            mqttConnection.notifyConnectionStateChange(ConnectionState.CONNECTED);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception)
        {
            Timber.e(exception, "Failed to connect");
            mqttConnection.notifyConnectionStateChange(ConnectionState.CONNECTING);
        }
    }
}