package com.craxiom.networksurvey.screens;

import androidx.test.espresso.NoMatchingViewException;

import com.craxiom.networksurvey.R;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.schibsted.spain.barista.interaction.BaristaClickInteractions.*;
import static com.schibsted.spain.barista.interaction.BaristaEditTextInteractions.*;
import static com.schibsted.spain.barista.interaction.BaristaSleepInteractions.*;
import static org.hamcrest.Matchers.*;

public class MqttConnectionScreen
{
    public static int MQTT_HOSTNAME_FIELD = R.id.mqttHostAddress;
    public static int MQTT_CLIENT_FIELD = R.id.deviceName;
    public static int MQTT_USERNAME_FIELD = R.id.usernameTextInputLayout;
    public static int MQTT_PASSWORD_FIELD = R.id.passwordTextInputLayout;
    public static int MQTT_PORT_NUMBER = R.id.mqttPortNumber;
    public static int MQTT_CONNECTION_STATUS_TEXT = R.id.connection_status_text;

    public static void setMqttHostnameField(String text)
    {
        writeTo(MQTT_HOSTNAME_FIELD, text);
    }

    public static void setMqttClientField(String text)
    {
        writeTo(MQTT_CLIENT_FIELD, text);
    }

    public static void toggleTlsEnabled()
    {
        clickOn(R.id.tlsToggleSwitch);
    }

    public static void setMqttPort(int port)
    {

    }

    public static void setMqttUsernameField(String username)
    {
        writeTo(MQTT_USERNAME_FIELD, username);
    }

    public static void setMqttPasswordField(String password)
    {
        writeTo(MQTT_PASSWORD_FIELD, password);
    }

    public static void toggleStreamCellular()
    {
        clickOn(R.id.streamCellularToggleSwitch);
    }

    public static void toggleStreamWiFi()
    {
        clickOn(R.id.streamWifiToggleSwitch);
    }

    public static void toggleStreamBluetooth()
    {
        clickOn(R.id.streamBluetoothToggleSwitch);
    }

    public static void toggleStreamGNSS()
    {
        clickOn(R.id.streamGnssToggleSwitch);
    }

    public static void toggleMqttConnect()
    {
        clickOn(R.id.mqttConnectToggleSwitch);
        sleep(3, TimeUnit.SECONDS);
    }

    public static boolean assertMqttPortNumber(int portNumber)
    {
        try
        {
            onView(
                    allOf(withId(R.id.mqttPortNumber), withText(Integer.toString(portNumber)),
                            withParent(withParent(withId(R.id.mqttPortNumberTextInputLayout))),
                            isDisplayed()))
                    .check(matches(withText(Integer.toString(portNumber))));
            return true;
        } catch (NoMatchingViewException e)
        {
            return false;
        }
    }
}
