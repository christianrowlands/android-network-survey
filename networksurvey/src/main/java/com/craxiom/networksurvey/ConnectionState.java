package com.craxiom.networksurvey;

/**
 * Represents the various states of the gRPC or MQTT Broker Connection.
 *
 * @since 0.0.4
 */
public enum ConnectionState
{
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
