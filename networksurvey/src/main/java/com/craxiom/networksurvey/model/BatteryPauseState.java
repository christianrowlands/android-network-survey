package com.craxiom.networksurvey.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

/**
 * Represents the state of all survey operations before a battery-triggered pause.
 * This class tracks which operations were active so they can be resumed when
 * the battery level rises above the threshold.
 */
public class BatteryPauseState implements Parcelable
{
    // Scanning states
    private boolean wasCellularScanningActive;
    private boolean wasWifiScanningActive;
    private boolean wasBluetoothScanningActive;
    private boolean wasGnssScanningActive;
    private boolean wasCdrScanningActive;

    // Logging states
    private boolean wasCellularLoggingEnabled;
    private boolean wasWifiLoggingEnabled;
    private boolean wasBluetoothLoggingEnabled;
    private boolean wasGnssLoggingEnabled;
    private boolean wasCdrLoggingEnabled;

    // Connection states
    private boolean wasMqttConnectionActive;
    private boolean wasGrpcConnectionActive;

    // Device status state
    private boolean wasDeviceStatusActive;

    // Constants for JSON serialization
    private static final String KEY_CELLULAR_SCANNING = "cellular_scanning";
    private static final String KEY_WIFI_SCANNING = "wifi_scanning";
    private static final String KEY_BLUETOOTH_SCANNING = "bluetooth_scanning";
    private static final String KEY_GNSS_SCANNING = "gnss_scanning";
    private static final String KEY_CDR_SCANNING = "cdr_scanning";
    private static final String KEY_CELLULAR_LOGGING = "cellular_logging";
    private static final String KEY_WIFI_LOGGING = "wifi_logging";
    private static final String KEY_BLUETOOTH_LOGGING = "bluetooth_logging";
    private static final String KEY_GNSS_LOGGING = "gnss_logging";
    private static final String KEY_CDR_LOGGING = "cdr_logging";
    private static final String KEY_MQTT_CONNECTION = "mqtt_connection";
    private static final String KEY_GRPC_CONNECTION = "grpc_connection";
    private static final String KEY_DEVICE_STATUS = "device_status";

    /**
     * Default constructor creates an empty state with all operations inactive.
     */
    public BatteryPauseState()
    {
        // All fields default to false
    }

    /**
     * Constructor for Parcelable implementation.
     */
    protected BatteryPauseState(Parcel in)
    {
        wasCellularScanningActive = in.readByte() != 0;
        wasWifiScanningActive = in.readByte() != 0;
        wasBluetoothScanningActive = in.readByte() != 0;
        wasGnssScanningActive = in.readByte() != 0;
        wasCdrScanningActive = in.readByte() != 0;
        wasCellularLoggingEnabled = in.readByte() != 0;
        wasWifiLoggingEnabled = in.readByte() != 0;
        wasBluetoothLoggingEnabled = in.readByte() != 0;
        wasGnssLoggingEnabled = in.readByte() != 0;
        wasCdrLoggingEnabled = in.readByte() != 0;
        wasMqttConnectionActive = in.readByte() != 0;
        wasGrpcConnectionActive = in.readByte() != 0;
        wasDeviceStatusActive = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeByte((byte) (wasCellularScanningActive ? 1 : 0));
        dest.writeByte((byte) (wasWifiScanningActive ? 1 : 0));
        dest.writeByte((byte) (wasBluetoothScanningActive ? 1 : 0));
        dest.writeByte((byte) (wasGnssScanningActive ? 1 : 0));
        dest.writeByte((byte) (wasCdrScanningActive ? 1 : 0));
        dest.writeByte((byte) (wasCellularLoggingEnabled ? 1 : 0));
        dest.writeByte((byte) (wasWifiLoggingEnabled ? 1 : 0));
        dest.writeByte((byte) (wasBluetoothLoggingEnabled ? 1 : 0));
        dest.writeByte((byte) (wasGnssLoggingEnabled ? 1 : 0));
        dest.writeByte((byte) (wasCdrLoggingEnabled ? 1 : 0));
        dest.writeByte((byte) (wasMqttConnectionActive ? 1 : 0));
        dest.writeByte((byte) (wasGrpcConnectionActive ? 1 : 0));
        dest.writeByte((byte) (wasDeviceStatusActive ? 1 : 0));
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    public static final Creator<BatteryPauseState> CREATOR = new Creator<BatteryPauseState>()
    {
        @Override
        public BatteryPauseState createFromParcel(Parcel in)
        {
            return new BatteryPauseState(in);
        }

        @Override
        public BatteryPauseState[] newArray(int size)
        {
            return new BatteryPauseState[size];
        }
    };

    /**
     * Serializes this state to a JSON string for persistence.
     *
     * @return JSON representation of the state, or null if serialization fails
     */
    public String toJson()
    {
        try
        {
            JSONObject json = new JSONObject();
            json.put(KEY_CELLULAR_SCANNING, wasCellularScanningActive);
            json.put(KEY_WIFI_SCANNING, wasWifiScanningActive);
            json.put(KEY_BLUETOOTH_SCANNING, wasBluetoothScanningActive);
            json.put(KEY_GNSS_SCANNING, wasGnssScanningActive);
            json.put(KEY_CDR_SCANNING, wasCdrScanningActive);
            json.put(KEY_CELLULAR_LOGGING, wasCellularLoggingEnabled);
            json.put(KEY_WIFI_LOGGING, wasWifiLoggingEnabled);
            json.put(KEY_BLUETOOTH_LOGGING, wasBluetoothLoggingEnabled);
            json.put(KEY_GNSS_LOGGING, wasGnssLoggingEnabled);
            json.put(KEY_CDR_LOGGING, wasCdrLoggingEnabled);
            json.put(KEY_MQTT_CONNECTION, wasMqttConnectionActive);
            json.put(KEY_GRPC_CONNECTION, wasGrpcConnectionActive);
            json.put(KEY_DEVICE_STATUS, wasDeviceStatusActive);
            return json.toString();
        } catch (JSONException e)
        {
            Timber.e(e, "Failed to serialize BatteryPauseState to JSON");
            return null;
        }
    }

    /**
     * Creates a BatteryPauseState from a JSON string.
     *
     * @param jsonString The JSON string to parse
     * @return The deserialized state, or a new empty state if parsing fails
     */
    public static BatteryPauseState fromJson(String jsonString)
    {
        if (jsonString == null || jsonString.isEmpty())
        {
            return new BatteryPauseState();
        }

        try
        {
            JSONObject json = new JSONObject(jsonString);
            BatteryPauseState state = new BatteryPauseState();

            state.wasCellularScanningActive = json.optBoolean(KEY_CELLULAR_SCANNING, false);
            state.wasWifiScanningActive = json.optBoolean(KEY_WIFI_SCANNING, false);
            state.wasBluetoothScanningActive = json.optBoolean(KEY_BLUETOOTH_SCANNING, false);
            state.wasGnssScanningActive = json.optBoolean(KEY_GNSS_SCANNING, false);
            state.wasCdrScanningActive = json.optBoolean(KEY_CDR_SCANNING, false);
            state.wasCellularLoggingEnabled = json.optBoolean(KEY_CELLULAR_LOGGING, false);
            state.wasWifiLoggingEnabled = json.optBoolean(KEY_WIFI_LOGGING, false);
            state.wasBluetoothLoggingEnabled = json.optBoolean(KEY_BLUETOOTH_LOGGING, false);
            state.wasGnssLoggingEnabled = json.optBoolean(KEY_GNSS_LOGGING, false);
            state.wasCdrLoggingEnabled = json.optBoolean(KEY_CDR_LOGGING, false);
            state.wasMqttConnectionActive = json.optBoolean(KEY_MQTT_CONNECTION, false);
            state.wasGrpcConnectionActive = json.optBoolean(KEY_GRPC_CONNECTION, false);
            state.wasDeviceStatusActive = json.optBoolean(KEY_DEVICE_STATUS, false);

            return state;
        } catch (JSONException e)
        {
            Timber.e(e, "Failed to deserialize BatteryPauseState from JSON");
            return new BatteryPauseState();
        }
    }

    /**
     * Checks if any operation was active before the pause.
     *
     * @return true if at least one operation was active
     */
    public boolean hasActiveOperations()
    {
        return wasCellularScanningActive || wasWifiScanningActive || wasBluetoothScanningActive ||
                wasGnssScanningActive || wasCdrScanningActive || wasCellularLoggingEnabled ||
                wasWifiLoggingEnabled || wasBluetoothLoggingEnabled || wasGnssLoggingEnabled ||
                wasCdrLoggingEnabled || wasMqttConnectionActive || wasGrpcConnectionActive ||
                wasDeviceStatusActive;
    }

    /**
     * Resets all states to inactive.
     */
    public void clear()
    {
        wasCellularScanningActive = false;
        wasWifiScanningActive = false;
        wasBluetoothScanningActive = false;
        wasGnssScanningActive = false;
        wasCdrScanningActive = false;
        wasCellularLoggingEnabled = false;
        wasWifiLoggingEnabled = false;
        wasBluetoothLoggingEnabled = false;
        wasGnssLoggingEnabled = false;
        wasCdrLoggingEnabled = false;
        wasMqttConnectionActive = false;
        wasGrpcConnectionActive = false;
        wasDeviceStatusActive = false;
    }

    // Getters and setters for all fields
    public boolean wasCellularScanningActive()
    {
        return wasCellularScanningActive;
    }

    public void setWasCellularScanningActive(boolean wasCellularScanningActive)
    {
        this.wasCellularScanningActive = wasCellularScanningActive;
    }

    public boolean wasWifiScanningActive()
    {
        return wasWifiScanningActive;
    }

    public void setWasWifiScanningActive(boolean wasWifiScanningActive)
    {
        this.wasWifiScanningActive = wasWifiScanningActive;
    }

    public boolean wasBluetoothScanningActive()
    {
        return wasBluetoothScanningActive;
    }

    public void setWasBluetoothScanningActive(boolean wasBluetoothScanningActive)
    {
        this.wasBluetoothScanningActive = wasBluetoothScanningActive;
    }

    public boolean wasGnssScanningActive()
    {
        return wasGnssScanningActive;
    }

    public void setWasGnssScanningActive(boolean wasGnssScanningActive)
    {
        this.wasGnssScanningActive = wasGnssScanningActive;
    }

    public boolean wasCdrScanningActive()
    {
        return wasCdrScanningActive;
    }

    public void setWasCdrScanningActive(boolean wasCdrScanningActive)
    {
        this.wasCdrScanningActive = wasCdrScanningActive;
    }

    public boolean wasCellularLoggingEnabled()
    {
        return wasCellularLoggingEnabled;
    }

    public void setWasCellularLoggingEnabled(boolean wasCellularLoggingEnabled)
    {
        this.wasCellularLoggingEnabled = wasCellularLoggingEnabled;
    }

    public boolean wasWifiLoggingEnabled()
    {
        return wasWifiLoggingEnabled;
    }

    public void setWasWifiLoggingEnabled(boolean wasWifiLoggingEnabled)
    {
        this.wasWifiLoggingEnabled = wasWifiLoggingEnabled;
    }

    public boolean wasBluetoothLoggingEnabled()
    {
        return wasBluetoothLoggingEnabled;
    }

    public void setWasBluetoothLoggingEnabled(boolean wasBluetoothLoggingEnabled)
    {
        this.wasBluetoothLoggingEnabled = wasBluetoothLoggingEnabled;
    }

    public boolean wasGnssLoggingEnabled()
    {
        return wasGnssLoggingEnabled;
    }

    public void setWasGnssLoggingEnabled(boolean wasGnssLoggingEnabled)
    {
        this.wasGnssLoggingEnabled = wasGnssLoggingEnabled;
    }

    public boolean wasCdrLoggingEnabled()
    {
        return wasCdrLoggingEnabled;
    }

    public void setWasCdrLoggingEnabled(boolean wasCdrLoggingEnabled)
    {
        this.wasCdrLoggingEnabled = wasCdrLoggingEnabled;
    }

    public boolean wasMqttConnectionActive()
    {
        return wasMqttConnectionActive;
    }

    public void setWasMqttConnectionActive(boolean wasMqttConnectionActive)
    {
        this.wasMqttConnectionActive = wasMqttConnectionActive;
    }

    public boolean wasGrpcConnectionActive()
    {
        return wasGrpcConnectionActive;
    }

    public void setWasGrpcConnectionActive(boolean wasGrpcConnectionActive)
    {
        this.wasGrpcConnectionActive = wasGrpcConnectionActive;
    }

    public boolean wasDeviceStatusActive()
    {
        return wasDeviceStatusActive;
    }

    public void setWasDeviceStatusActive(boolean wasDeviceStatusActive)
    {
        this.wasDeviceStatusActive = wasDeviceStatusActive;
    }
}