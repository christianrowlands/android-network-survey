package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.databinding.FragmentBluetoothListBinding;
import com.craxiom.networksurvey.fragments.model.BluetoothViewModel;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.model.SortedSet;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

/**
 * The fragment that displays a list of Bluetooth devices returned from the scan.
 *
 * @since 1.0.0
 */
public class BluetoothFragment extends AServiceDataFragment implements IBluetoothSurveyRecordListener
{
    private static final int ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID = 42;
    private FragmentBluetoothListBinding binding;

    private SortedSet<BluetoothRecord> bluetoothRecordSortedSet;
    private Handler uiThreadHandler;
    private BluetoothViewModel viewModel;

    private Context applicationContext;
    private BluetoothRecyclerViewAdapter bluetoothRecyclerViewAdapter;

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
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        uiThreadHandler = new Handler(Looper.getMainLooper());
        applicationContext = requireActivity().getApplicationContext();
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentBluetoothListBinding.inflate(inflater);

        viewModel = new ViewModelProvider(requireActivity()).get(BluetoothViewModel.class);

        binding.setVm(viewModel);

        bluetoothRecordSortedSet = viewModel.getBluetoothList();

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        viewModel.setSortByIndex(preferences.getInt(NetworkSurveyConstants.PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER, 0));

        bluetoothRecyclerViewAdapter = new BluetoothRecyclerViewAdapter(bluetoothRecordSortedSet, getContext(), this);
        binding.bluetoothDeviceList.setAdapter(bluetoothRecyclerViewAdapter);

        binding.bluetoothDeviceList.addItemDecoration(new DividerItemDecoration(binding.bluetoothDeviceList.getContext(), DividerItemDecoration.VERTICAL));

        binding.pauseButton.setOnClickListener(v -> viewModel.toggleUpdatesPaused());
        binding.sortButton.setOnClickListener(v -> showSortByDialog());

        initializeView();

        final Context context = requireContext();

        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
        viewModel.getScanStatusId().observe(viewLifecycleOwner,
                scanStatusId -> binding.scanStatus.setText(context.getString(scanStatusId)));

        viewModel.getDevicesInScan().observe(viewLifecycleOwner,
                count -> binding.btDevicesInScan.setText(context.getString(R.string.bluetooth_devices_in_scan, count)));

        viewModel.areUpdatesPaused().observe(viewLifecycleOwner,
                paused -> binding.pauseButton.setBackgroundResource(paused ? R.drawable.ic_play : R.drawable.ic_pause));

        return binding.getRoot();
    }

    @SuppressLint("InlinedApi") // Validated in the hasBtScanPermission check
    @Override
    public void onResume()
    {
        super.onResume();

        showBluetoothPermissionRationaleAndRequestPermissions();

        if (!hasBtScanPermission())
        {
            final Context context = getContext();
            if (context != null)
            {
                viewModel.setScanStatusId(R.string.scan_status_permission);
                Toast.makeText(context, getString(R.string.grant_bluetooth_scan_permission), Toast.LENGTH_LONG).show();
            }

            return;
        }

        bluetoothScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS, applicationContext);

        registerBluetoothBroadcastReceiver();

        checkBluetoothEnabled();

        startAndBindToService();
    }

    @Override
    public void onPause()
    {
        unregisterBluetoothBroadcastReceiver();

        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
        viewModel.getScanStatusId().removeObservers(viewLifecycleOwner);
        viewModel.getDevicesInScan().removeObservers(viewLifecycleOwner);
        viewModel.areUpdatesPaused().removeObservers(viewLifecycleOwner);

        super.onDestroyView();
    }

    @Override
    public void onDestroy()
    {
        applicationContext = null;

        super.onDestroy();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        service.registerBluetoothSurveyRecordListener(this);
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        service.unregisterBluetoothSurveyRecordListener(this);
        super.onSurveyServiceDisconnecting(service);
    }

    @Override
    public void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        //noinspection ConstantConditions
        if (viewModel.areUpdatesPaused().getValue()) return;

        uiThreadHandler.post(() -> {
            //noinspection SynchronizeOnNonFinalField
            synchronized (bluetoothRecordSortedSet)
            {
                bluetoothRecordSortedSet.add(bluetoothRecord);

                checkAndRemoveStaleRecords();

                if (bluetoothRecyclerViewAdapter != null)
                {
                    bluetoothRecyclerViewAdapter.notifyDataSetChanged();
                }

                viewModel.setDevicesInScan(bluetoothRecordSortedSet.size());
            }
        });
    }

    @Override
    public void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        //noinspection ConstantConditions
        if (viewModel.areUpdatesPaused().getValue()) return;

        uiThreadHandler.post(() -> {
            //noinspection SynchronizeOnNonFinalField
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

                viewModel.setDevicesInScan(bluetoothRecordSortedSet.size());
            }
        });
    }

    /**
     * Navigates to the Bluetooth details screen for the selected Bluetooth device.
     */
    public void navigateToBluetoothDetails(BluetoothRecordData bluetoothData)
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        try
        {
            SharedViewModel viewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
            viewModel.triggerNavigationToBluetooth(bluetoothData);
        } catch (Exception e)
        {
            // An IllegalArgumentException can occur when the user switches to a new fragment (e.g. cellular details)
            // before the navigation is complete. This is an edge case that we can ignore.
            Timber.e(e, "Could not navigate to the Bluetooth Details Fragment");
        }
    }

    /**
     * Updates the view with the information stored in the view model.
     *
     * @since 1.11
     */
    private void initializeView()
    {
        final Context context = requireContext();

        final Integer scanStatusId = viewModel.getScanStatusId().getValue();
        if (scanStatusId != null)
        {
            binding.scanStatus.setText(context.getString(scanStatusId));
        }

        final Integer count = viewModel.getDevicesInScan().getValue();
        if (count != null)
        {
            binding.btDevicesInScan.setText(context.getString(R.string.bluetooth_devices_in_scan, count));
        }
    }

    /**
     * Note that the {@link Manifest.permission#BLUETOOTH_CONNECT} permission was added in Android 12, so this method
     * returns true for all older versions.
     *
     * @return True if the {@link Manifest.permission#BLUETOOTH_CONNECT} permission has been granted. False otherwise.
     * @since 1.6.0
     */
    private boolean hasBtConnectPermission()
    {
        // The BLUETOOTH_CONNECT permission was added in Android 12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;

        final Context context = getContext();
        if (context == null || ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The BLUETOOTH_CONNECT permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Note that the {@link Manifest.permission#BLUETOOTH_SCAN} permission was added in Android 12, so this method
     * returns true for all older versions.
     *
     * @return True if the {@link Manifest.permission#BLUETOOTH_SCAN} permission has been granted. False otherwise.
     * @since 1.6.0
     */
    private boolean hasBtScanPermission()
    {
        // The BLUETOOTH_SCAN permission was added in Android 12
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The BLUETOOTH_SCAN permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Check to see if we should show the rationale for any of the Bluetooth permissions. If so,
     * then display a dialog that explains what permissions we need for bluetooth to work properly.
     * <p>
     * If we should not show the rationale, then just request the permissions.
     */
    private void showBluetoothPermissionRationaleAndRequestPermissions()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;

        final Context context = getContext();
        if (context == null) return;

        if (missingAnyPermissions(NetworkSurveyActivity.BLUETOOTH_PERMISSIONS))
        {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(getString(R.string.bluetooth_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.bluetooth_permissions_rationale));
            alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> requestBluetoothPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        }
    }

    /**
     * Request the permissions needed for bluetooth if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestBluetoothPermissions()
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        if (missingAnyPermissions(NetworkSurveyActivity.BLUETOOTH_PERMISSIONS))
        {
            ActivityCompat.requestPermissions(activity, NetworkSurveyActivity.BLUETOOTH_PERMISSIONS, ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID);
        }
    }

    /**
     * @return True if any of the permissions have been denied. False if all the permissions
     * have been granted.
     */
    private boolean missingAnyPermissions(String[] permissions)
    {
        final Context context = getContext();
        if (context == null) return true;
        for (String permission : permissions)
        {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
            {
                Timber.i("Missing the permission: %s", permission);
                return true;
            }
        }

        return false;
    }

    /**
     * Runs through the records in the current view, and removes any that have timestamps that are older than n seconds.
     * <p>
     * This method does NOT call a notify method to trigger an update to the UI. The caller must handle that on its own.
     */
    private void checkAndRemoveStaleRecords()
    {
        //noinspection SynchronizeOnNonFinalField
        synchronized (bluetoothRecordSortedSet)
        {
            final long currentTimeMillis = System.currentTimeMillis();

            final LinkedList<BluetoothRecord> itemsToRemove = new LinkedList<>();

            final int sortedListSize = bluetoothRecordSortedSet.size();
            for (int i = 0; i < sortedListSize; ++i)
            {
                final BluetoothRecord bluetoothRecord = bluetoothRecordSortedSet.get(i);
                // Adding 5_000 ms so that we have plenty of time for the next scan to return its results
                if (NsUtils.getEpochFromRfc3339(bluetoothRecord.getData().getDeviceTime()) + bluetoothScanRateMs + 5_000 < currentTimeMillis)
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

        builder.setSingleChoiceItems(R.array.bluetooth_sort_options, viewModel.getSortByIndex(),
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
        //noinspection SynchronizeOnNonFinalField
        synchronized (bluetoothRecordSortedSet)
        {
            preferences.edit().putInt(NetworkSurveyConstants.PROPERTY_BLUETOOTH_DEVICES_SORT_ORDER, selectedIndex).apply();
            viewModel.setSortByIndex(selectedIndex);

            bluetoothRecordSortedSet.beginBatchedUpdates();

            final ArrayList<BluetoothRecord> tempBluetoothList = new ArrayList<>();

            final int sortedListSize = bluetoothRecordSortedSet.size();
            for (int i = 0; i < sortedListSize; ++i)
            {
                tempBluetoothList.add(bluetoothRecordSortedSet.get(i));
            }
            bluetoothRecordSortedSet.clear();
            // It is ok to use addAll here because we don't need the custom add() implementation that evicts duplicates
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
                        case BluetoothAdapter.STATE_OFF ->
                                viewModel.setScanStatusId(R.string.bluetooth_scan_status_disabled);
                        case BluetoothAdapter.STATE_ON ->
                        {
                            //noinspection ConstantConditions
                            viewModel.setScanStatusId(viewModel.areUpdatesPaused().getValue() ? R.string.scan_status_paused : R.string.scan_status_scanning);
                            startAndBindToService();
                        }
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
        if (bluetoothBroadcastReceiver != null)
        {
            requireActivity().unregisterReceiver(bluetoothBroadcastReceiver);
        }
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
            if (!hasBtConnectPermission())
            {
                // We don't really need the BLUETOOTH_CONNECT permission, but it makes it easier. If the user denied it, just show a toast.
                Toast.makeText(requireContext(), getString(R.string.grant_bluetooth_scan_permission), Toast.LENGTH_LONG).show();
                return;
            }

            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // First, update the status UI if bluetooth is disabled
            if (bluetoothAdapter == null)
            {
                viewModel.setScanStatusId(R.string.bluetooth_scan_status_not_supported);
            } else if (!bluetoothAdapter.isEnabled())
            {
                viewModel.setScanStatusId(R.string.bluetooth_scan_status_disabled);

                // Bluetooth is present, but disabled; prompt the user to enable it, but only ask the user once per app opening (per fragment instance)
                if (promptedToEnableBluetooth) return;

                if (!bluetoothAdapter.isEnabled())
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
                //noinspection ConstantConditions
                viewModel.setScanStatusId(viewModel.areUpdatesPaused().getValue() ? R.string.scan_status_paused : R.string.scan_status_scanning);
            }
        } catch (Exception e)
        {
            Timber.e(e, "Something went wrong when trying to prompt the user to enable Bluetooth");
        }
    }
}
