package com.craxiom.networksurvey.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.SortedList;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.databinding.FragmentWifiNetworksListBinding;
import com.craxiom.networksurvey.fragments.model.WifiViewModel;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiNetwork;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfoList;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * The fragment that displays a list of Wi-Fi networks returned from the scan.
 *
 * @since 0.1.2
 */
public class WifiNetworksFragment extends AServiceDataFragment implements IWifiSurveyRecordListener
{
    private FragmentWifiNetworksListBinding binding;
    private SortedList<WifiRecordWrapper> wifiRecordSortedList;
    private final Object wifiRecordSortedListLock = new Object();
    private final Handler uiThreadHandler;

    private WifiViewModel viewModel;

    private Context applicationContext;
    private MyWifiNetworkRecyclerViewAdapter wifiNetworkRecyclerViewAdapter;
    private String currentConnectedBssid = null;

    private long lastScanTime = 0;
    private boolean throttlingNotificationShown = false;

    /**
     * Only show the prompt to enable Wi-Fi one time per instance of this Wi-Fi fragment.
     */
    private boolean promptedToEnableWifi = false;

    private BroadcastReceiver wifiBroadcastReceiver;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes).
     */
    public WifiNetworksFragment()
    {
        uiThreadHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        applicationContext = requireActivity().getApplicationContext();
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        binding = FragmentWifiNetworksListBinding.inflate(inflater);

        viewModel = new ViewModelProvider(requireActivity()).get(WifiViewModel.class);

        binding.setVm(viewModel);

        wifiRecordSortedList = viewModel.getWifiList();

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        viewModel.setSortByIndex(preferences.getInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, 0));

        wifiNetworkRecyclerViewAdapter = new MyWifiNetworkRecyclerViewAdapter(wifiRecordSortedList, getContext(), this);
        binding.wifiNetworkList.setAdapter(wifiNetworkRecyclerViewAdapter);

        binding.wifiNetworkList.addItemDecoration(new DividerItemDecoration(binding.wifiNetworkList.getContext(), DividerItemDecoration.VERTICAL));

        binding.pauseButton.setOnClickListener(v -> viewModel.toggleUpdatesPaused(getContext()));
        binding.sortButton.setOnClickListener(v -> showSortByDialog());

        initializeView();

        final Context context = requireContext();

        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
        viewModel.getScanStatusId().observe(viewLifecycleOwner,
                scanStatusId -> binding.scanStatus.setText(context.getString(scanStatusId)));

        viewModel.getApsInLastScan().observe(viewLifecycleOwner,
                apCount -> binding.apsInScan.setText(context.getString(R.string.wifi_aps_in_scan, apCount)));

        viewModel.getScanNumber().observe(viewLifecycleOwner,
                scanNumber -> binding.scanNumber.setText(context.getString(R.string.scan_number, scanNumber)));

        viewModel.areUpdatesPaused().observe(viewLifecycleOwner,
                paused -> {
                    binding.pauseButton.setBackgroundResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);

                    // If we are transitioning to un-pause scan updates, then artificially reset the last scan time so that we don't
                    // think that scans are being throttled by the Android OS.
                    if (paused) lastScanTime = System.currentTimeMillis();
                });

        return binding.getRoot();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        registerWifiBroadcastReceiver();

        checkWifiEnabled();

        startAndBindToService();

        checkForScanThrottlingAndroid11();
        
        // Update the connected BSSID when returning to this fragment
        // This ensures the connection indicator is shown immediately when navigating back from details
        updateConnectedNetworkStatus();
    }

    @Override
    public void onPause()
    {
        unregisterWifiBroadcastReceiver();

        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
        viewModel.getScanStatusId().removeObservers(viewLifecycleOwner);
        viewModel.getApsInLastScan().removeObservers(viewLifecycleOwner);
        viewModel.getScanNumber().removeObservers(viewLifecycleOwner);
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
        service.registerWifiSurveyRecordListener(this);
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        service.unregisterWifiSurveyRecordListener(this);
        super.onSurveyServiceDisconnecting(service);
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        //noinspection ConstantConditions
        if (viewModel.areUpdatesPaused().getValue()) return;

        // Move this back to the UI thread since we are updating the UI
        uiThreadHandler.post(() -> {
            try
            {
                checkForScanThrottling();

                // Update the currently connected BSSID
                currentConnectedBssid = getCurrentConnectedBssid();

                viewModel.incrementScanNumber();
                viewModel.setApsInLastScan(wifiBeaconRecords.size());

                synchronized (wifiRecordSortedListLock)
                {
                    wifiRecordSortedList.clear();
                    wifiRecordSortedList.addAll(wifiBeaconRecords);
                    if (wifiNetworkRecyclerViewAdapter != null)
                    {
                        wifiNetworkRecyclerViewAdapter.setConnectedBssid(currentConnectedBssid);
                        wifiNetworkRecyclerViewAdapter.notifyDataSetChanged();
                    }

                    updateSharedModelWifiNetworkList();
                }
            } catch (Exception e)
            {
                // IllegalStateExceptions are happening because of the requireContext call. I am guessing this is due
                // to the fact that the wifi results are coming back after the user has switched away from the fragment
                // but the listener has not been removed yet. Basically a race condition. We can ignore these.
                Timber.e(e, "Could not update the Wi-Fi Fragment UI due to an exception");
            }
        });
    }

    /**
     * Navigates to the Wi-Fi details screen for the selected Wi-Fi network.
     */
    public void navigateToWifiDetails(WifiNetwork wifiNetwork)
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        try
        {
            SharedViewModel viewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
            viewModel.triggerNavigationToWifiDetails(wifiNetwork);
        } catch (Exception e)
        {
            // An IllegalArgumentException can occur when the user switches to a new fragment (e.g. cellular details)
            // before the navigation is complete. This is an edge case that we can ignore.
            Timber.e(e, "Could not navigate to the Wi-Fi Details Fragment");
        }
    }

    /**
     * This is not ideal, but the {@link com.craxiom.networksurvey.ui.main.HomeScreenKt} needs a
     * way to access the latest Wi-Fi scan results when the user navigates to the Wi-Fi Spectrum
     * screen. Therefore, we are updating the shared view model with the latest Wi-Fi scan results
     * every time we get a new scan result.
     */
    private void updateSharedModelWifiNetworkList()
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        List<WifiRecordWrapper> wifiNetworks = new ArrayList<>();
        int size = wifiRecordSortedList.size();
        for (int i = 0; i < size; i++)
        {
            wifiNetworks.add(wifiRecordSortedList.get(i));
        }
        WifiNetworkInfoList wifiNetworkInfoList = new WifiNetworkInfoList(wifiNetworks);

        SharedViewModel viewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
        Timber.i("Updating viewModel with the latest Wi-Fi scan results %s", wifiNetworkInfoList);
        viewModel.updateWifiNetworkInfoList(wifiNetworkInfoList);
    }

    /**
     * Updates the view with the information stored in the view model.
     *
     * @since 1.6.0
     */
    private void initializeView()
    {
        final Context context = requireContext();

        final Integer scanStatusId = viewModel.getScanStatusId().getValue();
        if (scanStatusId != null)
        {
            binding.scanStatus.setText(context.getString(scanStatusId));
        }

        final Integer apCount = viewModel.getApsInLastScan().getValue();
        if (apCount != null)
        {
            binding.apsInScan.setText(context.getString(R.string.wifi_aps_in_scan, apCount));
        }

        final Integer scanNumber = viewModel.getScanNumber().getValue();
        if (scanNumber != null)
        {
            binding.scanNumber.setText(context.getString(R.string.scan_number, scanNumber));
        }
    }

    /**
     * Show the Sort Dialog so the user can pick how they want to sort the list of Wi-Fi networks.
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

        builder.setSingleChoiceItems(R.array.wifi_network_sort_options, viewModel.getSortByIndex(),
                (dialog, index) -> {
                    onSortByChanged(preferences, index);
                    dialog.dismiss();
                });
        final AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(activity);
        dialog.show();
    }

    /**
     * Saves the new sort by index in the shared preferences, triggers a resort of the Wi-Fi networks sorted list, and
     * then notifies the recycler view that the data has changed.
     *
     * @param preferences   The SharedPreferences to store the sort by index in.
     * @param selectedIndex The newly selected sort by index (from arrays.xml).
     */
    private void onSortByChanged(SharedPreferences preferences, int selectedIndex)
    {
        synchronized (wifiRecordSortedListLock)
        {
            preferences.edit().putInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, selectedIndex).apply();
            viewModel.setSortByIndex(selectedIndex);

            wifiRecordSortedList.beginBatchedUpdates();

            final ArrayList<WifiRecordWrapper> tempWifiNetworkList = new ArrayList<>();

            final int sortedListSize = wifiRecordSortedList.size();
            for (int i = 0; i < sortedListSize; ++i)
            {
                tempWifiNetworkList.add(wifiRecordSortedList.get(i));
            }
            wifiRecordSortedList.clear();
            wifiRecordSortedList.addAll(tempWifiNetworkList);
            tempWifiNetworkList.clear();

            wifiRecordSortedList.endBatchedUpdates();

            if (wifiNetworkRecyclerViewAdapter != null)
            {
                wifiNetworkRecyclerViewAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Creates and registers a Wi-Fi receiver that is notified of Wi-Fi state changes (i.e. when Wi-Fi is
     * turned on and off). This is used to update the UI status text, and to kick off the Network Survey Service.
     *
     * @since 1.0.0
     */
    private void registerWifiBroadcastReceiver()
    {
        wifiBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                final String action = intent.getAction();

                if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action))
                {
                    final int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                    //noinspection SwitchStatementWithoutDefaultBranch
                    switch (state)
                    {
                        case WifiManager.WIFI_STATE_DISABLED ->
                                viewModel.setScanStatusId(R.string.wifi_scan_status_disabled);
                        case WifiManager.WIFI_STATE_ENABLED ->
                        {
                            //noinspection ConstantConditions
                            viewModel.setScanStatusId(viewModel.areUpdatesPaused().getValue() ? R.string.scan_status_paused : R.string.scan_status_scanning);
                            startAndBindToService();
                        }
                    }
                }
            }
        };

        // Register for broadcasts on Wi-Fi state change
        IntentFilter filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        requireActivity().registerReceiver(wifiBroadcastReceiver, filter);
    }

    /**
     * Unregisters the Wi-Fi receiver that is notified of state changes (i.e. when Wi-Fi is turned on and off).
     */
    private void unregisterWifiBroadcastReceiver()
    {
        if (wifiBroadcastReceiver != null)
        {
            requireActivity().unregisterReceiver(wifiBroadcastReceiver);
        }
    }

    /**
     * Checks to see if the Wi-Fi manager is present, and if Wi-Fi is enabled.
     * <p>
     * After the check to see if Wi-Fi is enabled, if Wi-Fi is currently disabled the user is then prompted to turn on
     * Wi-Fi.
     * <p>
     * The prompt to enable Wi-Fi is only shown once per creation of this fragment.
     */
    private void checkWifiEnabled()
    {
        if (promptedToEnableWifi) return;

        try
        {
            final WifiManager wifiManager = (WifiManager) requireContext().getSystemService(Context.WIFI_SERVICE);

            if (wifiManager != null && !wifiManager.isWifiEnabled())
            {
                Timber.i("Wi-Fi is disabled, prompting the user to enable it");

                promptedToEnableWifi = true;

                if (Build.VERSION.SDK_INT >= 29)
                {
                    final Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                    panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(panelIntent);
                } else
                {
                    // Open the Wi-Fi setting pages after a couple seconds
                    Toast.makeText(requireContext(), getString(R.string.turn_on_wifi), Toast.LENGTH_SHORT).show();
                    new Handler().postDelayed(() -> {
                        try
                        {
                            final Intent wifiSettingIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                            wifiSettingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(wifiSettingIntent);
                        } catch (Exception e)
                        {
                            // An IllegalStateException can occur when the fragment is no longer attached to the activity
                            // This edge case can occur when the user switches away from the Wi-Fi fragment before this
                            // delayed code is executed.
                            Timber.e(e, "Could not kick off the Wifi Settings Intent for the older pre Android 10 setup");
                        }
                    }, 2000);
                }
            }
        } catch (Exception e)
        {
            Timber.e(e, "Something went wrong when trying to prompt the user to enable wifi");
        }
    }

    /**
     * Check to see if Scan throttling is enabled. This is a specific version of the method for Android 11 and higher
     * since as of API level 30 there is the {@link WifiManager#isScanThrottleEnabled()} check.
     */
    private void checkForScanThrottlingAndroid11()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        Context context = getContext();
        if (context == null) return;

        // Check the MDM preference to see if we should show the warning
        boolean ignoreWarning = PreferenceUtils.getIgnoreWifiThrottlingWarningPreference(false, context);
        if (ignoreWarning) return;

        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null && wifiManager.isScanThrottleEnabled())
        {
            Timber.i("Wi-Fi scan throttling is enabled (via API call check), prompting the user to disable it");

            showScanThrottlingSnackbar();
        }
    }

    /**
     * Check to see if we can notice that scan throttling is enabled.  I wish we could grab this straight from the
     * OS settings, but it seems that the Android API does not exposes this OS setting.  Therefore, we check to see if
     * the scan interval is significantly longer than what we are requesting it to be.
     * <p>
     * If we do determine that scan throttling is enabled, then alert the user.  Note that the alert should be
     * different for Android 9 vs 10.
     * <p>
     * We have to make sure to handle pausing the UI updates so we don't artificially trigger this alert.
     */
    private void checkForScanThrottling()
    {
        // Scan throttling is new as of Android 9
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) return;

        // There is a better way to check for scan throttling as of API level 30 (see the other method)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return;

        Context context = getContext();
        if (context == null) return;

        // Check the MDM preference to see if we should show the warning
        boolean ignoreWarning = PreferenceUtils.getIgnoreWifiThrottlingWarningPreference(false, context);
        if (ignoreWarning) return;

        if (lastScanTime == 0)
        {
            // First time scanning, so initialize the last scan time.
            lastScanTime = System.currentTimeMillis();
            return;
        }

        // Don't keep annoying the user with the prompts. Notify them once.
        if (throttlingNotificationShown) return;

        final long newScanTime = System.currentTimeMillis();
        final boolean devOptionsEnabled = areDeveloperOptionsEnabled();

        if (service == null) return;
        if (!devOptionsEnabled || newScanTime - lastScanTime > service.getWifiScanRateMs() * 3L)
        {
            showScanThrottlingSnackbar();

            throttlingNotificationShown = true;
        }

        lastScanTime = newScanTime;
    }

    /**
     * Show a Snackbar message to the user with some information about Wi-Fi throttling, and a link to the settings
     * where they can disable throttling.
     *
     * @since 1.4.0
     */
    private void showScanThrottlingSnackbar()
    {
        // Scan throttling is new as of Android 9
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) return;

        String snackbarMessage;
        final boolean devOptionsEnabled = areDeveloperOptionsEnabled();

        // It appears we are not getting scan results as frequently as we are asking for them. It is possible that
        // the Wi-Fi scan rate is being throttled by the Android OS. https://developer.android.com/guide/topics/connectivity/wifi-scan#wifi-scan-throttling
        // Inform the user that they can disable scan throttling in Developer Options
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P)
        {
            snackbarMessage = getString(R.string.android_9_throttling_information);
        } else //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            snackbarMessage = getString(R.string.android_10_throttling_information);
            if (!devOptionsEnabled)
            {
                snackbarMessage += "\n\n" + getString(R.string.enable_developer_options);
            }
        }

        final Snackbar snackbar = Snackbar.make(requireView(), snackbarMessage, Snackbar.LENGTH_INDEFINITE)
                .setAction("Open", v -> startActivity(new Intent(devOptionsEnabled ? Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS : Settings.ACTION_DEVICE_INFO_SETTINGS)))
                .setBackgroundTint(getResources().getColor(R.color.alert_red, null));

        if (snackbar.isShown()) return;

        TextView snackTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        snackTextView.setMaxLines(12);

        snackbar.show();
    }

    /**
     * @return True if the developer options are enabled, false otherwise.
     */
    private boolean areDeveloperOptionsEnabled()
    {
        return Settings.Global.getInt(requireContext().getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    /**
     * Updates the connected network status by refreshing the current BSSID and notifying the adapter.
     * This is called when the fragment resumes and when new scan results arrive.
     */
    private void updateConnectedNetworkStatus()
    {
        currentConnectedBssid = getCurrentConnectedBssid();
        if (wifiNetworkRecyclerViewAdapter != null)
        {
            wifiNetworkRecyclerViewAdapter.setConnectedBssid(currentConnectedBssid);
            wifiNetworkRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Gets the BSSID of the currently connected WiFi network.
     * <p>
     * Note: This method uses the deprecated WifiManager.getConnectionInfo() API because:
     * 1. It still works reliably on all Android versions including Android 16
     * 2. The new ConnectivityManager approach requires complex asynchronous NetworkCallback
     * with FLAG_INCLUDE_LOCATION_INFO to get the actual BSSID (otherwise returns "02:00:00:00:00:00")
     * 3. Since we only need this for UI display when the fragment is visible, the simpler
     * synchronous deprecated method is the pragmatic choice
     *
     * @return The BSSID of the connected WiFi network, or null if not connected to WiFi.
     */
    @SuppressWarnings("deprecation")
    private String getCurrentConnectedBssid()
    {
        try
        {
            Context context = getContext();
            if (context == null) return null;

            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null)
            {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null)
                {
                    String bssid = wifiInfo.getBSSID();
                    // Check if it's a valid BSSID (not the default "02:00:00:00:00:00" placeholder)
                    if (bssid != null && !bssid.equals("02:00:00:00:00:00"))
                    {
                        return bssid;
                    }
                }
            }
        } catch (Exception e)
        {
            Timber.e(e, "Failed to get current connected WiFi BSSID");
        }
        return null;
    }
}
