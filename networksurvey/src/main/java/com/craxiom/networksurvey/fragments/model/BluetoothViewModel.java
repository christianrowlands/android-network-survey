package com.craxiom.networksurvey.fragments.model;

import android.bluetooth.BluetoothAdapter;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.SortedList;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.model.SortedSet;

import java.util.Objects;

/**
 * The view model for the bluetooth scan results fragment. Storing the list here allows the list to
 * live beyond the fragment lifecycle. This view model can be scoped to various levels, but at the
 * time of this writing it is being scoped to the navigation graph's cycle. This allows the view
 * model to remain present until the app is force closed or killed for any other reason.
 *
 * @since 1.11
 */
public class BluetoothViewModel extends ViewModel
{
    private final SortedSet<BluetoothRecord> bluetoothSortedList;
    private final MutableLiveData<Integer> scanStatusId = new MutableLiveData<>(R.string.scan_status_scanning);
    private final MutableLiveData<Integer> devicesInScan = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> updatesPaused = new MutableLiveData<>(false);

    private int sortByIndex = 0;

    public BluetoothViewModel()
    {
        bluetoothSortedList = new SortedSet<>(BluetoothRecord.class, new RecordSortedListCallback());
    }

    public SortedSet<BluetoothRecord> getBluetoothList()
    {
        return bluetoothSortedList;
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

    public void setDevicesInScan(int devicesInScan)
    {
        this.devicesInScan.postValue(devicesInScan);
    }

    public LiveData<Integer> getDevicesInScan()
    {
        return devicesInScan;
    }

    public LiveData<Boolean> areUpdatesPaused()
    {
        return updatesPaused;
    }

    public void toggleUpdatesPaused()
    {
        @SuppressWarnings("ConstantConditions") final Boolean newPausedValue = !updatesPaused.getValue();
        updatesPaused.postValue(newPausedValue);

        if (!BluetoothAdapter.getDefaultAdapter().isEnabled())
        {
            setScanStatusId(R.string.bluetooth_scan_status_disabled);
        } else
        {
            setScanStatusId(newPausedValue ? R.string.scan_status_paused : R.string.scan_status_scanning);
        }
    }

    /**
     * A Sorted list callback for controlling the behavior of the Bluetooth records sorted list.
     */
    public class RecordSortedListCallback extends SortedList.Callback<BluetoothRecord>
    {
        @Override
        public int compare(BluetoothRecord record1, BluetoothRecord record2)
        {
            // CAUTION!!! The switch statement here needs to be kept in sync with the values from bluetooth_sort_options in arrays.xml
            switch (sortByIndex)
            {
                case 1: // Source Address
                    return record1.getData().getSourceAddress().compareTo(record2.getData().getSourceAddress());

                case 2: // OTA Device Name
                    // Invert the sort so that devices without a device name at all show up at the bottom.
                    return -1 * record1.getData().getOtaDeviceName().compareTo(record2.getData().getOtaDeviceName());

                case 3: // Supported Technologies
                    return BluetoothMessageConstants.getSupportedTechString(record1.getData().getSupportedTechnologies())
                            .compareTo(BluetoothMessageConstants.getSupportedTechString(record2.getData().getSupportedTechnologies()));

                default: // Signal Strength
                    // Signal Strength is index 0 in the array, but we also use it as the default case
                    // Invert the sort so that the strongest records are at the top (descending)
                    if (!record1.getData().hasSignalStrength() && !record2.getData().hasSignalStrength())
                    {
                        return 0;
                    }

                    if (!record1.getData().hasSignalStrength()) return -1;

                    if (!record2.getData().hasSignalStrength()) return 1;

                    return -1 * Float.compare(record1.getData().getSignalStrength().getValue(), record2.getData().getSignalStrength().getValue());
            }
        }

        @Override
        public void onChanged(int position, int count)
        {

        }

        @Override
        public boolean areContentsTheSame(BluetoothRecord oldRecord, BluetoothRecord newRecord)
        {
            return false;
        }

        @Override
        public boolean areItemsTheSame(BluetoothRecord record1, BluetoothRecord record2)
        {
            return Objects.equals(record1.getData().getSourceAddress(), record2.getData().getSourceAddress());
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