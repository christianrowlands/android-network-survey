package com.craxiom.networksurvey.fragments.model;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.craxiom.networksurvey.model.CellularProtocol;

import java.util.Objects;
import java.util.SortedSet;

/**
 * View model for notifying the {@link com.craxiom.networksurvey.fragments.DashboardFragment} of
 * any data updates,
 *
 * @since 1.10.0
 */
public class DashboardViewModel extends ViewModel
{
    private final MutableLiveData<Boolean> cellularLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> wifiLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> bluetoothLoggingEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gnssLoggingEnabled = new MutableLiveData<>();

    private final MutableLiveData<Boolean> mqttConnectionStatus = new MutableLiveData<>();

    private final MutableLiveData<Location> location = new MutableLiveData<>();
    private final MutableLiveData<Boolean> providerEnabled = new MutableLiveData<>(true);

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