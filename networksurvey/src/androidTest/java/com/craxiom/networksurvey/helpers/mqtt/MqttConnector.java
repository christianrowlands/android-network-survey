package com.craxiom.networksurvey.helpers.mqtt;

import android.content.Context;
import android.util.Log;

import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.networksurvey.TestBase;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MqttConnector
{
    private static final String LOG_TAG = TestBase.class.getSimpleName();
    public MqttAndroidClient mqttAndroidClient;
    BrokerConnectionInfo connectionInfo;

    public MqttConnector(Context context, BrokerConnectionInfo connectionInfo)
    {
        mqttAndroidClient = new MqttAndroidClient(context, connectionInfo.getMqttServerUri(), "test-automation-subscriber");
        mqttAndroidClient.setCallback(new MqttCallbackExtended()
        {
            @Override
            public void connectComplete(boolean b, String s)
            {
                Log.d(LOG_TAG, s);
            }

            @Override
            public void connectionLost(Throwable throwable)
            {

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception
            {
                Log.d(LOG_TAG, mqttMessage.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken)
            {

            }
        });
        this.connectionInfo = connectionInfo;
        connect();
    }

    public void setCallback(MqttCallbackExtended callback)
    {
        mqttAndroidClient.setCallback(callback);
    }

    private void connect()
    {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(connectionInfo.getMqttUsername());
        mqttConnectOptions.setPassword(connectionInfo.getMqttPassword().toCharArray());

        try
        {

            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener()
            {
                @Override
                public void onSuccess(IMqttToken asyncActionToken)
                {

                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception)
                {
                    Log.e(LOG_TAG, "Failed to connect to: " + connectionInfo.getMqttServerUri() + exception.toString());
                }
            });
        } catch (MqttException ex)
        {
            ex.printStackTrace();
        }
    }

    private void subscribeToTopic()
    {
        try
        {
            mqttAndroidClient.subscribe("bluetooth_message", 2, null, new IMqttActionListener()
            {
                @Override
                public void onSuccess(IMqttToken asyncActionToken)
                {
                    Log.w(LOG_TAG, "Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception)
                {
                    Log.w(LOG_TAG, "Subscribed fail!");
                }
            });
        } catch (MqttException ex)
        {
            Log.e(LOG_TAG, "Exception whilst subscribing", ex);
        }
    }
}
