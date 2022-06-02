package com.craxiom.networksurvey.fragments.model;

import com.google.gson.annotations.SerializedName;

public class ScannedSettings
{
    @SerializedName("mqtt_host")
    private String host;

    @SerializedName("mqtt_port")
    private int port;

    @SerializedName("mqtt_tls")
    private Boolean tlsEnabled;

    @SerializedName("mqtt_client")
    private String deviceName;

    @SerializedName("mqtt_username")
    private String mqttUsername;

    @SerializedName("mqtt_password")
    private String mqttPassword;

    public ScannedSettings(){}

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Boolean getTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(Boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public void setMqttUsername(String mqttUsername) {
        this.mqttUsername = mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }
}
