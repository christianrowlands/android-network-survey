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

    public static final boolean DEFAULT_MQTT_CELLULAR_STREAM_SETTING = true;
    public static final boolean DEFAULT_MQTT_WIFI_STREAM_SETTING = true;
    public static final boolean DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING = true;
    public static final boolean DEFAULT_MQTT_GNSS_STREAM_SETTING = true;

    public static final String NOTIFICATION_CHANNEL_ID = "network_survey_notification";
    public static final int GRPC_CONNECTION_NOTIFICATION_ID = 3;
    public static final int LOGGING_NOTIFICATION_ID = 1;

    public static final String LOG_DIRECTORY_NAME = "NetworkSurveyData";

    public static final String CELLULAR_FILE_NAME_PREFIX = "craxiom-cellular-";
    public static final String WIFI_FILE_NAME_PREFIX = "craxiom-wifi-";
    public static final String BLUETOOTH_FILE_NAME_PREFIX = "craxiom-bluetooth-";
    public static final String GNSS_FILE_NAME_PREFIX = "craxiom-gnss-";

    public static final String GPRS = "GPRS";
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

    public static final int DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_WIFI_SCAN_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_GNSS_SCAN_INTERVAL_SECONDS = 8;

    public static final String DEFAULT_ROLLOVER_SIZE_MB = "5";

    public static final String PROPERTY_MDM_OVERRIDE_KEY = "mdm_override";

    // Preferences
    public static final String PROPERTY_AUTO_START_CELLULAR_LOGGING = "auto_start_logging";
    public static final String PROPERTY_AUTO_START_WIFI_LOGGING = "auto_start_wifi_logging";
    public static final String PROPERTY_AUTO_START_GNSS_LOGGING = "auto_start_gnss_logging";
    public static final String PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS = "cellular_scan_interval_seconds";
    public static final String PROPERTY_WIFI_SCAN_INTERVAL_SECONDS = "wifi_scan_interval_seconds";
    public static final String PROPERTY_GNSS_SCAN_INTERVAL_SECONDS = "gnss_scan_interval_seconds";
    public static final String PROPERTY_LOG_ROLLOVER_SIZE_MB = "log_rollover_size_mb";

    public static final String PROPERTY_MQTT_MDM_OVERRIDE = "mqtt_mdm_override";

    // The following key is used in the app_restrictions.xml file and in the app's shared preferences
    public static final String PROPERTY_MQTT_START_ON_BOOT = "mqtt_start_on_boot";

    public static final String PROPERTY_MQTT_CELLULAR_STREAM_ENABLED = "cellular_stream_enabled";
    public static final String PROPERTY_MQTT_WIFI_STREAM_ENABLED = "wifi_stream_enabled";
    public static final String PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED = "bluetooth_stream_enabled";
    public static final String PROPERTY_MQTT_GNSS_STREAM_ENABLED = "gnss_stream_enabled";

    // Stored Preferences not exposed via the Settings UI
    public static final String PROPERTY_NETWORK_SURVEY_CONNECTION_HOST = "connection_host";
    public static final String PROPERTY_NETWORK_SURVEY_CONNECTION_PORT = "connection_port";
    public static final String PROPERTY_NETWORK_SURVEY_DEVICE_NAME = "device_name";

    public static final String PROPERTY_WIFI_NETWORKS_SORT_ORDER = "wifi_networks_sort_order";
}
