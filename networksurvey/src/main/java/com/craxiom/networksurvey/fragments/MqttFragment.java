package com.craxiom.networksurvey.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;

import androidx.appcompat.widget.SwitchCompat;
import androidx.navigation.Navigation;

import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.mqttlibrary.ui.AConnectionFragment;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.services.NetworkSurveyService;

/**
 * A fragment for allowing the user to connect to an MQTT broker. This fragment handles
 * the UI portion of the connection and delegates the actual connection logic to {@link NetworkSurveyService}.
 *
 * @since 0.1.1
 */
public class MqttFragment extends AConnectionFragment<NetworkSurveyService.SurveyServiceBinder>
{
    private SwitchCompat cellularStreamToggleSwitch;
    private SwitchCompat wifiStreamToggleSwitch;
    private SwitchCompat bluetoothStreamToggleSwitch;
    private SwitchCompat gnssStreamToggleSwitch;
    private SwitchCompat deviceStatusStreamToggleSwitch;

    private boolean cellularStreamEnabled = true;
    private boolean wifiStreamEnabled = true;
    private boolean bluetoothStreamEnabled = true;
    private boolean gnssStreamEnabled = true;
    private boolean deviceStatusStreamEnabled = true;

    @Override
    protected void inflateAdditionalFieldsViewStub(LayoutInflater layoutInflater, ViewStub viewStub)
    {
        viewStub.setLayoutResource(R.layout.fragment_stream_options);
        View inflatedStub = viewStub.inflate();

        cellularStreamToggleSwitch = inflatedStub.findViewById(R.id.streamCellularToggleSwitch);
        wifiStreamToggleSwitch = inflatedStub.findViewById(R.id.streamWifiToggleSwitch);
        bluetoothStreamToggleSwitch = inflatedStub.findViewById(R.id.streamBluetoothToggleSwitch);
        gnssStreamToggleSwitch = inflatedStub.findViewById(R.id.streamGnssToggleSwitch);
        deviceStatusStreamToggleSwitch = inflatedStub.findViewById(R.id.streamDeviceStatusToggleSwitch);

        Button scanCodeButton = inflatedStub.findViewById(R.id.code_scan_button);
        scanCodeButton.setVisibility(View.VISIBLE);
        scanCodeButton.setOnClickListener(v -> {
                Navigation.findNavController(requireActivity(), getId())
                        .navigate(MqttFragmentDirections.actionMqttConnectionFragmentToScannerFragment());
        });
    }

    @Override
    protected Context getApplicationContext()
    {
        return requireActivity().getApplicationContext();
    }

    @Override
    protected Class<?> getServiceClass()
    {
        return NetworkSurveyService.class;
    }


    @Override
    protected void readMdmConfigAdditionalProperties(Bundle mdmProperties)
    {
        cellularStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        wifiStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        bluetoothStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        gnssStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        deviceStatusStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
    }

    /**
     * Update the UI fields from the instance variables in this class.
     *
     * @since 0.1.5
     */
    @Override
    protected void updateUiFieldsFromStoredValues()
    {
        super.updateUiFieldsFromStoredValues();

        cellularStreamToggleSwitch.setChecked(cellularStreamEnabled);
        wifiStreamToggleSwitch.setChecked(wifiStreamEnabled);
        bluetoothStreamToggleSwitch.setChecked(bluetoothStreamEnabled);
        gnssStreamToggleSwitch.setChecked(gnssStreamEnabled);
        deviceStatusStreamToggleSwitch.setChecked(deviceStatusStreamEnabled);
    }

    @Override
    protected void readUIAdditionalFields()
    {
        cellularStreamEnabled = cellularStreamToggleSwitch.isChecked();
        wifiStreamEnabled = wifiStreamToggleSwitch.isChecked();
        bluetoothStreamEnabled = bluetoothStreamToggleSwitch.isChecked();
        gnssStreamEnabled = gnssStreamToggleSwitch.isChecked();
        deviceStatusStreamEnabled = deviceStatusStreamToggleSwitch.isChecked();
    }

    @Override
    protected void storeAdditionalParameters(SharedPreferences.Editor editor)
    {
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, cellularStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, wifiStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, bluetoothStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, gnssStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, deviceStatusStreamEnabled);
    }

    @Override
    protected void restoreAdditionalParameters(SharedPreferences sharedPreferences)
    {
        cellularStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        wifiStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        bluetoothStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        gnssStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        deviceStatusStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
    }

    @Override
    protected void setConnectionInputFieldsEditable(boolean editable, boolean force)
    {
        super.setConnectionInputFieldsEditable(editable, force);

        cellularStreamToggleSwitch.setEnabled(editable);
        wifiStreamToggleSwitch.setEnabled(editable);
        bluetoothStreamToggleSwitch.setEnabled(editable);
        gnssStreamToggleSwitch.setEnabled(editable);
        deviceStatusStreamToggleSwitch.setEnabled(editable);
    }

    @Override
    protected BrokerConnectionInfo getBrokerConnectionInfo()
    {
        return new MqttConnectionInfo(host,
                portNumber,
                tlsEnabled,
                deviceName,
                mqttUsername,
                mqttPassword,
                cellularStreamEnabled,
                wifiStreamEnabled,
                bluetoothStreamEnabled,
                gnssStreamEnabled,
                deviceStatusStreamEnabled);
    }
}
