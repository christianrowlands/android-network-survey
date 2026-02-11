package com.craxiom.networksurvey.constants;

/**
 * Constants for NS Analytics integration
 */
public class NsAnalyticsConstants
{
    // Preference Keys
    public static final String PROPERTY_NS_ANALYTICS_REGISTERED = "ns_analytics_registered";
    public static final String PROPERTY_NS_ANALYTICS_DEVICE_ID = "ns_analytics_device_id";
    public static final String PROPERTY_NS_ANALYTICS_DEVICE_TOKEN = "ns_analytics_device_token";
    public static final String PROPERTY_NS_ANALYTICS_WORKSPACE_ID = "ns_analytics_workspace_id";
    public static final String PROPERTY_NS_ANALYTICS_WORKSPACE_NAME = "ns_analytics_workspace_name";
    public static final String PROPERTY_NS_ANALYTICS_API_URL = "ns_analytics_api_url";
    public static final String PROPERTY_NS_ANALYTICS_URL = "ns_analytics_url";
    public static final String PROPERTY_NS_ANALYTICS_UPLOAD_FREQUENCY = "ns_analytics_upload_frequency";
    public static final String PROPERTY_NS_ANALYTICS_LAST_UPLOAD = "ns_analytics_last_upload";
    public static final String PROPERTY_NS_ANALYTICS_LAST_UPLOAD_WORK_ID = "ns_analytics_last_upload_work_id";
    public static final String PROPERTY_NS_ANALYTICS_LAST_UPLOAD_SUCCESS = "ns_analytics_last_upload_success";
    public static final String PROPERTY_NS_ANALYTICS_LAST_UPLOAD_RECORDS = "ns_analytics_last_upload_records";
    public static final String PROPERTY_NS_ANALYTICS_LAST_UPLOAD_ERROR_TYPE = "ns_analytics_last_upload_error_type";
    public static final String PROPERTY_NS_ANALYTICS_AUTO_UPLOAD = "ns_analytics_auto_upload";
    public static final String PROPERTY_NS_ANALYTICS_CELLULAR_ENABLED = "ns_analytics_cellular_enabled";
    public static final String PROPERTY_NS_ANALYTICS_WIFI_ENABLED = "ns_analytics_wifi_enabled";
    public static final String PROPERTY_NS_ANALYTICS_BLUETOOTH_ENABLED = "ns_analytics_bluetooth_enabled";
    public static final String PROPERTY_NS_ANALYTICS_GNSS_ENABLED = "ns_analytics_gnss_enabled";
    public static final String PROPERTY_NS_ANALYTICS_PHONE_STATE_ENABLED = "ns_analytics_phone_state_enabled";

    // Upload Frequencies (in minutes)
    public static final int UPLOAD_FREQUENCY_REAL_TIME = 0;
    public static final int UPLOAD_FREQUENCY_5_MIN = 5;
    public static final int UPLOAD_FREQUENCY_15_MIN = 15;
    public static final int UPLOAD_FREQUENCY_30_MIN = 30;
    public static final int UPLOAD_FREQUENCY_MANUAL = -1;

    // Default Values
    public static final boolean DEFAULT_CELLULAR_ENABLED = true;
    public static final boolean DEFAULT_PHONE_STATE_ENABLED = true;
    public static final boolean DEFAULT_WIFI_ENABLED = true;
    public static final boolean DEFAULT_BLUETOOTH_ENABLED = false;
    public static final boolean DEFAULT_GNSS_ENABLED = false;
    public static final int DEFAULT_UPLOAD_FREQUENCY = UPLOAD_FREQUENCY_15_MIN;
    public static final boolean DEFAULT_AUTO_UPLOAD_ENABLED = true;
    public static final int MAX_BATCH_SIZE = 1000; // Maximum records per upload batch
    public static final int MAX_RETRY_COUNT = 3;
    public static final long CLEANUP_AGE_DAYS = 7; // Clean up uploaded records after 7 days

    // Record Types
    public static final String RECORD_TYPE_GSM = "gsm";
    public static final String RECORD_TYPE_CDMA = "cdma";
    public static final String RECORD_TYPE_UMTS = "umts";
    public static final String RECORD_TYPE_LTE = "lte";
    public static final String RECORD_TYPE_NR = "nr";
    public static final String RECORD_TYPE_WIFI = "wifi";
    public static final String RECORD_TYPE_BLUETOOTH = "bluetooth";
    public static final String RECORD_TYPE_GNSS = "gnss";
    public static final String RECORD_TYPE_DEVICE_STATUS = "device_status";
    public static final String RECORD_TYPE_PHONE_STATE = "phone_state";

    // Worker Tags
    public static final String NS_ANALYTICS_UPLOAD_WORKER_TAG = "ns_analytics_upload_worker";
    public static final String NS_ANALYTICS_PERIODIC_WORKER_TAG = "ns_analytics_periodic_worker";

    // Notification IDs
    public static final int NS_ANALYTICS_NOTIFICATION_ID = 103;

    // Intent Actions
    public static final String ACTION_NS_ANALYTICS_REGISTERED = "com.craxiom.networksurvey.NS_ANALYTICS_REGISTERED";
    public static final String ACTION_NS_ANALYTICS_UPLOAD_COMPLETE = "com.craxiom.networksurvey.NS_ANALYTICS_UPLOAD_COMPLETE";
    public static final String ACTION_NS_ANALYTICS_UPLOAD_FAILED = "com.craxiom.networksurvey.NS_ANALYTICS_UPLOAD_FAILED";

    // Intent Extras
    public static final String EXTRA_UPLOAD_SUCCESS = "upload_success";
    public static final String EXTRA_RECORDS_UPLOADED = "records_uploaded";

    // Error Codes
    public static final String ERROR_CODE_DEVICE_DEREGISTERED = "DEVICE_DEREGISTERED";
    public static final String ERROR_CODE_INVALID_TOKEN = "INVALID_TOKEN";
    public static final String ERROR_CODE_QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String ERROR_CODE_REGISTRATION_INVALID = "REGISTRATION_INVALID";
    public static final String ERROR_CODE_VIEWER_CANNOT_UPLOAD = "VIEWER_CANNOT_UPLOAD";

    // Worker Output Data Keys
    public static final String ERROR_OUTPUT_KEY_TYPE = "error_type";
    public static final String ERROR_OUTPUT_KEY_MESSAGE = "error_message";

    // Quota Error Extras
    public static final String EXTRA_QUOTA_CURRENT_USAGE = "quota_current_usage";
    public static final String EXTRA_QUOTA_MAX_RECORDS = "quota_max_records";
    public static final String EXTRA_QUOTA_LIMIT_TYPE = "quota_limit_type";
    public static final String EXTRA_QUOTA_MESSAGE = "quota_message";
    public static final String EXTRA_QUOTA_WEB_URL = "quota_web_url";

    // Deep Link Configuration
    public static final String NS_ANALYTICS_DEEP_LINK_HOST = "networksurvey.app";
    public static final String NS_ANALYTICS_DEEP_LINK_PATH = "/app/register";

    // Query Parameter Keys for Deep Link
    public static final String DEEP_LINK_PARAM_TOKEN = "token";
    public static final String DEEP_LINK_PARAM_WORKSPACE_ID = "workspace_id";
    public static final String DEEP_LINK_PARAM_API_URL = "api_url";
    public static final String DEEP_LINK_PARAM_WORKSPACE_NAME = "workspace_name";

    private NsAnalyticsConstants()
    {
        // Private constructor to prevent instantiation
    }
}