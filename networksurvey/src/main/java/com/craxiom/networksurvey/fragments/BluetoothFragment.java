package com.craxiom.networksurvey.fragments;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.model.SortedSet;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.IOUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import timber.log.Timber;

/**
 * The fragment that displays a list of Bluetooth devices returned from the scan.
 *
 * @since 1.0.0
 */
public class BluetoothFragment extends Fragment implements IBluetoothSurveyRecordListener
{
    private final SortedSet<BluetoothRecord> bluetoothRecordSortedSet = new SortedSet<>(BluetoothRecord.class, new RecordSortedListCallback());
    private final Handler uiThreadHandler;

    private Context applicationContext;
    private NetworkSurveyService surveyService;
    private BluetoothRecyclerViewAdapter bluetoothRecyclerViewAdapter;
    private TextView scanStatusView;
    private TextView devicesInScanView;

    private volatile boolean updatesPaused = false;

    private int sortByIndex = 0;
    private int bluetoothScanRateMs;

    /**
     * Only show the prompt to enable Bluetooth one time per instance of this fragment.
     */
    private boolean promptedToEnableBluetooth = false;

    private BroadcastReceiver bluetoothBroadcastReceiver;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes).
     */
    public BluetoothFragment()
    {
        uiThreadHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        applicationContext = requireActivity().getApplicationContext();
        super.onCreate(savedInstanceState);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        sortByIndex = preferences.getInt(NetworkSurveyConstants.PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_bluetooth_list, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.bluetooth_device_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        bluetoothRecyclerViewAdapter = new BluetoothRecyclerViewAdapter(bluetoothRecordSortedSet, getContext());
        recyclerView.setAdapter(bluetoothRecyclerViewAdapter);

        final ImageButton pauseButton = view.findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(this::onPauseUiUpdatesToggle);

        final ImageButton sortButton = view.findViewById(R.id.sort_button);
        sortButton.setOnClickListener(v -> showSortByDialog());

        final Context context = requireContext();

        scanStatusView = view.findViewById(R.id.scan_status);
        scanStatusView.setText(context.getString(R.string.scan_status_scanning));

        devicesInScanView = view.findViewById(R.id.bt_devices_in_scan);
        devicesInScanView.setText(context.getString(R.string.bluetooth_devices_in_scan, 0));

        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        bluetoothScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS, applicationContext);

        registerBluetoothBroadcastReceiver();

        checkBluetoothEnabled();

        startAndBindToNetworkSurveyService();
    }

    @Override
    public void onPause()
    {
        unregisterBluetoothBroadcastReceiver();

        if (surveyService != null) surveyService.unregisterBluetoothSurveyRecordListener(this);

        super.onPause();
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        if (updatesPaused) return;

        uiThreadHandler.post(() -> {
            synchronized (bluetoothRecordSortedSet)
            {
                bluetoothRecordSortedSet.add(bluetoothRecord);

                checkAndRemoveStaleRecords();

                if (bluetoothRecyclerViewAdapter != null)
                {
                    bluetoothRecyclerViewAdapter.notifyDataSetChanged();
                }

                devicesInScanView.setText(requireContext().getString(R.string.bluetooth_devices_in_scan, bluetoothRecordSortedSet.size()));
            }
        });
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        if (updatesPaused) return;

        // Move this back to the UI thread since we are updating the UI
        uiThreadHandler.post(() -> {
            synchronized (bluetoothRecordSortedSet)
            {
                // We can't use the SortedList#addAll method because we have not overridden that method in our custom
                // SortedSet implementation of SortedList.
                bluetoothRecords.forEach(bluetoothRecordSortedSet::add);

                checkAndRemoveStaleRecords();

                if (bluetoothRecyclerViewAdapter != null)
                {
                    bluetoothRecyclerViewAdapter.notifyDataSetChanged();
                }

                devicesInScanView.setText(requireContext().getString(R.string.bluetooth_devices_in_scan, bluetoothRecordSortedSet.size()));
            }
        });
    }

    /**
     * Runs through the records in the current view, and removes any that have timestamps that are older than n seconds.
     * <p>
     * This method does NOT call a notify method to trigger an update to the UI. The caller must handle that on its own.
     */
    private void checkAndRemoveStaleRecords()
    {
        Timber.d("Removing any stale Bluetooth records");

        synchronized (bluetoothRecordSortedSet)
        {
            final long currentTimeMillis = System.currentTimeMillis();

            final LinkedList<BluetoothRecord> itemsToRemove = new LinkedList<>();

            final int sortedListSize = bluetoothRecordSortedSet.size();
            for (int i = 0; i < sortedListSize; ++i)
            {
                final BluetoothRecord bluetoothRecord = bluetoothRecordSortedSet.get(i);
                // Adding 5_000 ms so that we have plenty of time for the next scan to return its results
                if (IOUtils.getEpochFromRfc3339(bluetoothRecord.getData().getDeviceTime()) + bluetoothScanRateMs + 5_000 < currentTimeMillis)
                {
                    itemsToRemove.add(bluetoothRecord);
                }
            }

            if (!itemsToRemove.isEmpty())
            {
                itemsToRemove.forEach(bluetoothRecordSortedSet::remove);
            }
        }
    }

    /**
     * Start the Network Survey Service (it won't start if it is already started), and then bind to the service.
     */
    private void startAndBindToNetworkSurveyService()
    {
        // Start the service
        Timber.i("Binding to the Network Survey Service");
        final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        applicationContext.startService(serviceIntent);

        // Bind to the service
        ServiceConnection surveyServiceConnection = new SurveyServiceConnection();
        final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
        Timber.i("NetworkSurveyService bound in the BluetoothFragment: %s", bound);
    }

    /**
     * Notification that UI updates have either been paused or resumed.  Toggle the paused state and update any UI
     * elements.
     *
     * @param view The ImageButton to update the paused/play icon on.
     */
    private void onPauseUiUpdatesToggle(View view)
    {
        scanStatusView.setText(requireContext().getString(updatesPaused ? R.string.scan_status_scanning : R.string.scan_status_paused));
        view.setBackgroundResource(updatesPaused ? R.drawable.ic_pause : R.drawable.ic_play);

        //noinspection NonAtomicOperationOnVolatileField
        updatesPaused = !updatesPaused;
    }

    /**
     * Show the Sort Dialog so the user can pick how they want to sort the list of Bluetooth devices.
     */
    private void showSortByDialog()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null)
        {
            Timber.wtf("The Activity is null so we are unable to show the sorting dialog.");
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_option_sort_by);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        builder.setSingleChoiceItems(R.array.bluetooth_sort_options, sortByIndex,
                (dialog, index) -> {
                    onSortByChanged(preferences, index);
                    dialog.dismiss();
                });
        final AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(activity);
        dialog.show();
    }

    /**
     * Saves the new sort by index in the shared preferences, triggers a resort of the Bluetooth sorted list, and
     * then notifies the recycler view that the data has changed.
     *
     * @param preferences   The SharedPreferences to store the sort by index in.
     * @param selectedIndex The newly selected sort by index (from arrays.xml)
     */
    private void onSortByChanged(SharedPreferences preferences, int selectedIndex)
    {
        synchronized (bluetoothRecordSortedSet)
        {
            preferences.edit().putInt(NetworkSurveyConstants.PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER, selectedIndex).apply();
            sortByIndex = selectedIndex;

            bluetoothRecordSortedSet.beginBatchedUpdates();

            final ArrayList<BluetoothRecord> tempBluetoothList = new ArrayList<>();

            final int sortedListSize = bluetoothRecordSortedSet.size();
            for (int i = 0; i < sortedListSize; ++i)
            {
                tempBluetoothList.add(bluetoothRecordSortedSet.get(i));
            }
            bluetoothRecordSortedSet.clear();
            bluetoothRecordSortedSet.addAll(tempBluetoothList);
            tempBluetoothList.clear();

            bluetoothRecordSortedSet.endBatchedUpdates();

            if (bluetoothRecyclerViewAdapter != null)
            {
                bluetoothRecyclerViewAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Creates and registers a bluetooth receiver that is notified of bluetooth state changes (i.e. when Bluetooth is
     * turned on and off). This is used to update the UI status text, and to kick off the Network Survey Service.
     */
    private void registerBluetoothBroadcastReceiver()
    {
        bluetoothBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                final String action = intent.getAction();

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
                {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    //noinspection SwitchStatementWithoutDefaultBranch
                    switch (state)
                    {
                        case BluetoothAdapter.STATE_OFF:
                            scanStatusView.setText(requireContext().getString(R.string.bluetooth_scan_status_disabled));
                            break;
                        case BluetoothAdapter.STATE_ON:
                            scanStatusView.setText(requireContext().getString(updatesPaused ? R.string.scan_status_paused : R.string.scan_status_scanning));
                            startAndBindToNetworkSurveyService();
                            break;
                    }
                }
            }
        };

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        requireActivity().registerReceiver(bluetoothBroadcastReceiver, filter);
    }

    /**
     * Unregisters the bluetooth receiver that is notified of bluetooth state changes (i.e. when Bluetooth is
     * turned on and off).
     */
    private void unregisterBluetoothBroadcastReceiver()
    {
        if (bluetoothBroadcastReceiver != null) requireActivity().unregisterReceiver(bluetoothBroadcastReceiver);
    }

    /**
     * Checks to see if the Bluetooth adapter is present, and if Bluetooth is enabled.
     * <p>
     * After the check to see if Bluetooth is enabled, if Bluetooth is currently disabled the user is then prompted to
     * turn on Bluetooth.
     * <p>
     * The prompt to enable Bluetooth is only shown once per creation of this fragment.
     * <p>
     * Also update the status UI with the current bluetooth enabled status.
     */
    private void checkBluetoothEnabled()
    {
        try
        {
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // First, update the status UI if bluetooth is disabled
            if (bluetoothAdapter == null)
            {
                scanStatusView.setText(requireContext().getString(R.string.bluetooth_scan_status_not_supported));
            } else if (!bluetoothAdapter.isEnabled())
            {
                scanStatusView.setText(requireContext().getString(R.string.bluetooth_scan_status_disabled));

                // Bluetooth is present, but disabled; prompt the user to enable it, but only ask the user once per app opening (per fragment instance)
                if (promptedToEnableBluetooth) return;

                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled())
                {
                    Timber.i("Bluetooth is disabled, prompting the user to enable it");

                    promptedToEnableBluetooth = true;

                    // Open the Bluetooth enable prompt
                    Toast.makeText(requireContext(), getString(R.string.turn_on_bluetooth), Toast.LENGTH_SHORT).show();
                    new Handler().post(() -> {
                        try
                        {
                            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(enableBtIntent);
                        } catch (Exception e)
                        {
                            // An IllegalStateException can occur when the fragment is no longer attached to the activity
                            Timber.e(e, "Could not kick off the Bluetooth Enable Intent");
                        }
                    });
                }
            } else
            {
                scanStatusView.setText(requireContext().getString(updatesPaused ? R.string.scan_status_paused : R.string.scan_status_scanning));
            }
        } catch (Exception e)
        {
            Timber.e(e, "Something went wrong when trying to prompt the user to enable Bluetooth");
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

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link NetworkSurveyService}.
     * <p>
     * We need to bind to the {@link NetworkSurveyService} so that we can get notified about the Bluetooth scan results.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder)
        {
            Timber.i("%s service connected", name);
            NetworkSurveyService.SurveyServiceBinder serviceBinder = (NetworkSurveyService.SurveyServiceBinder) binder;
            surveyService = (NetworkSurveyService) serviceBinder.getService();
            surveyService.registerBluetoothSurveyRecordListener(BluetoothFragment.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Timber.i("%s service disconnected", name);
            surveyService = null;
        }
    }
}
