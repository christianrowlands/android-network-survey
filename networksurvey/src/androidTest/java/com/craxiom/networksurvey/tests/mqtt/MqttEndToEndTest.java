package com.craxiom.networksurvey.tests.mqtt;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.craxiom.mqttlibrary.MqttConstants;
import com.craxiom.networksurvey.TestBase;
import com.craxiom.networksurvey.helpers.TestUtils;
import com.craxiom.networksurvey.helpers.mqtt.MqttConnector;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.screens.HamburgerMenu;
import com.craxiom.networksurvey.screens.MqttConnectionScreen;
import com.craxiom.networksurvey.screens.TopMenuBar;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.*;
import static com.schibsted.spain.barista.assertion.BaristaVisibilityAssertions.*;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.*;

@RunWith(AndroidJUnit4.class)
public class MqttEndToEndTest extends TestBase
{
    private static final String LOG_TAG = MqttEndToEndTest.class.getSimpleName();
    private static final String MQTT_HOSTNAME = TestUtils.System.getDockerMqttBrokerHostname();
    private static MqttConnectionInfo connectionInfo;
    private MqttConnector mqttConnector;

    @Test
    public void enableBluetoothLoggingToMQTTBrokerNonTls()
    {
        Log.i(LOG_TAG, "Setting up MQTT connection...");
        TopMenuBar.clickHamburgerMenu();
        HamburgerMenu.clickMqttConnectionOption();
        MqttConnectionScreen.setMqttHostnameField(MQTT_HOSTNAME);
        /*
            Disable TLS, The overhead required to manage a certificate is not needed. This extra step
            is required since clearing application preferences before a test run results in an
            exception at the end of the test run. Otherwise, we carry over settings between runs and
            the test could get in a bad state.
         */
        if (MqttConnectionScreen.assertMqttPortNumber(MqttConstants.MQTT_SSL_PORT))
        {
            MqttConnectionScreen.toggleTlsEnabled();
        }
        assertWithMessage("Port number is updated to 1883")
                .that(MqttConnectionScreen.assertMqttPortNumber(MqttConstants.MQTT_PLAIN_TEXT_PORT))
                .isTrue();
        MqttConnectionScreen.setMqttClientField("test-automation-publisher");
        MqttConnectionScreen.setMqttUsernameField("docker");
        MqttConnectionScreen.setMqttPasswordField("docker");
        MqttConnectionScreen.toggleMqttConnect();
        assertContains(MqttConnectionScreen.MQTT_CONNECTION_STATUS_TEXT, "Connected");
        Log.i(LOG_TAG, "Service successfully connected to MQTT broker for publishing" + TestUtils.System.getDockerMqttBrokerHostname());
        Log.i(LOG_TAG, "Gathering bluetooth records...");
        sleep(30, TimeUnit.SECONDS);
        connectionInfo = new MqttConnectionInfo(
                MQTT_HOSTNAME,
                MqttConstants.MQTT_PLAIN_TEXT_PORT,
                false,
                "test-automation-subscriber",
                "docker",
                "docker",
                false,
                false,
                true,
                false);
        mqttConnector = new MqttConnector(getContext(), connectionInfo);
        mqttConnector.mqttAndroidClient.setCallback(new MqttCallbackExtended()
        {
            @Override
            public void connectComplete(boolean b, String s)
            {
                Log.w("Debug", "Connected");
            }

            @Override
            public void connectionLost(Throwable throwable)
            {

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception
            {
                assertWithMessage("MQTT Messages were delivered")
                        .that(mqttMessage.getPayload())
                        .isNotEmpty();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken)
            {
            }
        });
    }
}
