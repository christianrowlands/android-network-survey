package com.craxiom.networksurvey.fragments.model;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * View model for notifying the {@link com.craxiom.networksurvey.fragments.NetworkDetailsFragment} of any data updates,
 * i.e. location updates, cellular scan updates, etc.
 *
 * @since 1.6.0
 */
public class CellularViewModel extends ViewModel
{
    private final MutableLiveData<String> dataNetworkType = new MutableLiveData<>();
    private final MutableLiveData<String> carrier = new MutableLiveData<>();
    private final MutableLiveData<String> voiceNetworkType = new MutableLiveData<>();

    private final MutableLiveData<Location> location = new MutableLiveData<>();
    private final MutableLiveData<Boolean> providerEnabled = new MutableLiveData<>(true);

    // Common Cellular fields
    private final MutableLiveData<String> servingCellTitle = new MutableLiveData<>();
    private final MutableLiveData<String> mcc = new MutableLiveData<>();
    private final MutableLiveData<String> mnc = new MutableLiveData<>();
    private final MutableLiveData<String> areaCode = new MutableLiveData<>();
    private final MutableLiveData<Long> cellId = new MutableLiveData<>(); // NR requires a long

    private final MutableLiveData<String> channelNumber = new MutableLiveData<>();

    // LTE specific fields
    private final MutableLiveData<String> pci = new MutableLiveData<>();
    private final MutableLiveData<String> bandwidth = new MutableLiveData<>();
    private final MutableLiveData<String> ta = new MutableLiveData<>();

    private final MutableLiveData<Integer> rsrp = new MutableLiveData<>();
    private final MutableLiveData<Integer> rsrq = new MutableLiveData<>();

    public LiveData<String> getDataNetworkType()
    {
        return dataNetworkType;
    }

    public void setDataNetworkType(String newDataNetworkType)
    {
        dataNetworkType.postValue(newDataNetworkType);
    }

    public LiveData<String> getCarrier()
    {
        return carrier;
    }

    public void setCarrier(String newCarrier)
    {
        carrier.postValue(newCarrier);
    }

    public LiveData<String> getVoiceNetworkType()
    {
        return voiceNetworkType;
    }

    public void setVoiceNetworkType(String newVoiceNetworkType)
    {
        voiceNetworkType.postValue(newVoiceNetworkType);
    }

    public LiveData<Location> getLocation()
    {
        return location;
    }

    public void setLocation(Location newLocation)
    {
        location.postValue(newLocation);
    }

    public LiveData<Boolean> getProviderEnabled()
    {
        return providerEnabled;
    }

    public void setProviderEnabled(boolean isProviderEnabled)
    {
        providerEnabled.postValue(isProviderEnabled);
    }

    public LiveData<String> getMcc()
    {
        return mcc;
    }

    public void setMcc(String newMcc)
    {
        mcc.postValue(newMcc);
    }

    public LiveData<String> getMnc()
    {
        return mnc;
    }

    public void setMnc(String newMnc)
    {
        mnc.postValue(newMnc);
    }

    public LiveData<String> getAreaCode()
    {
        return areaCode;
    }

    public void setAreaCode(String newAreaCode)
    {
        areaCode.postValue(newAreaCode);
    }

    public LiveData<Long> getCellId()
    {
        return cellId;
    }

    public void setCellId(Long newCellId)
    {
        cellId.postValue(newCellId);
    }

    public LiveData<String> getChannelNumber()
    {
        return channelNumber;
    }

    public void setChannelNumber(String newChannelNumber)
    {
        channelNumber.postValue(newChannelNumber);
    }

    public LiveData<String> getPci()
    {
        return pci;
    }

    public void setPci(String newPci)
    {
        pci.postValue(newPci);
    }

    public LiveData<String> getBandwidth()
    {
        return bandwidth;
    }

    public void setBandwidth(String newBandwidth)
    {
        bandwidth.postValue(newBandwidth);
    }

    public LiveData<String> getTa()
    {
        return ta;
    }

    public void setTa(String newTa)
    {
        ta.postValue(newTa);
    }

    public LiveData<Integer> getRsrp()
    {
        return rsrp;
    }

    public void setRsrp(Integer newRsrp)
    {
        rsrp.postValue(newRsrp);
    }

    public LiveData<Integer> getRsrq()
    {
        return rsrq;
    }

    public void setRsrq(Integer newRsrq)
    {
        rsrq.postValue(newRsrq);
    }
}