package com.craxiom.networksurvey.mqtt;

import android.content.Context;
import android.util.Log;

import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IConnectionStateListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.UmtsRecord;
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

/**
 * Class for creating a connection to an MQTT server.
 *
 * @since 0.1.1
 */
public class MqttConnection implements ICellularSurveyRecordListener, IWifiSurveyRecordListener
{
    private static final String LOG_TAG = MqttConnection.class.getSimpleName();

    private static final String MQTT_GSM_MESSAGE_TOPIC = "GSM_MESSAGE";
    private static final String MQTT_CDMA_MESSAGE_TOPIC = "CDMA_MESSAGE";
    private static final String MQTT_UMTS_MESSAGE_TOPIC = "UMTS_MESSAGE";
    private static final String MQTT_LTE_MESSAGE_TOPIC = "LTE_MESSAGE";
    private static final String MQTT_WIFI_BEACON_MESSAGE_TOPIC = "80211_BEACON_MESSAGE";

    private final List<IConnectionStateListener> mqttConnectionListeners = new CopyOnWriteArrayList<>();

    private MqttAndroidClient mqttAndroidClient;
    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private final JsonFormat.Printer jsonFormatter;

    public MqttConnection()
    {
        jsonFormatter = JsonFormat.printer().preservingProtoFieldNames().omittingInsignificantWhitespace();
    }

    @Override
    public void onGsmSurveyRecord(GsmRecord gsmRecord)
    {
        publishMessage(MQTT_GSM_MESSAGE_TOPIC, gsmRecord);
    }

    @Override
    public void onCdmaSurveyRecord(CdmaRecord cdmaRecord)
    {
        publishMessage(MQTT_CDMA_MESSAGE_TOPIC, cdmaRecord);
    }

    @Override
    public void onUmtsSurveyRecord(UmtsRecord umtsRecord)
    {
        publishMessage(MQTT_UMTS_MESSAGE_TOPIC, umtsRecord);
    }

    @Override
    public void onLteSurveyRecord(LteRecord lteRecord)
    {
        publishMessage(MQTT_LTE_MESSAGE_TOPIC, lteRecord);
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        wifiBeaconRecords.forEach(wifiRecord -> publishMessage(MQTT_WIFI_BEACON_MESSAGE_TOPIC, wifiRecord.getWifiBeaconRecord()));
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
     *
     * @param applicationContext The context to use for the MQTT Android Client.
     */
    public synchronized void connect(Context applicationContext, MqttBrokerConnectionInfo connectionInfo)
    {
        try
        {
            mqttAndroidClient = new MqttAndroidClient(applicationContext, connectionInfo.getMqttServerUri(), connectionInfo.getMqttClientId());
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
            Log.e(LOG_TAG, "Unable to create the connection to the MQTT broker");
        }
    }

    /**
     * Disconnect from the MQTT Broker.
     */
    public synchronized void disconnect()
    {
        if (mqttAndroidClient != null)
        {
            try
            {
                if (mqttAndroidClient.isConnected()) mqttAndroidClient.disconnect(500L);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Could not successfully disconnect from the MQTT broker");
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
        if (Log.isLoggable(LOG_TAG, Log.INFO))
        {
            Log.i(LOG_TAG, "MQTT Connection State Changed.  oldConnectionState=" + connectionState + ", newConnectionState=" + newConnectionState);
        }

        connectionState = newConnectionState;

        for (IConnectionStateListener listener : mqttConnectionListeners)
        {
            try
            {
                listener.onConnectionStateChange(newConnectionState);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a MQTT Connection State Listener because of an exception", e);
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
            Log.e(LOG_TAG, "Caught an exception when trying to send an MQTT message");
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
                Log.i(LOG_TAG, "Reconnect to: " + serverURI);
            } else
            {
                Log.i(LOG_TAG, "Connected to: " + serverURI);
            }
        }

        @Override
        public void connectionLost(Throwable cause)
        {
            Log.w(LOG_TAG, "Connection lost: ", cause);

            // As best I can tell, the connection lost method is called for all connection lost scenarios, including
            // when the user manually stops the connection.  If the user manually stopped the connection the cause seems
            // to be null, so don't indicate that we are trying to reconnect.
            if (cause != null) mqttConnection.notifyConnectionStateChange(ConnectionState.CONNECTING);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message)
        {
            Log.i(LOG_TAG, "Message arrived: Topic=" + topic + "MQTT Message=" + message);
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
            Log.i(LOG_TAG, "MQTT Broker Connected!!!!");
            mqttConnection.notifyConnectionStateChange(ConnectionState.CONNECTED);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception)
        {
            Log.e(LOG_TAG, "Failed to connect", exception);
        }
    }
}