package com.craxiom.networksurvey.constants;

/**
 * Some constants used in the Network Survey App.
 *
 * @since 0.0.4
 */
public class NetworkSurveyConstants
{
    private NetworkSurveyConstants()
    {
    }

    public static final int DEFAULT_GRPC_PORT = 2621;

    public static final int MQTT_PLAIN_TEXT_PORT = 1883;
    public static final int MQTT_SSL_PORT = 8883;
    public static final int DEFAULT_MQTT_PORT = MQTT_SSL_PORT;
    public static final boolean DEFAULT_MQTT_TLS_SETTING = true;

    public static final String NOTIFICATION_CHANNEL_ID = "network_survey_notification";
    public static final int GRPC_CONNECTION_NOTIFICATION_ID = 3;
    public static final int LOGGING_NOTIFICATION_ID = 1;

    public static final String GPRS = "GRPS";
    public static final String EDGE = "EDGE";
    public static final String UMTS = "UMTS";
    public static final String CDMA = "CDMA";
    public static final String EVDO_0 = "EVDO 0";
    public static final String EVDO_A = "EVDO A";
    public static final String RTT1x = "CDMA - 1xRTT";
    public static final String HSDPA = "HSDPA";
    public static final String HSUPA = "HSUPA";
    public static final String HSPA = "HSPA";
    public static final String IDEN = "IDEN";
    public static final String EVDO_B = "EVDO B";
    public static final String LTE = "LTE";
    public static final String EHRPD = "CDMA - eHRPD";
    public static final String HSPAP = "HSPA+";
    public static final String GSM = "GSM";
    public static final String TD_SCDMA = "TD-SCDMA";
    public static final String IWLAN = "IWLAN";
    public static final String LTE_CA = "LTE-CA";
    public static final String NR = "NR";

    /**
     * The key for the Intent extra that indicates the {@link com.craxiom.networksurvey.services.NetworkSurveyService}
     * is being started at boot.
     *
     * @since 0.1.1
     */
    public static final String EXTRA_STARTED_AT_BOOT = "com.craxiom.networksurvey.extra.STARTED_AT_BOOT";

    // Preferences
    public static final String PROPERTY_AUTO_START_CELLULAR_LOGGING = "auto_start_logging";
    public static final String PROPERTY_AUTO_START_GNSS_LOGGING = "auto_start_gnss_logging";
    public static final String PROPERTY_CONNECTION_TIMEOUT = "connection_timeout";
    public static final String PROPERTY_MQTT_MDM_OVERRIDE = "mqtt_mdm_override";
    // The following keys are used in the app_restrictions.xml file as well.
    public static final String PROPERTY_MQTT_START_ON_BOOT = "mqtt_start_on_boot";
    public static final String PROPERTY_MQTT_BROKER_URL = "mqtt_broker_url";
    public static final String PROPERTY_MQTT_CLIENT_ID = "mqtt_client_id";
    public static final String PROPERTY_MQTT_USERNAME = "mqtt_username";
    public static final String PROPERTY_MQTT_PASSWORD = "mqtt_password";

    // Stored Preferences not exposed via the Settings UI
    public static final String PROPERTY_NETWORK_SURVEY_CONNECTION_HOST = "connection_host";
    public static final String PROPERTY_NETWORK_SURVEY_CONNECTION_PORT = "connection_port";
    public static final String PROPERTY_NETWORK_SURVEY_DEVICE_NAME = "device_name";

    public static final String PROPERTY_MQTT_CONNECTION_HOST = "mqtt_connection_host";
    public static final String PROPERTY_MQTT_CONNECTION_PORT = "mqtt_connection_port";
    public static final String PROPERTY_MQTT_CONNECTION_TLS_ENABLED = "mqtt_tls_enabled";
    public static final String PROPERTY_MQTT_DEVICE_NAME = "mqtt_device_name";
}
