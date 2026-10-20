package com.craxiom.networksurvey.destinations

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.mqttlibrary.MqttQos
import com.craxiom.mqttlibrary.connection.ConnectionState
import com.craxiom.networksurvey.mqtt.MqttConnection
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that the embedded Moquette broker runs on ART and receives a record published by the
 * app's own [MqttConnection]. Every other MQTT destination test depends on this.
 */
@RunWith(AndroidJUnit4::class)
class MqttBrokerSpikeTest {

    private val broker = EmbeddedMqttBroker()
    private val connection = MqttConnection()

    @Before
    fun setUp() {
        broker.start()
    }

    @After
    fun tearDown() {
        connection.disconnect()
        broker.stop()
    }

    @Test
    fun brokerOnArt_receivesLteRecordFromAppConnection() {
        connection.connect(ApplicationProvider.getApplicationContext(), connectionInfo(broker.port))

        assertThat(awaitCondition { connection.connectionState == ConnectionState.CONNECTED })
            .isTrue()

        connection.onLteSurveyRecord(
            LteRecord.newBuilder()
                .setData(LteRecordData.newBuilder().setDeviceSerialNumber(SERIAL))
                .build()
        )

        assertThat(awaitCondition { broker.messagesOn(LTE_TOPIC).isNotEmpty() }).isTrue()
        assertThat(broker.connectedClientIds).contains(CLIENT_ID)
        assertThat(broker.messagesOn(LTE_TOPIC).first().payload).contains(SERIAL)
    }

    private companion object {
        const val CLIENT_ID = "ns-spike-client"
        const val SERIAL = "spike-serial"
        const val LTE_TOPIC = "lte_message"

        fun connectionInfo(port: Int) = MqttConnectionInfo(
            LOOPBACK_HOST, port, false, CLIENT_ID, null, null,
            true, true, true, true, true, true,
            "", null, MqttQos.AT_LEAST_ONCE
        )
    }
}
