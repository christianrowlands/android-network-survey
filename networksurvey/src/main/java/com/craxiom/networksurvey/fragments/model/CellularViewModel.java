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
    /**
     * A {@link MutableLiveData} whose {@link #postIfChanged} suppresses posts only when the value
     * matches the last POSTED value, not the last delivered one. The distinct check itself is
     * load-bearing (re-delivering an unchanged signal value re-triggers the signal bar
     * animations), but checking against {@link #getValue()} has a race: postValue only guarantees
     * delivery of the LAST posted value, so when two updates land within one main-thread drain
     * window, a setter comparing against the stale delivered value can conclude "no change" and
     * swallow a post (e.g. a clear-to-null right after a batch, leaving stale data on screen).
     * Tracking the last posted value closes that window. Synchronized because setters are called
     * from both the survey executor and the main thread (airplane mode receiver, onResume clear).
     */
    private static final class DistinctLiveData<T> extends MutableLiveData<T>
    {
        private T lastPosted;

        DistinctLiveData()
        {
        }

        DistinctLiveData(T initialValue)
        {
            super(initialValue);
            lastPosted = initialValue;
        }

        synchronized void postIfChanged(T value)
        {
            if (Objects.equals(lastPosted, value)) return;
            lastPosted = value;
            postValue(value);
        }
    }

    private final DistinctLiveData<String> carrier = new DistinctLiveData<>();
    // Top card hero line ("NR · Standalone") and its color resource.
    private final DistinctLiveData<String> heroText = new DistinctLiveData<>();
    private final DistinctLiveData<Integer> heroColorId = new DistinctLiveData<>();
    // Pill values (the label part is added by the fragment); null means "hide". A null voice pill
    // value hides the whole pill row (used when the device has no service).
    private final DistinctLiveData<String> voicePillValue = new DistinctLiveData<>();
    private final DistinctLiveData<String> dataPillValue = new DistinctLiveData<>();
    private final DistinctLiveData<String> brandingPillValue = new DistinctLiveData<>();
    // Carrier aggregation chips + summary; null means "hide the section".
    private final DistinctLiveData<CarrierAggregationViewState> carrierAggregation = new DistinctLiveData<>();
    // The 5G NR secondary cell details card (the NSA data leg, or an NR CA SCell under SA); null
    // means "hide the card".
    private final DistinctLiveData<NrSecondaryCellViewState> nrSecondaryCell = new DistinctLiveData<>();
    // The idle card's "Last seen ..." line, kept separate from the card's view state so that the
    // ticking age does not invalidate the whole card once per scan; an empty string hides it.
    private final DistinctLiveData<String> nrLastSeenText = new DistinctLiveData<>("");
    // The most recent live (non-idle) NR secondary cell sighting, retained here rather than in the
    // fragment so the card's idle state survives rotation. Written on the survey executor and
    // cleared from the main thread, hence the synchronized accessors.
    private NrSecondaryCellSighting lastNrSecondaryCellSighting = NrSecondaryCellSighting.NONE;

    private final DistinctLiveData<Location> location = new DistinctLiveData<>();
    private final DistinctLiveData<Boolean> providerEnabled = new DistinctLiveData<>(true);
    private final DistinctLiveData<Boolean> airplaneModeActive = new DistinctLiveData<>(false);

    // Common Cellular fields
    private final DistinctLiveData<CellularProtocol> servingCellProtocol = new DistinctLiveData<>(CellularProtocol.NONE);
    private final DistinctLiveData<String> mcc = new DistinctLiveData<>();
    private final DistinctLiveData<String> mnc = new DistinctLiveData<>();
    private final DistinctLiveData<String> areaCode = new DistinctLiveData<>();
    private final DistinctLiveData<Long> cellId = new DistinctLiveData<>(); // NR requires a long

    private final DistinctLiveData<String> channelNumber = new DistinctLiveData<>(); // AKA ARFCN, EARFCN, etc
    private final DistinctLiveData<String> frequency = new DistinctLiveData<>(); // For NR frequency in MHz
    private final DistinctLiveData<String> band = new DistinctLiveData<>(); // For NR band with name
    private final DistinctLiveData<String> lteBand = new DistinctLiveData<>(); // For LTE band with name

    // LTE specific fields
    private final DistinctLiveData<String> pci = new DistinctLiveData<>();
    private final DistinctLiveData<String> bandwidth = new DistinctLiveData<>();
    private final DistinctLiveData<String> ta = new DistinctLiveData<>();
    private final DistinctLiveData<String> cqi = new DistinctLiveData<>();
    private final DistinctLiveData<Integer> signalOne = new DistinctLiveData<>(); // Also used for RSSI and SS_RSRP
    private final DistinctLiveData<Integer> signalTwo = new DistinctLiveData<>(); // Also used for RSCP and SS_RSRQ
    private final DistinctLiveData<Integer> signalThree = new DistinctLiveData<>(); // Used for LTE SNR
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
        carrier.postIfChanged(newCarrier);
    }

    public LiveData<String> getHeroText()
    {
        return heroText;
    }

    public void setHeroText(String newHeroText)
    {
        heroText.postIfChanged(newHeroText);
    }

    public LiveData<Integer> getHeroColorId()
    {
        return heroColorId;
    }

    public void setHeroColorId(Integer newHeroColorId)
    {
        heroColorId.postIfChanged(newHeroColorId);
    }

    public LiveData<String> getVoicePillValue()
    {
        return voicePillValue;
    }

    public void setVoicePillValue(String newVoicePillValue)
    {
        voicePillValue.postIfChanged(newVoicePillValue);
    }

    public LiveData<String> getDataPillValue()
    {
        return dataPillValue;
    }

    public void setDataPillValue(String newDataPillValue)
    {
        dataPillValue.postIfChanged(newDataPillValue);
    }

    public LiveData<String> getBrandingPillValue()
    {
        return brandingPillValue;
    }

    public void setBrandingPillValue(String newBrandingPillValue)
    {
        brandingPillValue.postIfChanged(newBrandingPillValue);
    }

    public LiveData<CarrierAggregationViewState> getCarrierAggregation()
    {
        return carrierAggregation;
    }

    public void setCarrierAggregation(CarrierAggregationViewState newCarrierAggregation)
    {
        carrierAggregation.postIfChanged(newCarrierAggregation);
    }

    public LiveData<NrSecondaryCellViewState> getNrSecondaryCell()
    {
        return nrSecondaryCell;
    }

    public void setNrSecondaryCell(NrSecondaryCellViewState newNrSecondaryCell)
    {
        nrSecondaryCell.postIfChanged(newNrSecondaryCell);
    }

    public LiveData<String> getNrLastSeenText()
    {
        return nrLastSeenText;
    }

    /**
     * @param newNrLastSeenText The idle card's "Last seen ..." line, or an empty string when the
     *                          card is not idle.
     */
    public void setNrLastSeenText(String newNrLastSeenText)
    {
        nrLastSeenText.postIfChanged(newNrLastSeenText);
    }

    /**
     * Records the most recent live NR secondary cell sighting for the card's idle state.
     *
     * @param sighting The sighting to retain, never null.
     */
    public synchronized void setLastNrSecondaryCellSighting(NrSecondaryCellSighting sighting)
    {
        lastNrSecondaryCellSighting = sighting;
    }

    /**
     * @return The last live NR secondary cell sighting as one consistent snapshot, or
     * {@link NrSecondaryCellSighting#NONE} when none has been seen. Never null.
     */
    public synchronized NrSecondaryCellSighting getLastNrSecondaryCellSighting()
    {
        return lastNrSecondaryCellSighting;
    }

    public synchronized void clearLastNrSecondaryCell()
    {
        lastNrSecondaryCellSighting = NrSecondaryCellSighting.NONE;
    }

    public LiveData<Location> getLocation()
    {
        return location;
    }

    public void setLocation(Location newLocation)
    {
        location.postIfChanged(newLocation);
    }

    public LiveData<Boolean> getProviderEnabled()
    {
        return providerEnabled;
    }

    public void setProviderEnabled(boolean isProviderEnabled)
    {
        providerEnabled.postIfChanged(isProviderEnabled);
    }

    public LiveData<Boolean> getAirplaneModeActive()
    {
        return airplaneModeActive;
    }

    public void setAirplaneModeActive(boolean isAirplaneModeActive)
    {
        airplaneModeActive.postIfChanged(isAirplaneModeActive);
    }

    public LiveData<CellularProtocol> getServingCellProtocol()
    {
        return servingCellProtocol;
    }

    public void setServingCellProtocol(CellularProtocol newProtocol)
    {
        servingCellProtocol.postIfChanged(newProtocol);
    }

    public LiveData<String> getMcc()
    {
        return mcc;
    }

    public void setMcc(String newMcc)
    {
        mcc.postIfChanged(newMcc);
    }

    public LiveData<String> getMnc()
    {
        return mnc;
    }

    public void setMnc(String newMnc)
    {
        mnc.postIfChanged(newMnc);
    }

    public LiveData<String> getAreaCode()
    {
        return areaCode;
    }

    public void setAreaCode(String newAreaCode)
    {
        areaCode.postIfChanged(newAreaCode);
    }

    public LiveData<Long> getCellId()
    {
        return cellId;
    }

    public void setCellId(Long newCellId)
    {
        cellId.postIfChanged(newCellId);
    }

    public LiveData<String> getChannelNumber()
    {
        return channelNumber;
    }

    public void setChannelNumber(String newChannelNumber)
    {
        channelNumber.postIfChanged(newChannelNumber);
    }

    public LiveData<String> getFrequency()
    {
        return frequency;
    }

    public void setFrequency(String newFrequency)
    {
        frequency.postIfChanged(newFrequency);
    }

    public LiveData<String> getBand()
    {
        return band;
    }

    public void setBand(String newBand)
    {
        band.postIfChanged(newBand);
    }

    public LiveData<String> getLteBand()
    {
        return lteBand;
    }

    public void setLteBand(String newLteBand)
    {
        lteBand.postIfChanged(newLteBand);
    }

    public LiveData<String> getPci()
    {
        return pci;
    }

    public void setPci(String newPci)
    {
        pci.postIfChanged(newPci);
    }

    public LiveData<String> getBandwidth()
    {
        return bandwidth;
    }

    public void setBandwidth(String newBandwidth)
    {
        bandwidth.postIfChanged(newBandwidth);
    }

    public LiveData<String> getTa()
    {
        return ta;
    }

    public void setTa(String newTa)
    {
        ta.postIfChanged(newTa);
    }

    public LiveData<String> getCqi()
    {
        return cqi;
    }

    public void setCqi(String newTa)
    {
        cqi.postIfChanged(newTa);
    }

    public LiveData<Integer> getSignalOne()
    {
        return signalOne;
    }

    public void setSignalOne(Integer newSignal)
    {
        signalOne.postIfChanged(newSignal);
    }

    public LiveData<Integer> getSignalTwo()
    {
        return signalTwo;
    }

    public void setSignalTwo(Integer newSignal)
    {
        signalTwo.postIfChanged(newSignal);
    }

    public LiveData<Integer> getSignalThree()
    {
        return signalThree;
    }

    public void setSignalThree(Integer newSignal)
    {
        signalThree.postIfChanged(newSignal);
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
