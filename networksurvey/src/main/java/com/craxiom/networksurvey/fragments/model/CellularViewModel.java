package com.craxiom.networksurvey.fragments.model;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.craxiom.networksurvey.model.CellularProtocol;

import java.util.Objects;
import java.util.SortedSet;

/**
 * View model for notifying the {@link com.craxiom.networksurvey.fragments.NetworkDetailsFragment} of any data updates,
 * i.e. location updates, cellular scan updates, etc.
 *
 * @since 1.6.0
 */
public class CellularViewModel extends ViewModel
{
    private final MutableLiveData<String> carrier = new MutableLiveData<>();
    // Top card hero line ("NR · Standalone") and its color resource.
    private final MutableLiveData<String> heroText = new MutableLiveData<>();
    private final MutableLiveData<Integer> heroColorId = new MutableLiveData<>();
    // Pill values (the label part is added by the fragment); null means "hide". A null voice pill
    // value hides the whole pill row (used when the device has no service).
    private final MutableLiveData<String> voicePillValue = new MutableLiveData<>();
    private final MutableLiveData<String> dataPillValue = new MutableLiveData<>();
    private final MutableLiveData<String> brandingPillValue = new MutableLiveData<>();
    // Carrier aggregation chips + summary; null means "hide the section".
    private final MutableLiveData<CarrierAggregationViewState> carrierAggregation = new MutableLiveData<>();

    private final MutableLiveData<Location> location = new MutableLiveData<>();
    private final MutableLiveData<Boolean> providerEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> airplaneModeActive = new MutableLiveData<>(false);

    // Common Cellular fields
    private final MutableLiveData<CellularProtocol> servingCellProtocol = new MutableLiveData<>(CellularProtocol.NONE);
    private final MutableLiveData<String> mcc = new MutableLiveData<>();
    private final MutableLiveData<String> mnc = new MutableLiveData<>();
    private final MutableLiveData<String> areaCode = new MutableLiveData<>();
    private final MutableLiveData<Long> cellId = new MutableLiveData<>(); // NR requires a long

    private final MutableLiveData<String> channelNumber = new MutableLiveData<>(); // AKA ARFCN, EARFCN, etc
    private final MutableLiveData<String> frequency = new MutableLiveData<>(); // For NR frequency in MHz
    private final MutableLiveData<String> band = new MutableLiveData<>(); // For NR band with name
    private final MutableLiveData<String> lteBand = new MutableLiveData<>(); // For LTE band with name

    // LTE specific fields
    private final MutableLiveData<String> pci = new MutableLiveData<>();
    private final MutableLiveData<String> bandwidth = new MutableLiveData<>();
    private final MutableLiveData<String> ta = new MutableLiveData<>();
    private final MutableLiveData<String> cqi = new MutableLiveData<>();
    private final MutableLiveData<Integer> signalOne = new MutableLiveData<>(); // Also used for RSSI and SS_RSRP
    private final MutableLiveData<Integer> signalTwo = new MutableLiveData<>(); // Also used for RSCP and SS_RSRQ
    private final MutableLiveData<Integer> signalThree = new MutableLiveData<>(); // Used for LTE SNR
    private final MutableLiveData<SortedSet<NrNeighbor>> nrNeighbors = new MutableLiveData<>();
    private final MutableLiveData<SortedSet<LteNeighbor>> lteNeighbors = new MutableLiveData<>();
    private final MutableLiveData<SortedSet<UmtsNeighbor>> umtsNeighbors = new MutableLiveData<>();
    private final MutableLiveData<SortedSet<GsmNeighbor>> gsmNeighbors = new MutableLiveData<>();

    public LiveData<String> getCarrier()
    {
        return carrier;
    }

    public void setCarrier(String newCarrier)
    {
        if (!Objects.equals(carrier.getValue(), newCarrier))
        {
            carrier.postValue(newCarrier);
        }
    }

    public LiveData<String> getHeroText()
    {
        return heroText;
    }

    public void setHeroText(String newHeroText)
    {
        if (!Objects.equals(heroText.getValue(), newHeroText))
        {
            heroText.postValue(newHeroText);
        }
    }

    public LiveData<Integer> getHeroColorId()
    {
        return heroColorId;
    }

    public void setHeroColorId(Integer newHeroColorId)
    {
        if (!Objects.equals(heroColorId.getValue(), newHeroColorId))
        {
            heroColorId.postValue(newHeroColorId);
        }
    }

    public LiveData<String> getVoicePillValue()
    {
        return voicePillValue;
    }

    public void setVoicePillValue(String newVoicePillValue)
    {
        if (!Objects.equals(voicePillValue.getValue(), newVoicePillValue))
        {
            voicePillValue.postValue(newVoicePillValue);
        }
    }

    public LiveData<String> getDataPillValue()
    {
        return dataPillValue;
    }

    public void setDataPillValue(String newDataPillValue)
    {
        if (!Objects.equals(dataPillValue.getValue(), newDataPillValue))
        {
            dataPillValue.postValue(newDataPillValue);
        }
    }

    public LiveData<String> getBrandingPillValue()
    {
        return brandingPillValue;
    }

    public void setBrandingPillValue(String newBrandingPillValue)
    {
        if (!Objects.equals(brandingPillValue.getValue(), newBrandingPillValue))
        {
            brandingPillValue.postValue(newBrandingPillValue);
        }
    }

    public LiveData<CarrierAggregationViewState> getCarrierAggregation()
    {
        return carrierAggregation;
    }

    public void setCarrierAggregation(CarrierAggregationViewState newCarrierAggregation)
    {
        if (!Objects.equals(carrierAggregation.getValue(), newCarrierAggregation))
        {
            carrierAggregation.postValue(newCarrierAggregation);
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

    public LiveData<Boolean> getAirplaneModeActive()
    {
        return airplaneModeActive;
    }

    public void setAirplaneModeActive(boolean isAirplaneModeActive)
    {
        if (!Objects.equals(airplaneModeActive.getValue(), isAirplaneModeActive))
        {
            airplaneModeActive.postValue(isAirplaneModeActive);
        }
    }

    public LiveData<CellularProtocol> getServingCellProtocol()
    {
        return servingCellProtocol;
    }

    public void setServingCellProtocol(CellularProtocol newProtocol)
    {
        if (servingCellProtocol.getValue() != newProtocol)
        {
            servingCellProtocol.postValue(newProtocol);
        }
    }

    public LiveData<String> getMcc()
    {
        return mcc;
    }

    public void setMcc(String newMcc)
    {
        if (!Objects.equals(mcc.getValue(), newMcc))
        {
            mcc.postValue(newMcc);
        }
    }

    public LiveData<String> getMnc()
    {
        return mnc;
    }

    public void setMnc(String newMnc)
    {
        if (!Objects.equals(mnc.getValue(), newMnc))
        {
            mnc.postValue(newMnc);
        }
    }

    public LiveData<String> getAreaCode()
    {
        return areaCode;
    }

    public void setAreaCode(String newAreaCode)
    {
        if (!Objects.equals(areaCode.getValue(), newAreaCode))
        {
            areaCode.postValue(newAreaCode);
        }
    }

    public LiveData<Long> getCellId()
    {
        return cellId;
    }

    public void setCellId(Long newCellId)
    {
        if (!Objects.equals(cellId.getValue(), newCellId))
        {
            cellId.postValue(newCellId);
        }
    }

    public LiveData<String> getChannelNumber()
    {
        return channelNumber;
    }

    public void setChannelNumber(String newChannelNumber)
    {
        if (!Objects.equals(channelNumber.getValue(), newChannelNumber))
        {
            channelNumber.postValue(newChannelNumber);
        }
    }

    public LiveData<String> getFrequency()
    {
        return frequency;
    }

    public void setFrequency(String newFrequency)
    {
        if (!Objects.equals(frequency.getValue(), newFrequency))
        {
            frequency.postValue(newFrequency);
        }
    }

    public LiveData<String> getBand()
    {
        return band;
    }

    public void setBand(String newBand)
    {
        if (!Objects.equals(band.getValue(), newBand))
        {
            band.postValue(newBand);
        }
    }

    public LiveData<String> getLteBand()
    {
        return lteBand;
    }

    public void setLteBand(String newLteBand)
    {
        if (!Objects.equals(lteBand.getValue(), newLteBand))
        {
            lteBand.postValue(newLteBand);
        }
    }

    public LiveData<String> getPci()
    {
        return pci;
    }

    public void setPci(String newPci)
    {
        if (!Objects.equals(pci.getValue(), newPci))
        {
            pci.postValue(newPci);
        }
    }

    public LiveData<String> getBandwidth()
    {
        return bandwidth;
    }

    public void setBandwidth(String newBandwidth)
    {
        if (!Objects.equals(bandwidth.getValue(), newBandwidth))
        {
            bandwidth.postValue(newBandwidth);
        }
    }

    public LiveData<String> getTa()
    {
        return ta;
    }

    public void setTa(String newTa)
    {
        if (!Objects.equals(ta.getValue(), newTa))
        {
            ta.postValue(newTa);
        }
    }

    public LiveData<String> getCqi()
    {
        return cqi;
    }

    public void setCqi(String newTa)
    {
        if (!Objects.equals(cqi.getValue(), newTa))
        {
            cqi.postValue(newTa);
        }
    }

    public LiveData<Integer> getSignalOne()
    {
        return signalOne;
    }

    public void setSignalOne(Integer newSignal)
    {
        if (!Objects.equals(signalOne.getValue(), newSignal))
        {
            signalOne.postValue(newSignal);
        }
    }

    public LiveData<Integer> getSignalTwo()
    {
        return signalTwo;
    }

    public void setSignalTwo(Integer newSignal)
    {
        if (!Objects.equals(signalTwo.getValue(), newSignal))
        {
            signalTwo.postValue(newSignal);
        }
    }

    public LiveData<Integer> getSignalThree()
    {
        return signalThree;
    }

    public void setSignalThree(Integer newSignal)
    {
        if (!Objects.equals(signalThree.getValue(), newSignal))
        {
            signalThree.postValue(newSignal);
        }
    }

    public LiveData<SortedSet<NrNeighbor>> getNrNeighbors()
    {
        return nrNeighbors;
    }

    public void setNrNeighbors(SortedSet<NrNeighbor> newNrNeighbors)
    {
        nrNeighbors.postValue(newNrNeighbors);
    }

    public LiveData<SortedSet<LteNeighbor>> getLteNeighbors()
    {
        return lteNeighbors;
    }

    public void setLteNeighbors(SortedSet<LteNeighbor> newLteNeighbors)
    {
        lteNeighbors.postValue(newLteNeighbors);
    }

    public LiveData<SortedSet<UmtsNeighbor>> getUmtsNeighbors()
    {
        return umtsNeighbors;
    }

    public void setUmtsNeighbors(SortedSet<UmtsNeighbor> newUmtsNeighbors)
    {
        umtsNeighbors.postValue(newUmtsNeighbors);
    }

    public LiveData<SortedSet<GsmNeighbor>> getGsmNeighbors()
    {
        return gsmNeighbors;
    }

    public void setGsmNeighbors(SortedSet<GsmNeighbor> newGsmNeighbors)
    {
        gsmNeighbors.postValue(newGsmNeighbors);
    }
}