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
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

/**
 * Instrumented regression tests for the Mission ID rolling behavior of {@link NetworkSurveyService}.
 * <p>
 * The Mission ID rolls on the transition to the first mission relevant survey of a session. The roll
 * is driven by caller intent ({@code onSurveyStarted(true)}) and every mission relevant start path
 * rolls before it registers any record listener, so the immediate SERVICE_STATE record produced when
 * a phone state / CDR listener is registered can never capture a pre-roll Mission ID. Records stamped
 * before any mission relevant survey starts carry an empty Mission ID (only OpenCelliD/BeaconDB scans
 * land there, and they discard it).
 * <p>
 * The MQTT test uses the real {@link com.craxiom.networksurvey.mqtt.MqttConnection} pointed at an
 * unreachable broker, so it exercises the genuine end to end behavior without needing a live broker.
 * The background connection attempt fails harmlessly; the connection state never calls
 * {@code onSurveyStopped()}, so the rolled values cannot be flipped back before the assertions run.
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
     * and notification setup run cleanly on a fresh emulator. READ_PHONE_STATE lets the phone state
     * roll test start phone state logging. The service is written to survive without location (the
     * foreground start is wrapped in a try/catch), but granting these removes the most likely sources
     * of flakiness.
     */
    @Rule
    public final GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE);

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
        service = bindService();

        // Clean session: nothing has rolled a Mission ID yet, so records carry an empty Mission ID.
        assertThat(service.getRolledMissionId()).isNull();
        assertThat(service.isMissionSessionActive()).isFalse();
        assertThat(service.getMissionIdForRecords()).isEmpty();

        service.connectToMqttBroker(buildUnreachableConnectionInfo());

        // connectToMqttBroker calls onSurveyStarted(true) inline, so the roll has already happened by
        // the time the call returns.
        assertThat(service.getRolledMissionId()).isNotNull();
        assertThat(service.getRolledMissionId()).startsWith(NetworkSurveyConstants.MISSION_ID_PREFIX);
        assertThat(service.isMissionSessionActive()).isTrue();

        // The value stamped on records is exactly the rolled Mission ID; there is no separate
        // bootstrap value that could diverge from it.
        assertThat(service.getMissionIdForRecords()).isEqualTo(service.getRolledMissionId());
    }

    /**
     * Verifies that before any mission relevant survey starts, records are stamped with an empty
     * Mission ID rather than a fabricated bootstrap value. This is the single-source-of-truth
     * guarantee that replaced the old two value (bootstrap + rolled) design.
     */
    @Test
    public void getMissionIdForRecords_isEmptyBeforeAnySurvey() throws TimeoutException
    {
        service = bindService();

        assertThat(service.getRolledMissionId()).isNull();
        assertThat(service.isMissionSessionActive()).isFalse();
        assertThat(service.getMissionIdForRecords()).isEmpty();
    }

    /**
     * Verifies that starting phone state logging rolls the Mission ID as part of the toggle, before
     * the phone state listener is registered. The listener's immediate SERVICE_STATE record is
     * therefore stamped with the rolled value, never a pre-roll one. Skipped when phone state logging
     * cannot start in the test environment (for example when no log type is enabled).
     */
    @Test
    public void togglePhoneStateLogging_rollsMissionId() throws TimeoutException
    {
        service = bindService();

        assertThat(service.getRolledMissionId()).isNull();
        assertThat(service.isMissionSessionActive()).isFalse();

        final Boolean started = service.togglePhoneStateLogging(true);
        Assume.assumeTrue("Phone state logging could not start in this environment",
                Boolean.TRUE.equals(started));

        try
        {
            // The roll runs synchronously inside togglePhoneStateLogging before the listener is
            // registered, so the Mission ID is committed by the time the call returns.
            assertThat(service.getRolledMissionId()).isNotNull();
            assertThat(service.getRolledMissionId()).startsWith(NetworkSurveyConstants.MISSION_ID_PREFIX);
            assertThat(service.isMissionSessionActive()).isTrue();
            assertThat(service.getMissionIdForRecords()).isEqualTo(service.getRolledMissionId());
        } finally
        {
            service.togglePhoneStateLogging(false);
        }
    }

    /**
     * Binds the {@link NetworkSurveyService} and returns the bound instance. The heavy
     * {@link NetworkSurveyService#onCreate()} runs during the bind.
     *
     * @return The bound service instance.
     */
    private NetworkSurveyService bindService() throws TimeoutException
    {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, NetworkSurveyService.class);
        final IBinder binder = serviceRule.bindService(intent);
        return (NetworkSurveyService) ((NetworkSurveyService.SurveyServiceBinder) binder).getService();
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
