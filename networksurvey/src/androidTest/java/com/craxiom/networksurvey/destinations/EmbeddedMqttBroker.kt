package com.craxiom.networksurvey.destinations

import io.moquette.BrokerConstants
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import io.moquette.broker.security.IAuthenticator
import io.moquette.interception.AbstractInterceptHandler
import io.moquette.interception.messages.InterceptConnectMessage
import io.moquette.interception.messages.InterceptPublishMessage
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An in-process MQTT broker (Moquette) for the instrumented destination tests. It listens on the
 * device's loopback interface only, so the tests never touch the network the phone is on.
 *
 * Every PUBLISH it receives is captured in [published] so a test can assert on topics and payloads.
 * Persistence is off (Android's working directory is read-only) and the native epoll transport and
 * the websocket listener are disabled because neither exists on ART.
 *
 * @param rejectSignIn True to refuse every client, with or without a login, so a test can see the
 *                     broker reject a sign-in. The app's client sends an empty username and
 *                     password when the profile has none, which Moquette's anonymous check lets
 *                     through, so the refusal needs an authenticator.
 */
class EmbeddedMqttBroker(val port: Int = DEFAULT_PORT, private val rejectSignIn: Boolean = false) {

    /** One captured PUBLISH. */
    data class Message(val clientId: String, val topic: String, val payload: String)

    val published: MutableList<Message> = CopyOnWriteArrayList()
    val connectedClientIds: MutableList<String> = CopyOnWriteArrayList()

    private var server: Server? = null

    private val captureHandler = object : AbstractInterceptHandler() {
        override fun getID(): String = "ns-test-capture"

        override fun onConnect(msg: InterceptConnectMessage) {
            connectedClientIds.add(msg.clientID)
        }

        override fun onPublish(msg: InterceptPublishMessage) {
            try {
                val payload = msg.payload.toString(StandardCharsets.UTF_8)
                published.add(Message(msg.clientID, msg.topicName, payload))
            } finally {
                super.onPublish(msg)
            }
        }

        override fun onSessionLoopError(error: Throwable?) {
            // Nothing to do; a test fails on the missing messages instead.
        }
    }

    /** Starts the broker. Safe to call again after [stop] to simulate a broker restart. */
    fun start() {
        val properties = Properties().apply {
            setProperty(BrokerConstants.HOST_PROPERTY_NAME, LOOPBACK_HOST)
            setProperty(BrokerConstants.PORT_PROPERTY_NAME, port.toString())
            setProperty(BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, BrokerConstants.DISABLED_PORT_BIND)
            setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, (!rejectSignIn).toString())
            setProperty(BrokerConstants.PERSISTENCE_ENABLED_PROPERTY_NAME, "false")
            setProperty(NETTY_EPOLL_PROPERTY_NAME, "false")
        }
        val authenticator = if (rejectSignIn) IAuthenticator { _, _, _ -> false } else null
        server = Server().also {
            it.startServer(MemoryConfig(properties), listOf(captureHandler), null, authenticator, null)
        }
    }

    /** Stops the broker; connected clients see the connection drop. */
    fun stop() {
        server?.stopServer()
        server = null
    }

    /** Returns the captured messages on [topic]. */
    fun messagesOn(topic: String): List<Message> = published.filter { it.topic == topic }

    companion object {
        const val DEFAULT_PORT = 18831
        private const val NETTY_EPOLL_PROPERTY_NAME = "netty.epoll"
    }
}
