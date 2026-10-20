package com.craxiom.networksurvey.constants;

import android.telephony.CellInfo;

import com.craxiom.mqttlibrary.MqttConstants;

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
    public static final boolean DEFAULT_MQTT_PHONE_STATE_STREAM_SETTING = true;
    public static final boolean DEFAULT_MQTT_WIFI_STREAM_SETTING = true;
    public static final boolean DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING = false;
    public static final boolean DEFAULT_MQTT_GNSS_STREAM_SETTING = false;
    public static final boolean DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING = true;
    public static final boolean DEFAULT_MQTT_WATCHLIST_STREAM_SETTING = true;

    // Notification channel ids. These are persisted by the OS along with the user's per-channel
    // settings, so they must never change once shipped. Channel display names live in strings.xml.
    public static final String NOTIFICATION_CHANNEL_ID = "network_survey_notification";
    public static final String UPLOADER_NOTIFICATION_CHANNEL_ID = "uploader_notification_channel";
    public static final String NEW_TOWER_NOTIFICATION_CHANNEL_ID = "new_tower_alerts";
    // A channel that older versions created for the pre-Android 12 expedited upload worker. It is
    // deleted on startup so it no longer appears in the system notification settings.
    public static final String LEGACY_UPLOAD_WORKER_CHANNEL_ID = "ns_upload_channel";

    // Notification ids. The survey notification (id 1) is the single ongoing notification for the
    // NetworkSurveyService and reflects every active survey output, including the gRPC connection.
    public static final int LOGGING_NOTIFICATION_ID = 1;
    public static final int UPLOADER_NOTIFICATION_ID = 102;
    public static final int NEW_TOWER_NOTIFICATION_ID = 104;

    // Intent extra used by the survey and upload notifications to land the user on the dashboard.
    public static final String EXTRA_NAVIGATE_TO_DASHBOARD = "navigate_to_dashboard";

    // Intent extra used by the new-tower notification to land the user on the Map tab.
    public static final String EXTRA_NAVIGATE_TO_MAP = "navigate_to_map";

    public static final String WAKE_LOCK_TAG = "NetworkSurvey:WakeLock";

    public static final String LOG_DIRECTORY_NAME = "NetworkSurveyData";
    public static final String CSV_LOG_DIRECTORY_NAME = LOG_DIRECTORY_NAME + "/csv";

    public static final String GSM_FILE_NAME_PREFIX = "craxiom-gsm-";
    public static final String CDMA_FILE_NAME_PREFIX = "craxiom-cdma-";
    public static final String UMTS_FILE_NAME_PREFIX = "craxiom-umts-";
    public static final String LTE_FILE_NAME_PREFIX = "craxiom-lte-";
    public static final String NR_FILE_NAME_PREFIX = "craxiom-nr-";
    public static final String WIFI_FILE_NAME_PREFIX = "craxiom-wifi-";
    public static final String BLUETOOTH_FILE_NAME_PREFIX = "craxiom-bluetooth-";
    public static final String GNSS_FILE_NAME_PREFIX = "craxiom-gnss-";
    public static final String CDR_FILE_NAME_PREFIX = "craxiom-cdr-";
    public static final String PHONESTATE_FILE_NAME_PREFIX = "craxiom-phonestate-";
    public static final String DEVICESTATUS_FILE_NAME_PREFIX = "craxiom-devicestatus-";

    /**
     * Prefix used when generating a Mission ID. A Mission ID has the form
     * "NS &lt;deviceId&gt; &lt;yyyyMMdd-HHmmss&gt;" where the trailing timestamp is the survey
     * session start time. This prefix is shared by the service that generates the Mission ID and
     * the processor that stamps it on records.
     */
    public static final String MISSION_ID_PREFIX = "NS ";

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
    public static final String UNKNOWN = "Unknown";

    // Inferred voice bearer labels and the 5G Non-Standalone acronym (not translated).
    public static final String VOLTE = "VoLTE";
    public static final String VONR = "VoNR";
    public static final String NSA = "NSA";

    /**
     * AKA {@link CellInfo#UNAVAILABLE}, but I am not using that because it was added in API level 29.
     *
     * @since 1.6.0
     */
    public static final int UNSET_VALUE = Integer.MAX_VALUE;

    /**
     * The key for the Intent extra that indicates the {@link com.craxiom.networksurvey.services.NetworkSurveyService}
     * is being started at boot.
     *
     * @since 0.1.1
     */
    public static final String EXTRA_STARTED_AT_BOOT = "com.craxiom.networksurvey.extra.STARTED_AT_BOOT";

    public static final String EXTRA_CELLULAR_FILE_LOGGING = "cellular_file_logging";
    public static final String EXTRA_PHONE_STATE_FILE_LOGGING = "phone_state_file_logging";
    public static final String EXTRA_WIFI_FILE_LOGGING = "wifi_file_logging";
    public static final String EXTRA_BLUETOOTH_FILE_LOGGING = "bluetooth_file_logging";
    public static final String EXTRA_GNSS_FILE_LOGGING = "gnss_file_logging";
    public static final String EXTRA_CDR_FILE_LOGGING = "cdr_file_logging";
    public static final String EXTRA_MQTT_CONFIG_JSON = "mqtt_config_json";
    /**
     * Intent API START extra: the record types to survey, as a string array or a comma separated
     * string of wire names (cellular, phone_state, wifi, bluetooth, gnss, cdr). Every destination
     * receives every record type. Preferred over the per-destination *_file_logging extras.
     */
    public static final String EXTRA_RECORD_TYPES = "record_types";
    /**
     * Intent API START extra: the destinations, as a string array or a comma separated string of
     * wire names (log_files, ns_analytics, mqtt, grpc, community).
     */
    public static final String EXTRA_DESTINATIONS = "destinations";

    public static final int DEFAULT_CELLULAR_SCAN_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_WIFI_SCAN_INTERVAL_SECONDS = 15;
    public static final int DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS = 30;
    public static final int DEFAULT_GNSS_SCAN_INTERVAL_SECONDS = 45;
    public static final int DEFAULT_DEVICE_STATUS_SCAN_INTERVAL_SECONDS = 120;

    /**
     * Minimum Bluetooth scan interval in seconds. The Bluetooth scanning process requires
     * at least 24 seconds: 10 seconds for BLE scanning, 12 seconds for Classic Bluetooth
     * discovery, a 1 second buffer so the inquiry reaches its natural end, and 1 second
     * minimum wait between cycles. Values below that floor behave identically to the floor.
     */
    public static final int MINIMUM_BLUETOOTH_SCAN_INTERVAL_SECONDS = 24;

    public static final String DEFAULT_ROLLOVER_SIZE_MB = "10";

    public static final boolean DEFAULT_INCLUDE_NEIGHBOR_CELLS = true;

    public static final boolean DEFAULT_UPLOAD_TO_OPENCELLID = true;
    public static final boolean DEFAULT_UPLOAD_TO_BEACONDB = true;
    public static final boolean DEFAULT_UPLOAD_RETRY_ENABLED = true;

    public static final String DEFAULT_COVERAGE_CIRCLE_COLOR = "default";

    public static final int LOCATION_PROVIDER_FUSED = 0;
    public static final int LOCATION_PROVIDER_GNSS = 1;
    public static final int LOCATION_PROVIDER_NETWORK = 2;
    public static final int LOCATION_PROVIDER_ALL = 3;
    public static final int DEFAULT_LOCATION_PROVIDER = LOCATION_PROVIDER_ALL;

    public static final String PROPERTY_MDM_OVERRIDE_KEY = MqttConstants.PROPERTY_MQTT_MDM_OVERRIDE;

    // MDM Only Preferences
    public static final String MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD = "allow_external_data_upload";
    public static final String MDM_PROPERTY_ALLOW_NS_ANALYTICS = "allow_ns_analytics";
    public static final String PROPERTY_MDM_MQTT_DEVICE_NAME = "mqtt_device_name";

    // Preferences
    // The pre-v2 per-record-type auto start keys. They are no longer user settings; they are only
    // read as deprecated MDM keys (LegacyMdmMapper) and by the one-time setup migration. The survey
    // now starts automatically through the single auto_start_survey setting (SurveyMdmKeys).
    public static final String PROPERTY_AUTO_START_CELLULAR_LOGGING = "auto_start_logging";
    public static final String PROPERTY_AUTO_START_WIFI_LOGGING = "auto_start_wifi_logging";
    public static final String PROPERTY_AUTO_START_BLUETOOTH_LOGGING = "auto_start_bluetooth_logging";
    public static final String PROPERTY_AUTO_START_GNSS_LOGGING = "auto_start_gnss_logging";
    public static final String PROPERTY_AUTO_START_CDR_LOGGING = "auto_start_cdr_logging";
    public static final String PROPERTY_AUTO_START_PHONE_STATE_LOGGING = "auto_start_phone_state_logging";
    public static final String PROPERTY_AUTO_INCLUDE_PHONE_STATE_WITH_CELLULAR = "auto_include_phone_state_with_cellular";
    public static final String PROPERTY_INCLUDE_NEIGHBOR_CELLS = "include_neighbor_cells";
    public static final String PROPERTY_AUTO_START_COMMUNITY_UPLOAD_SURVEY_AT_BOOT = "auto_start_community_upload_survey_at_boot";
    public static final String PROPERTY_CELLULAR_SCAN_INTERVAL_SECONDS = "cellular_scan_interval_seconds";
    public static final String PROPERTY_WIFI_SCAN_INTERVAL_SECONDS = "wifi_scan_interval_seconds";
    public static final String PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS = "bluetooth_scan_interval_seconds";
    public static final String PROPERTY_GNSS_SCAN_INTERVAL_SECONDS = "gnss_scan_interval_seconds";
    public static final String PROPERTY_DEVICE_STATUS_SCAN_INTERVAL_SECONDS = "device_status_scan_interval_seconds";
    public static final String PROPERTY_LOG_ROLLOVER_SIZE_MB = "log_rollover_size_mb";
    public static final String PROPERTY_LOCATION_PROVIDER = "location_provider";
    public static final String PROPERTY_ALLOW_INTENT_CONTROL = "allow_intent_control";
    public static final String PROPERTY_IGNORE_WIFI_SCAN_THROTTLING_WARNING = "ignore_wifi_scan_throttling_warning";

    // OUI lookup: server-backed manufacturer resolution for WiFi/BT details screens.
    // UI-only; never written to CSV, protobuf, MQTT, or gRPC outputs.
    public static final String PROPERTY_OUI_LOOKUP_ENABLED = "oui_lookup_enabled";
    public static final boolean DEFAULT_OUI_LOOKUP_ENABLED = true;

    // Watchlist: alert the user when a saved Wi-Fi network (by SSID and/or BSSID) is seen, and keep
    // a browsable history of past sightings. UI/notification feature only; never written to CSV,
    // protobuf, MQTT, or gRPC survey outputs.
    public static final String PROPERTY_WATCHLIST_ENABLED = "watchlist_enabled";
    public static final boolean DEFAULT_WATCHLIST_ENABLED = false;
    public static final String PROPERTY_WATCHLIST_ABSENCE_WINDOW_SECONDS = "watchlist_absence_window_seconds";
    public static final String PROPERTY_WATCHLIST_DEFAULT_COOLDOWN_SECONDS = "watchlist_default_cooldown_seconds";
    public static final String PROPERTY_WATCHLIST_HISTORY_RETENTION_DAYS = "watchlist_history_retention_days";
    // Intent extra used to deep-link from a watchlist notification to the history screen.
    public static final String EXTRA_NAVIGATE_TO_WATCHLIST_HISTORY = "navigate_to_watchlist_history";
    // App Link used to populate the watchlist from a shared URL. The host is the same already-verified
    // networksurvey.app domain used by the NS Analytics registration link (NsAnalyticsConstants), so no
    // additional Digital Asset Links verification is needed. The single "d" query parameter carries a
    // base64url-encoded JSON payload of the networks to add.
    public static final String WATCHLIST_DEEP_LINK_PATH = "/app/watchlist";
    public static final String WATCHLIST_DEEP_LINK_PARAM_DATA = "d";
    // The link that opens the gRPC page (the manifest declares the same host and path).
    public static final String GRPC_DEEP_LINK_HOST = "craxiom.com";
    public static final String GRPC_DEEP_LINK_PATH = "/grpc_server_connection";

    // A read only value in the preferences that shows the Device ID, which is called the Device Serial Number in NS Messaging API
    public static final String PREFERENCE_DEVICE_SERIAL_NUMBER = "preference_device_serial_number";
    // A read only value in the preferences that shows the App Instance ID if applicable
    public static final String PROPERTY_APP_INSTANCE_ID = "app_instance_id";

    public static final String TOWER_MAP_PREFERENCES_GROUP = "tower_map_preferences_group";
    public static final String PROPERTY_MAP_KEEP_SCREEN_ON = "tower_map_keep_screen_on";
    public static final String PROPERTY_MAP_DISPLAY_SERVING_CELL_COVERAGE = "map_display_serving_cell_coverage_area";
    public static final String PROPERTY_MAP_COVERAGE_CIRCLE_COLOR = "map_coverage_circle_color";
    public static final String PROPERTY_MAP_COVERAGE_CIRCLE_OPACITY = "map_coverage_circle_opacity";
    public static final String PROPERTY_MAP_SHOW_ZOOM_CONTROLS = "map_show_zoom_controls";
    public static final String PROPERTY_MANAGE_PROVIDER_COLOR_OVERRIDES = "manage_provider_color_overrides";

    // The following key is used in the app_restrictions.xml file and in the app's shared preferences
    public static final String PROPERTY_MQTT_START_ON_BOOT = "mqtt_start_on_boot";

    public static final String PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG = "show_configuration_upload_dialog";
    public static final String PROPERTY_UPLOAD_TO_OPENCELLID = "upload_to_opencellid";
    public static final String PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD = "anonymous_opencellid_upload";
    public static final String PROPERTY_UPLOAD_TO_BEACONDB = "upload_to_beacondb";
    public static final String PROPERTY_UPLOAD_RETRY_ENABLED = "upload_retry_enabled";
    public static final String PROPERTY_OCID_API_KEY = "ocid_api_key";
    public static final String UPLOAD_PREFERENCES_GROUP = "upload_preferences_group";

    // Auto Upload preferences
    public static final String PROPERTY_AUTO_UPLOAD_ENABLED = "auto_upload_enabled";
    public static final String PROPERTY_AUTO_UPLOAD_WIFI_ONLY = "auto_upload_wifi_only";
    public static final int AUTO_UPLOAD_RECORD_THRESHOLD = 500;
    public static final long AUTO_UPLOAD_COOLDOWN_MS = 15 * 60 * 1000L;
    public static final int AUTO_UPLOAD_MAX_CONSECUTIVE_FAILURES = 3;
    public static final String COMMUNITY_UPLOAD_UNIQUE_WORK_NAME = "community_upload_work";
    public static final String PROPERTY_NS_ANALYTICS_CONNECTION = "ns_analytics_connection_pref";
    public static final String PROPERTY_DELETE_ALL_DATA_IN_UPLOAD_DATABASE = "delete_all_data_in_upload_database";

    // The pre-v2 MQTT record type stream keys. Every destination now receives every record type the
    // survey records, so these are only read as deprecated MDM keys (LegacyMdmMapper) and by the
    // one-time setup migration. The device status and watchlist stream keys are still in use.
    public static final String PROPERTY_MQTT_CELLULAR_STREAM_ENABLED = "cellular_stream_enabled";
    public static final String PROPERTY_MQTT_WIFI_STREAM_ENABLED = "wifi_stream_enabled";
    public static final String PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED = "bluetooth_stream_enabled";
    public static final String PROPERTY_MQTT_GNSS_STREAM_ENABLED = "gnss_stream_enabled";
    public static final String PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED = "device_status_stream_enabled";
    public static final String PROPERTY_MQTT_PHONE_STATE_STREAM_ENABLED = "phone_state_stream_enabled";
    public static final String PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED = "watchlist_stream_enabled";

    // The saved MQTT broker profiles (JSON), the id of the selected one, and the storage version.
    // These replace the single broker that was stored in the flat mqtt_connection_* keys.
    public static final String PROPERTY_MQTT_BROKER_PROFILES = "mqtt_broker_profiles";
    // The Settings row that opens the Organization settings page (managed phones only)
    public static final String PROPERTY_ORGANIZATION_SETTINGS = "organization_settings";
    public static final String PROPERTY_MQTT_ACTIVE_BROKER_PROFILE = "mqtt_active_broker_profile";
    public static final String PROPERTY_MQTT_BROKER_PROFILES_SCHEMA_VERSION = "mqtt_broker_profiles_schema_version";


    // Stored Preferences not exposed via the Settings UI
    public static final String PROPERTY_NETWORK_SURVEY_CONNECTION_HOST = "connection_host";
    public static final String PROPERTY_NETWORK_SURVEY_CONNECTION_PORT = "connection_port";
    public static final String PROPERTY_NETWORK_SURVEY_DEVICE_NAME = "device_name";

    public static final String PROPERTY_WIFI_NETWORKS_SORT_ORDER = "wifi_networks_sort_order";
    public static final String PROPERTY_WIFI_GROUP_BY_SSID = "wifi_group_by_ssid";
    public static final String PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER = "bluetooth_devices_sort_order";
    /** The GNSS sort, as an Int index into R.array.sort_sats. */
    public static final String PROPERTY_GNSS_SORT_INDEX = "preference_sat_sort_index";
    public static final String PROPERTY_KEY_DENIED_BACKGROUND_LOCATION_PERMISSION = "denied_background_location_permission";

    public static final String PROPERTY_LAST_SELECTED_TOWER_SOURCE = "last_selected_tower_source";
    public static final String PROPERTY_SELECTED_MAP_TILE_SOURCE = "selected_map_tile_source";
    public static final String PROPERTY_SHOW_BEACONDB_COVERAGE = "show_beacondb_coverage";
    public static final String PROPERTY_SHOW_TOWERS_LAYER = "show_towers_layer";
    public static final String PROPERTY_MAP_SHOW_ONLY_SERVING_CELL = "map_show_only_serving_cell";
    /**
     * Maximum tower age (in months) used to hide stale towers on the Map. 0 means "show all" (no
     * filter).
     */
    public static final String PROPERTY_MAP_MAX_TOWER_AGE_MONTHS = "map_max_tower_age_months";
    public static final String PROPERTY_NEW_TOWER_ALERTS_ENABLED = "new_tower_alerts_enabled";

    // The survey points layer on the Map
    public static final String PROPERTY_SHOW_SURVEY_POINTS = "show_survey_points";
    public static final String PROPERTY_SURVEY_POINTS_COLOR_MODE = "survey_points_color_mode";
    public static final String PROPERTY_SURVEY_POINTS_KIND = "survey_points_kind";
    public static final String PROPERTY_CLEAR_SURVEY_POINTS = "clear_survey_points";

    public static final String TOWER_MAP_SHARED_PREFERENCES = "tower_map_prefs";
    public static final String PROPERTY_LAST_TOWER_MAP_VIEW_LOCATION = "last_tower_map_view_location";

    // Battery Management preferences
    public static final String PROPERTY_BATTERY_THRESHOLD_PERCENT = "battery_threshold_percent";
    public static final int DEFAULT_BATTERY_THRESHOLD_PERCENT = 0;
    /** The Survey screen warns once the battery is within this many percent of the pause threshold. */
    public static final int BATTERY_WARNING_MARGIN_PERCENT = 5;

    // Streaming Queue Management preferences
    /** Keep the screen awake on the Survey screen and the survey map while a survey records. */
    public static final String PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING = "keep_screen_on_while_recording";
    public static final boolean DEFAULT_KEEP_SCREEN_ON_WHILE_RECORDING = true;

    public static final String PROPERTY_STREAMING_QUEUE_LIMIT = "streaming_queue_limit";
    public static final int DEFAULT_STREAMING_QUEUE_LIMIT = 20000;
    public static final int HIGH_STREAMING_QUEUE_LIMIT_THRESHOLD = 50000;
    /**
     * Sentinel value that disables the streaming queue limit, resulting in an unbounded queue
     * (no backpressure, scanning never pauses due to queue fullness).
     */
    public static final int STREAMING_QUEUE_LIMIT_DISABLED = 0;

    // Display Settings preferences
    public static final String PROPERTY_MEASUREMENT_UNITS = "measurement_units";
    public static final String MEASUREMENT_UNITS_METRIC = "metric";
    public static final String MEASUREMENT_UNITS_IMPERIAL = "imperial";
    public static final String DEFAULT_MEASUREMENT_UNITS = MEASUREMENT_UNITS_METRIC;
    public static final String PROPERTY_APP_THEME = "app_theme";
    public static final String APP_THEME_SYSTEM = "system";
    public static final String APP_THEME_LIGHT = "light";
    public static final String APP_THEME_DARK = "dark";
    public static final String DEFAULT_APP_THEME = APP_THEME_SYSTEM;

    // Permission request IDs
    public static final int ACCESS_REQUIRED_PERMISSION_REQUEST_ID = 20;
    public static final int ACCESS_OPTIONAL_PERMISSION_REQUEST_ID = 21;
    public static final int ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID = 22;
}
