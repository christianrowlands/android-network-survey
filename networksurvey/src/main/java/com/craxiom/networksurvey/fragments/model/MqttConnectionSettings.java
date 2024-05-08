package com.craxiom.networksurvey.fragments.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Holds the MQTT Broker connection settings scanned from a QR Code
 *
 * @since 1.7.0
 */
public record MqttConnectionSettings(
        @SerializedName("mqtt_host") String host,
        @SerializedName("mqtt_port") int port,
        @SerializedName("mqtt_tls") Boolean tlsEnabled,
        @SerializedName("mqtt_client") String deviceName,
        @SerializedName("mqtt_username") String mqttUsername,
        @SerializedName("mqtt_password") String mqttPassword,
        @SerializedName("mqtt_topic_prefix") String mqttTopicPrefix
) implements Serializable {

    public static class Builder {
        private String host;
        private int port;
        private Boolean tlsEnabled;
        private String deviceName;
        private String mqttUsername;
        private String mqttPassword;
        private String mqttTopicPrefix;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder tlsEnabled(Boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
            return this;
        }

        public Builder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public Builder mqttUsername(String mqttUsername) {
            this.mqttUsername = mqttUsername;
            return this;
        }

        public Builder mqttPassword(String mqttPassword) {
            this.mqttPassword = mqttPassword;
            return this;
        }

        public Builder mqttTopicPrefix(String mqttTopicPrefix) {
            this.mqttTopicPrefix = mqttTopicPrefix;
            return this;
        }

        public MqttConnectionSettings build() {
            return new MqttConnectionSettings(host, port, tlsEnabled, deviceName, mqttUsername, mqttPassword, mqttTopicPrefix);
        }
    }
}
