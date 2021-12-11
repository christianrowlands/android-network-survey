package com.craxiom.networksurvey.fragments.model;

import android.content.Context;
import android.net.wifi.WifiManager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.SortedList;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.model.WifiRecordWrapper;

/**
 * The view model for the wifi scan results fragment. Storing the list here allows the list to live beyond the
 * fragment lifecycle. This view model can be scoped to various levels, but at the time of this writing it is being
 * scoped to the navigation graph's cycle. This allows the view model to remain present until the app is force closed
 * or killed for any other reason.
 * <p>
 * Information on this was pulled from a lot of places, but this tutorial seemed to provide the most help:
 * https://pspdfkit.com/blog/2019/using-viewmodels-to-retain-state-on-android/
 * <p>
 * FWIW, most blogs and developer guides indicated to use the Fragments onSaveInstanceState method, but after trying
 * for hours I could not get the state I saved there to show up in the onCreate, or any of the other Fragment lifecycle
 * "on" methods.
 * <p>
 * Eventually, we might want to use the Androidx Room Database to store the Wi-Fi scan to disk because this solution
 * only works while the activity is still alive.
 *
 * @since 1.6.0
 */
public class WifiViewModel extends ViewModel
{
    private final SortedList<WifiRecordWrapper> wifiSortedList;
    private final MutableLiveData<Integer> scanStatusId = new MutableLiveData<>(R.string.scan_status_scanning);
    private final MutableLiveData<Integer> apsInLastScan = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> scanNumber = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> updatesPaused = new MutableLiveData<>(false);

    private int sortByIndex = 0;

    public WifiViewModel()
    {
        wifiSortedList = new SortedList<>(WifiRecordWrapper.class, new WifiRecordSortedListCallback());
    }

    public SortedList<WifiRecordWrapper> getWifiList()
    {
        return wifiSortedList;
    }

    public void setSortByIndex(int newSortByIndex)
    {
        sortByIndex = newSortByIndex;
    }

    public int getSortByIndex()
    {
        return sortByIndex;
    }

    public void setScanStatusId(int stringResourceId)
    {
        scanStatusId.setValue(stringResourceId);
    }

    public MutableLiveData<Integer> getScanStatusId()
    {
        return scanStatusId;
    }

    public void setApsInLastScan(int apsInLastScan)
    {
        this.apsInLastScan.postValue(apsInLastScan);
    }

    public LiveData<Integer> getApsInLastScan()
    {
        return apsInLastScan;
    }

    public LiveData<Boolean> areUpdatesPaused()
    {
        return updatesPaused;
    }

    public void toggleUpdatesPaused(Context context)
    {
        @SuppressWarnings("ConstantConditions") final Boolean newPausedValue = !updatesPaused.getValue();
        updatesPaused.postValue(newPausedValue);

        // Update the status UI to reflect the new state
        if (context == null) return;
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled())
        {
            setScanStatusId(newPausedValue ? R.string.scan_status_paused : R.string.scan_status_scanning);
        } else
        {
            setScanStatusId(R.string.wifi_scan_status_disabled);
        }
    }

    /**
     * Increments the Scan Number by 1, unless the scan number is not set, in which case the scan
     * number is set to 1.
     */
    public void incrementScanNumber()
    {
        final Integer scanNum = scanNumber.getValue();
        scanNumber.setValue(scanNum != null ? scanNum + 1 : 1);
    }

    public LiveData<Integer> getScanNumber()
    {
        return scanNumber;
    }

    /**
     * A Sorted list callback for controlling the behavior of the Wi-Fi records sorted list.
     */
    private class WifiRecordSortedListCallback extends SortedList.Callback<WifiRecordWrapper>
    {
        @Override
        public int compare(WifiRecordWrapper record1, WifiRecordWrapper record2)
        {
            // CAUTION!!! The switch statement here needs to be kept in sync with the values from wifi_network_sort_options in arrays.xml
            switch (sortByIndex)
            {
                case 1: // SSID
                    return record1.getWifiBeaconRecord().getData().getSsid().compareTo(record2.getWifiBeaconRecord().getData().getSsid());

                case 2: // BSSID
                    return record1.getWifiBeaconRecord().getData().getBssid().compareTo(record2.getWifiBeaconRecord().getData().getBssid());

                case 3: // Channel
                    return Integer.compare(record1.getWifiBeaconRecord().getData().getChannel().getValue(), record2.getWifiBeaconRecord().getData().getChannel().getValue());

                case 4: // Frequency
                    return Integer.compare(record1.getWifiBeaconRecord().getData().getFrequencyMhz().getValue(), record2.getWifiBeaconRecord().getData().getFrequencyMhz().getValue());

                case 5: // Security Type
                    return WifiBeaconMessageConstants.getEncryptionTypeString(record1.getWifiBeaconRecord().getData().getEncryptionType())
                            .compareTo(WifiBeaconMessageConstants.getEncryptionTypeString(record2.getWifiBeaconRecord().getData().getEncryptionType()));

                default: // Signal Strength
                    // Signal Strength is index 0 in the array, but we also use it as the default case
                    // Invert the sort so that the strongest records are at the top (descending)
                    return -1 * Float.compare(record1.getWifiBeaconRecord().getData().getSignalStrength().getValue(), record2.getWifiBeaconRecord().getData().getSignalStrength().getValue());
            }
        }

        @Override
        public void onChanged(int position, int count)
        {

        }

        @Override
        public boolean areContentsTheSame(WifiRecordWrapper oldRecord, WifiRecordWrapper newRecord)
        {
            return false;
        }

        @Override
        public boolean areItemsTheSame(WifiRecordWrapper record1, WifiRecordWrapper record2)
        {
            return false;
        }

        @Override
        public void onInserted(int position, int count)
        {

        }

        @Override
        public void onRemoved(int position, int count)
        {

        }

        @Override
        public void onMoved(int fromPosition, int toPosition)
        {

        }
    }
}