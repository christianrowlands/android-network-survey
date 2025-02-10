package com.craxiom.networksurvey.fragments.model;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.craxiom.mqttlibrary.connection.ConnectionState;

import java.util.Objects;

/**
 * View model for notifying the {@link com.craxiom.networksurvey.fragments.DashboardFragment} of
 * any data updates,
 *
 * @since 1.10.0
 */
public class DashboardViewModel extends ViewModel
{
    private final MutableLiveData<Boolean> uploadScanningActive = new MutableLiveData<>();
    private final MutableLiveData<Integer> cellularUploadQueueCount = new MutableLiveData<>(-1);
    private final MutableLiveData<Integer> wifiUploadQueueCount = new MutableLiveData<>(-1);

    private final MutableLiveData<Boolean> cellularLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> wifiLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> bluetoothLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gnssLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> cdrLoggingEnabled = new MutableLiveData<>();

    private final MutableLiveData<ConnectionState> mqttConnectionState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> cellularMqttStreamEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> wifiMqttStreamEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> bluetoothMqttStreamEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gnssMqttStreamEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deviceStatusMqttStreamEnabled = new MutableLiveData<>();

    private final MutableLiveData<Location> location = new MutableLiveData<>();
    private final MutableLiveData<Boolean> providerEnabled = new MutableLiveData<>(true);

    public LiveData<Boolean> getUploadScanningActive()
    {
        return uploadScanningActive;
    }

    public void setUploadScanningActive(boolean isEnabled)
    {
        if (!Objects.equals(uploadScanningActive.getValue(), isEnabled))
        {
            uploadScanningActive.postValue(isEnabled);
        }
    }

    public LiveData<Integer> getCellularUploadQueueCount()
    {
        return cellularUploadQueueCount;
    }

    public void setCellularUploadQueueCount(int count)
    {
        if (cellularUploadQueueCount.getValue() != count)
        {
            cellularUploadQueueCount.postValue(count);
        }
    }

    public LiveData<Integer> getWifiUploadQueueCount()
    {
        return wifiUploadQueueCount;
    }

    public void setWifiUploadQueueCount(int count)
    {
        if (wifiUploadQueueCount.getValue() != count)
        {
            wifiUploadQueueCount.postValue(count);
        }
    }

    public LiveData<Boolean> getCellularLoggingEnabled()
    {
        return cellularLoggingEnabled;
    }

    public void setCellularLoggingEnabled(boolean isEnabled)
    {
        if (!Objects.equals(cellularLoggingEnabled.getValue(), isEnabled))
        {
            cellularLoggingEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getWifiLoggingEnabled()
    {
        return wifiLoggingEnabled;
    }

    public void setWifiLoggingEnabled(boolean isEnabled)
    {
        if (!Objects.equals(wifiLoggingEnabled.getValue(), isEnabled))
        {
            wifiLoggingEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getBluetoothLoggingEnabled()
    {
        return bluetoothLoggingEnabled;
    }

    public void setBluetoothLoggingEnabled(boolean isEnabled)
    {
        if (!Objects.equals(bluetoothLoggingEnabled.getValue(), isEnabled))
        {
            bluetoothLoggingEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getGnssLoggingEnabled()
    {
        return gnssLoggingEnabled;
    }

    public void setGnssLoggingEnabled(boolean isEnabled)
    {
        if (!Objects.equals(gnssLoggingEnabled.getValue(), isEnabled))
        {
            gnssLoggingEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getCdrLoggingEnabled()
    {
        return cdrLoggingEnabled;
    }

    public void setCdrLoggingEnabled(boolean isEnabled)
    {
        if (!Objects.equals(cdrLoggingEnabled.getValue(), isEnabled))
        {
            cdrLoggingEnabled.postValue(isEnabled);
        }
    }

    public LiveData<ConnectionState> getMqttConnectionState()
    {
        return mqttConnectionState;
    }

    public void setMqttConnectionState(ConnectionState state)
    {
        if (mqttConnectionState.getValue() != state)
        {
            mqttConnectionState.postValue(state);
        }
    }

    public LiveData<Boolean> getCellularMqttStreamEnabled()
    {
        return cellularMqttStreamEnabled;
    }

    public void setCellularMqttStreamEnabled(boolean isEnabled)
    {
        if (!Objects.equals(cellularMqttStreamEnabled.getValue(), isEnabled))
        {
            cellularMqttStreamEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getWifiMqttStreamEnabled()
    {
        return wifiMqttStreamEnabled;
    }

    public void setWifiMqttStreamEnabled(boolean isEnabled)
    {
        if (!Objects.equals(wifiMqttStreamEnabled.getValue(), isEnabled))
        {
            wifiMqttStreamEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getBluetoothMqttStreamEnabled()
    {
        return bluetoothMqttStreamEnabled;
    }

    public void setBluetoothMqttStreamEnabled(boolean isEnabled)
    {
        if (!Objects.equals(bluetoothMqttStreamEnabled.getValue(), isEnabled))
        {
            bluetoothMqttStreamEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getGnssMqttStreamEnabled()
    {
        return gnssMqttStreamEnabled;
    }

    public void setGnssMqttStreamEnabled(boolean isEnabled)
    {
        if (!Objects.equals(gnssMqttStreamEnabled.getValue(), isEnabled))
        {
            gnssMqttStreamEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Boolean> getDeviceStatusMqttStreamEnabled()
    {
        return deviceStatusMqttStreamEnabled;
    }

    public void setDeviceStatusMqttStreamEnabled(boolean isEnabled)
    {
        if (!Objects.equals(deviceStatusMqttStreamEnabled.getValue(), isEnabled))
        {
            deviceStatusMqttStreamEnabled.postValue(isEnabled);
        }
    }

    public LiveData<Location> getLocation()
    {
        return location;
    }

    public void setLocation(Location newLocation)
    {
        if (!Objects.equals(location.getValue(), newLocation))
        {
            location.postValue(newLocation);
        }
    }

    public LiveData<Boolean> getProviderEnabled()
    {
        return providerEnabled;
    }

    public void setProviderEnabled(boolean isProviderEnabled)
    {
        if (!Objects.equals(providerEnabled.getValue(), isProviderEnabled))
        {
            providerEnabled.postValue(isProviderEnabled);
        }
    }
}