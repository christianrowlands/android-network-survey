package com.craxiom.networksurvey.services;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import com.craxiom.mqttlibrary.MqttQos;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

/**
 * Instrumented regression tests for the Mission ID rolling behavior of {@link NetworkSurveyService}.
 * <p>
 * The Mission ID rolls on the transition to the first mission relevant survey of a session. MQTT
 * streaming is one such trigger, and it is the survey type that auto-starts at phone boot. The roll
 * relies on a tight ordering inside {@link NetworkSurveyService#connectToMqttBroker}: the underlying
 * MQTT connection must report the {@code CONNECTING} state (set synchronously by
 * {@code DefaultMqttConnection.connect()}) before {@code onSurveyStarted()} runs one line later. If
 * a future change reordered those calls, or moved the {@code CONNECTING} notification into the
 * asynchronous connect callback, the roll would silently stop happening at boot. This test pins that
 * invariant.
 * <p>
 * The test uses the real {@link com.craxiom.networksurvey.mqtt.MqttConnection} pointed at an
 * unreachable broker, so it exercises the genuine end to end behavior without needing a live broker.
 * The background connection attempt fails harmlessly; the connection state stays {@code CONNECTING}
 * and never calls {@code onSurveyStopped()}, so the rolled values cannot be flipped back before the
 * assertions run.
 */
@RunWith(AndroidJUnit4.class)
public class NetworkSurveyServiceMissionIdTest
{
    /**
     * A documentation only address from the TEST-NET-1 block (RFC 5737). It is guaranteed not to
     * route to a real host, so the MQTT connection attempt fails in the background without the test
     * ever contacting a broker.
     */
    private static final String UNREACHABLE_BROKER_HOST = "192.0.2.1";
    private static final int MQTT_PORT = 1883;
    private static final String TEST_CLIENT_ID = "ns-mission-id-test-client";
    private static final String TEST_TOPIC_PREFIX = "ns-mission-id-test";
    private static final String TEST_DEVICE_NAME = "ns-mission-id-test-device";

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    /**
     * Granting location up front lets the heavy {@link NetworkSurveyService#onCreate()} controller
     * and notification setup run cleanly on a fresh emulator. The service is written to survive
     * without it (the foreground start is wrapped in a try/catch), but granting it removes the most
     * likely source of flakiness.
     */
    @Rule
    public final GrantPermissionRule locationPermissionRule =
            GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION);

    private NetworkSurveyService service;

    @After
    public void tearDown()
    {
        // Stop the background auto-reconnect to the unreachable broker. ServiceTestRule unbinds the
        // service after the test, which destroys the bound-only service.
        if (service != null)
        {
            service.disconnectFromMqttBroker();
        }
    }

    /**
     * Verifies that starting MQTT streaming rolls a fresh Mission ID and marks the mission session
     * active. This is the exact code path that auto-starts at phone boot
     * ({@code StartAtBootReceiver -> attemptMqttConnectionAtBoot -> connectToMqttBroker}).
     */
    @Test
    public void connectToMqttBroker_rollsMissionId() throws TimeoutException
    {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, NetworkSurveyService.class);
        final IBinder binder = serviceRule.bindService(intent);
        service = (NetworkSurveyService) ((NetworkSurveyService.SurveyServiceBinder) binder).getService();

        // Clean session: nothing has rolled a Mission ID yet.
        assertThat(service.getRolledMissionId()).isNull();
        assertThat(service.isMissionSessionActive()).isFalse();

        service.connectToMqttBroker(buildUnreachableConnectionInfo());

        // connectToMqttBroker sets the connection state to CONNECTING synchronously and then calls
        // onSurveyStarted() inline, so the roll has already happened by the time the call returns.
        assertThat(service.getRolledMissionId()).isNotNull();
        assertThat(service.getRolledMissionId()).startsWith(NetworkSurveyConstants.MISSION_ID_PREFIX);
        assertThat(service.isMissionSessionActive()).isTrue();
    }

    /**
     * Builds a connection to an unreachable broker with every stream flag disabled. Disabling the
     * stream flags keeps {@code connectToMqttBroker} from registering the permission sensitive scan
     * listeners; the Mission ID roll does not depend on them.
     *
     * @return A {@link MqttConnectionInfo} that will never reach a real broker.
     */
    private MqttConnectionInfo buildUnreachableConnectionInfo()
    {
        return new MqttConnectionInfo(
                UNREACHABLE_BROKER_HOST,
                MQTT_PORT,
                false, // tlsEnabled
                TEST_CLIENT_ID,
                null, // mqttUsername
                null, // mqttPassword
                // The six stream flags must all stay false (cellular, wifi, bluetooth, gnss,
                // deviceStatus, phoneState) so connectToMqttBroker skips registering the scan
                // listeners. The Mission ID roll does not depend on them.
                false, // isCellularStreamEnabled
                false, // isWifiStreamEnabled
                false, // isBluetoothStreamEnabled
                false, // isGnssStreamEnabled
                false, // isDeviceStatusStreamEnabled
                false, // isPhoneStateStreamEnabled
                TEST_TOPIC_PREFIX,
                TEST_DEVICE_NAME,
                MqttQos.AT_MOST_ONCE);
    }
}
