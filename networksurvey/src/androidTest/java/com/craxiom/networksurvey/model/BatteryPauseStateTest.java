package com.craxiom.networksurvey.model;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

/**
 * Instrumented tests for BatteryPauseState model class.
 * Tests JSON serialization/deserialization, state tracking, and Parcelable implementation.
 */
@RunWith(AndroidJUnit4.class)
public class BatteryPauseStateTest
{
    @Test
    public void testDefaultConstructor()
    {
        BatteryPauseState state = new BatteryPauseState();
        
        // All states should be false by default
        assertThat(state.wasCellularScanningActive()).isFalse();
        assertThat(state.wasWifiScanningActive()).isFalse();
        assertThat(state.wasBluetoothScanningActive()).isFalse();
        assertThat(state.wasGnssScanningActive()).isFalse();
        assertThat(state.wasCdrScanningActive()).isFalse();
        assertThat(state.wasCellularLoggingEnabled()).isFalse();
        assertThat(state.wasWifiLoggingEnabled()).isFalse();
        assertThat(state.wasBluetoothLoggingEnabled()).isFalse();
        assertThat(state.wasGnssLoggingEnabled()).isFalse();
        assertThat(state.wasCdrLoggingEnabled()).isFalse();
        assertThat(state.wasMqttConnectionActive()).isFalse();
        assertThat(state.wasGrpcConnectionActive()).isFalse();
        assertThat(state.wasDeviceStatusActive()).isFalse();
        assertThat(state.hasActiveOperations()).isFalse();
    }

    @Test
    public void testSettersAndGetters()
    {
        BatteryPauseState state = new BatteryPauseState();
        
        // Test scanning states
        state.setWasCellularScanningActive(true);
        assertThat(state.wasCellularScanningActive()).isTrue();
        
        state.setWasWifiScanningActive(true);
        assertThat(state.wasWifiScanningActive()).isTrue();
        
        state.setWasBluetoothScanningActive(true);
        assertThat(state.wasBluetoothScanningActive()).isTrue();
        
        state.setWasGnssScanningActive(true);
        assertThat(state.wasGnssScanningActive()).isTrue();
        
        state.setWasCdrScanningActive(true);
        assertThat(state.wasCdrScanningActive()).isTrue();
        
        // Test logging states
        state.setWasCellularLoggingEnabled(true);
        assertThat(state.wasCellularLoggingEnabled()).isTrue();
        
        state.setWasWifiLoggingEnabled(true);
        assertThat(state.wasWifiLoggingEnabled()).isTrue();
        
        state.setWasBluetoothLoggingEnabled(true);
        assertThat(state.wasBluetoothLoggingEnabled()).isTrue();
        
        state.setWasGnssLoggingEnabled(true);
        assertThat(state.wasGnssLoggingEnabled()).isTrue();
        
        state.setWasCdrLoggingEnabled(true);
        assertThat(state.wasCdrLoggingEnabled()).isTrue();
        
        // Test connection states
        state.setWasMqttConnectionActive(true);
        assertThat(state.wasMqttConnectionActive()).isTrue();
        
        state.setWasGrpcConnectionActive(true);
        assertThat(state.wasGrpcConnectionActive()).isTrue();
        
        state.setWasDeviceStatusActive(true);
        assertThat(state.wasDeviceStatusActive()).isTrue();
    }

    @Test
    public void testHasActiveOperations()
    {
        BatteryPauseState state = new BatteryPauseState();
        assertThat(state.hasActiveOperations()).isFalse();
        
        // Test each operation individually
        state.setWasCellularScanningActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasWifiScanningActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasBluetoothScanningActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasGnssScanningActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasCdrScanningActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasCellularLoggingEnabled(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasWifiLoggingEnabled(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasBluetoothLoggingEnabled(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasGnssLoggingEnabled(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasCdrLoggingEnabled(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasMqttConnectionActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasGrpcConnectionActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
        state.clear();
        
        state.setWasDeviceStatusActive(true);
        assertThat(state.hasActiveOperations()).isTrue();
    }

    @Test
    public void testClear()
    {
        BatteryPauseState state = new BatteryPauseState();
        
        // Set all states to true
        state.setWasCellularScanningActive(true);
        state.setWasWifiScanningActive(true);
        state.setWasBluetoothScanningActive(true);
        state.setWasGnssScanningActive(true);
        state.setWasCdrScanningActive(true);
        state.setWasCellularLoggingEnabled(true);
        state.setWasWifiLoggingEnabled(true);
        state.setWasBluetoothLoggingEnabled(true);
        state.setWasGnssLoggingEnabled(true);
        state.setWasCdrLoggingEnabled(true);
        state.setWasMqttConnectionActive(true);
        state.setWasGrpcConnectionActive(true);
        state.setWasDeviceStatusActive(true);
        
        assertThat(state.hasActiveOperations()).isTrue();
        
        // Clear all states
        state.clear();
        
        // Verify all states are false
        assertThat(state.wasCellularScanningActive()).isFalse();
        assertThat(state.wasWifiScanningActive()).isFalse();
        assertThat(state.wasBluetoothScanningActive()).isFalse();
        assertThat(state.wasGnssScanningActive()).isFalse();
        assertThat(state.wasCdrScanningActive()).isFalse();
        assertThat(state.wasCellularLoggingEnabled()).isFalse();
        assertThat(state.wasWifiLoggingEnabled()).isFalse();
        assertThat(state.wasBluetoothLoggingEnabled()).isFalse();
        assertThat(state.wasGnssLoggingEnabled()).isFalse();
        assertThat(state.wasCdrLoggingEnabled()).isFalse();
        assertThat(state.wasMqttConnectionActive()).isFalse();
        assertThat(state.wasGrpcConnectionActive()).isFalse();
        assertThat(state.wasDeviceStatusActive()).isFalse();
        assertThat(state.hasActiveOperations()).isFalse();
    }

    @Test
    public void testJsonSerialization() throws JSONException
    {
        BatteryPauseState originalState = new BatteryPauseState();
        
        // Set some states
        originalState.setWasCellularScanningActive(true);
        originalState.setWasWifiLoggingEnabled(true);
        originalState.setWasMqttConnectionActive(true);
        originalState.setWasDeviceStatusActive(true);
        
        // Serialize to JSON
        String json = originalState.toJson();
        assertThat(json).isNotNull();
        
        // Verify JSON contains expected values
        JSONObject jsonObject = new JSONObject(json);
        assertThat(jsonObject.getBoolean("cellular_scanning")).isTrue();
        assertThat(jsonObject.getBoolean("wifi_logging")).isTrue();
        assertThat(jsonObject.getBoolean("mqtt_connection")).isTrue();
        assertThat(jsonObject.getBoolean("device_status")).isTrue();
        assertThat(jsonObject.getBoolean("bluetooth_scanning")).isFalse();
        assertThat(jsonObject.getBoolean("gnss_logging")).isFalse();
    }

    @Test
    public void testJsonDeserialization()
    {
        // Create test JSON
        String json = "{\"cellular_scanning\":true,\"wifi_scanning\":false,\"bluetooth_scanning\":true," +
                      "\"gnss_scanning\":false,\"cdr_scanning\":true,\"cellular_logging\":false," +
                      "\"wifi_logging\":true,\"bluetooth_logging\":false,\"gnss_logging\":true," +
                      "\"cdr_logging\":false,\"mqtt_connection\":true,\"grpc_connection\":false," +
                      "\"device_status\":true}";
        
        BatteryPauseState state = BatteryPauseState.fromJson(json);
        
        // Verify deserialized values
        assertThat(state.wasCellularScanningActive()).isTrue();
        assertThat(state.wasWifiScanningActive()).isFalse();
        assertThat(state.wasBluetoothScanningActive()).isTrue();
        assertThat(state.wasGnssScanningActive()).isFalse();
        assertThat(state.wasCdrScanningActive()).isTrue();
        assertThat(state.wasCellularLoggingEnabled()).isFalse();
        assertThat(state.wasWifiLoggingEnabled()).isTrue();
        assertThat(state.wasBluetoothLoggingEnabled()).isFalse();
        assertThat(state.wasGnssLoggingEnabled()).isTrue();
        assertThat(state.wasCdrLoggingEnabled()).isFalse();
        assertThat(state.wasMqttConnectionActive()).isTrue();
        assertThat(state.wasGrpcConnectionActive()).isFalse();
        assertThat(state.wasDeviceStatusActive()).isTrue();
        assertThat(state.hasActiveOperations()).isTrue();
    }

    @Test
    public void testJsonRoundTrip()
    {
        BatteryPauseState originalState = new BatteryPauseState();
        
        // Set various states
        originalState.setWasCellularScanningActive(true);
        originalState.setWasWifiScanningActive(false);
        originalState.setWasBluetoothScanningActive(true);
        originalState.setWasGnssScanningActive(true);
        originalState.setWasCdrScanningActive(false);
        originalState.setWasCellularLoggingEnabled(true);
        originalState.setWasWifiLoggingEnabled(true);
        originalState.setWasBluetoothLoggingEnabled(false);
        originalState.setWasGnssLoggingEnabled(false);
        originalState.setWasCdrLoggingEnabled(true);
        originalState.setWasMqttConnectionActive(true);
        originalState.setWasGrpcConnectionActive(false);
        originalState.setWasDeviceStatusActive(true);
        
        // Serialize and deserialize
        String json = originalState.toJson();
        BatteryPauseState deserializedState = BatteryPauseState.fromJson(json);
        
        // Verify all states match
        assertThat(deserializedState.wasCellularScanningActive()).isEqualTo(originalState.wasCellularScanningActive());
        assertThat(deserializedState.wasWifiScanningActive()).isEqualTo(originalState.wasWifiScanningActive());
        assertThat(deserializedState.wasBluetoothScanningActive()).isEqualTo(originalState.wasBluetoothScanningActive());
        assertThat(deserializedState.wasGnssScanningActive()).isEqualTo(originalState.wasGnssScanningActive());
        assertThat(deserializedState.wasCdrScanningActive()).isEqualTo(originalState.wasCdrScanningActive());
        assertThat(deserializedState.wasCellularLoggingEnabled()).isEqualTo(originalState.wasCellularLoggingEnabled());
        assertThat(deserializedState.wasWifiLoggingEnabled()).isEqualTo(originalState.wasWifiLoggingEnabled());
        assertThat(deserializedState.wasBluetoothLoggingEnabled()).isEqualTo(originalState.wasBluetoothLoggingEnabled());
        assertThat(deserializedState.wasGnssLoggingEnabled()).isEqualTo(originalState.wasGnssLoggingEnabled());
        assertThat(deserializedState.wasCdrLoggingEnabled()).isEqualTo(originalState.wasCdrLoggingEnabled());
        assertThat(deserializedState.wasMqttConnectionActive()).isEqualTo(originalState.wasMqttConnectionActive());
        assertThat(deserializedState.wasGrpcConnectionActive()).isEqualTo(originalState.wasGrpcConnectionActive());
        assertThat(deserializedState.wasDeviceStatusActive()).isEqualTo(originalState.wasDeviceStatusActive());
    }

    @Test
    public void testFromJsonWithNull()
    {
        BatteryPauseState state = BatteryPauseState.fromJson(null);
        assertThat(state).isNotNull();
        assertThat(state.hasActiveOperations()).isFalse();
    }

    @Test
    public void testFromJsonWithEmptyString()
    {
        BatteryPauseState state = BatteryPauseState.fromJson("");
        assertThat(state).isNotNull();
        assertThat(state.hasActiveOperations()).isFalse();
    }

    @Test
    public void testFromJsonWithInvalidJson()
    {
        BatteryPauseState state = BatteryPauseState.fromJson("not valid json");
        assertThat(state).isNotNull();
        assertThat(state.hasActiveOperations()).isFalse();
    }

    @Test
    public void testFromJsonWithPartialJson()
    {
        // JSON with only some fields
        String json = "{\"cellular_scanning\":true,\"wifi_logging\":true}";
        BatteryPauseState state = BatteryPauseState.fromJson(json);
        
        // Specified fields should be set
        assertThat(state.wasCellularScanningActive()).isTrue();
        assertThat(state.wasWifiLoggingEnabled()).isTrue();
        
        // Unspecified fields should default to false
        assertThat(state.wasWifiScanningActive()).isFalse();
        assertThat(state.wasBluetoothScanningActive()).isFalse();
        assertThat(state.wasGnssScanningActive()).isFalse();
        assertThat(state.wasCdrScanningActive()).isFalse();
        assertThat(state.wasCellularLoggingEnabled()).isFalse();
        assertThat(state.wasBluetoothLoggingEnabled()).isFalse();
        assertThat(state.wasGnssLoggingEnabled()).isFalse();
        assertThat(state.wasCdrLoggingEnabled()).isFalse();
        assertThat(state.wasMqttConnectionActive()).isFalse();
        assertThat(state.wasGrpcConnectionActive()).isFalse();
        assertThat(state.wasDeviceStatusActive()).isFalse();
    }
    
    @Test
    public void testParcelable()
    {
        BatteryPauseState originalState = new BatteryPauseState();
        
        // Set some states
        originalState.setWasCellularScanningActive(true);
        originalState.setWasWifiScanningActive(false);
        originalState.setWasBluetoothLoggingEnabled(true);
        originalState.setWasMqttConnectionActive(true);
        originalState.setWasDeviceStatusActive(false);
        
        // Write to parcel
        Parcel parcel = Parcel.obtain();
        originalState.writeToParcel(parcel, 0);
        
        // Reset parcel position for reading
        parcel.setDataPosition(0);
        
        // Read from parcel
        BatteryPauseState recreatedState = BatteryPauseState.CREATOR.createFromParcel(parcel);
        
        // Verify all states match
        assertThat(recreatedState.wasCellularScanningActive()).isEqualTo(originalState.wasCellularScanningActive());
        assertThat(recreatedState.wasWifiScanningActive()).isEqualTo(originalState.wasWifiScanningActive());
        assertThat(recreatedState.wasBluetoothScanningActive()).isEqualTo(originalState.wasBluetoothScanningActive());
        assertThat(recreatedState.wasGnssScanningActive()).isEqualTo(originalState.wasGnssScanningActive());
        assertThat(recreatedState.wasCdrScanningActive()).isEqualTo(originalState.wasCdrScanningActive());
        assertThat(recreatedState.wasCellularLoggingEnabled()).isEqualTo(originalState.wasCellularLoggingEnabled());
        assertThat(recreatedState.wasWifiLoggingEnabled()).isEqualTo(originalState.wasWifiLoggingEnabled());
        assertThat(recreatedState.wasBluetoothLoggingEnabled()).isEqualTo(originalState.wasBluetoothLoggingEnabled());
        assertThat(recreatedState.wasGnssLoggingEnabled()).isEqualTo(originalState.wasGnssLoggingEnabled());
        assertThat(recreatedState.wasCdrLoggingEnabled()).isEqualTo(originalState.wasCdrLoggingEnabled());
        assertThat(recreatedState.wasMqttConnectionActive()).isEqualTo(originalState.wasMqttConnectionActive());
        assertThat(recreatedState.wasGrpcConnectionActive()).isEqualTo(originalState.wasGrpcConnectionActive());
        assertThat(recreatedState.wasDeviceStatusActive()).isEqualTo(originalState.wasDeviceStatusActive());
        
        // Clean up
        parcel.recycle();
    }
    
    @Test
    public void testParcelableDescribeContents()
    {
        BatteryPauseState state = new BatteryPauseState();
        assertThat(state.describeContents()).isEqualTo(0);
    }
    
    @Test
    public void testParcelableNewArray()
    {
        BatteryPauseState[] array = BatteryPauseState.CREATOR.newArray(5);
        assertThat(array).hasLength(5);
        assertThat(array[0]).isNull();
        assertThat(array[4]).isNull();
    }
}