package com.craxiom.networksurvey.fragments;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.databinding.FragmentWifiNetworksListBinding;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiNetwork;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.ui.wifi.WifiNetworksCompose;
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel;
import com.craxiom.networksurvey.ui.wifi.model.WifiNetworkInfoList;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * The fragment that displays a list of Wi-Fi networks returned from the scan.
 * <p>
 * The UI is rendered with Jetpack Compose via {@code ui/wifi/WifiListScreen}; this fragment
 * retains the surrounding Android plumbing: service binding, scan-throttling detection,
 * Wi-Fi enable prompt, broadcast receiver, and the {@link SharedViewModel} update for the
 * Wi-Fi Spectrum screen.
 *
 * @since 0.1.2
 */
public class WifiNetworksFragment extends AServiceDataFragment implements IWifiSurveyRecordListener
{
    private FragmentWifiNetworksListBinding binding;
    private final Handler uiThreadHandler;

    private WifiListViewModel viewModel;

    private Context applicationContext;

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

        viewModel = new ViewModelProvider(requireActivity()).get(WifiListViewModel.class);

        // Seed the scan status from the Wi-Fi state since scan callbacks may not arrive immediately
        updateScanStatusFromWifiState();

        final ComposeView composeView = binding.wifiListComposeView;
        WifiNetworksCompose.setContent(
                composeView,
                viewModel,
                this::navigateToWifiDetails,
                this::handleTogglePause);

        return binding.getRoot();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // Reset keepScreenOn to false to ensure screen doesn't stay on
        View view = getView();
        if (view != null)
        {
            view.setKeepScreenOn(false);
        }

        registerWifiBroadcastReceiver();

        checkWifiEnabled();

        startAndBindToService();

        checkForScanThrottlingAndroid11();
    }

    @Override
    public void onPause()
    {
        unregisterWifiBroadcastReceiver();

        super.onPause();
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
    public boolean wantsExcludedRecords()
    {
        // The Wi-Fi network list shows excluded SSIDs (greyed out), so it needs all records.
        return true;
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        // Legacy fragment bailed here when paused; mirror that so the Wi-Fi Spectrum screen
        // (fed by updateSharedModelWifiNetworkList below) freezes in lockstep with the list.
        if (viewModel != null && viewModel.getUiState().getValue().getUpdatesPaused())
        {
            return;
        }

        uiThreadHandler.post(() -> {
            try
            {
                checkForScanThrottling();

                if (viewModel != null)
                {
                    viewModel.onScanResults(wifiBeaconRecords);
                }

                updateSharedModelWifiNetworkList(wifiBeaconRecords);
            } catch (Exception e)
            {
                // IllegalStateExceptions are possible due to race conditions when the user
                // switches away from the fragment but the listener has not yet been removed.
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
            SharedViewModel sharedViewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
            sharedViewModel.triggerNavigationToWifiDetails(wifiNetwork);
        } catch (Exception e)
        {
            // An IllegalArgumentException can occur when the user switches to a new fragment
            // before the navigation is complete. This is an edge case that we can ignore.
            Timber.e(e, "Could not navigate to the Wi-Fi Details Fragment");
        }
    }

    /**
     * Delegates the pause toggle to the view model and mirrors the new state into the scan
     * status text (so the UI says "Paused" vs "Scanning…").
     */
    private void handleTogglePause()
    {
        if (viewModel == null) return;
        final boolean paused = viewModel.togglePaused();

        final Context context = getContext();
        if (context != null)
        {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null || !wifiManager.isWifiEnabled())
            {
                viewModel.setScanStatus(R.string.wifi_scan_status_disabled);
            } else
            {
                viewModel.setScanStatus(paused ? R.string.scan_status_paused : R.string.scan_status_scanning);
            }
        }

        // Reset the last scan time so we don't falsely trigger the throttling dialog after resuming.
        if (paused) lastScanTime = System.currentTimeMillis();
    }

    /**
     * Shared-model bridge for the Wi-Fi Spectrum screen unchanged logic, moved to a direct
     * consumer of the callback list (no longer reading from a SortedList).
     */
    private void updateSharedModelWifiNetworkList(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        List<WifiRecordWrapper> wifiNetworks = new ArrayList<>(wifiBeaconRecords);
        WifiNetworkInfoList wifiNetworkInfoList = new WifiNetworkInfoList(wifiNetworks);

        SharedViewModel sharedViewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
        sharedViewModel.updateWifiNetworkInfoList(wifiNetworkInfoList);
    }

    private void updateScanStatusFromWifiState()
    {
        final Context context = getContext();
        if (context == null || viewModel == null) return;

        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || !wifiManager.isWifiEnabled())
        {
            viewModel.setScanStatus(R.string.wifi_scan_status_disabled);
        } else
        {
            viewModel.setScanStatus(R.string.scan_status_scanning);
        }
    }

    /**
     * Creates and registers a Wi-Fi receiver that is notified of Wi-Fi state changes (i.e. when Wi-Fi is
     * turned on and off). This is used to update the UI status text, and to kick off the Network Survey Service.
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
                    switch (state)
                    {
                        case WifiManager.WIFI_STATE_DISABLED ->
                        {
                            if (viewModel != null)
                            {
                                viewModel.setScanStatus(R.string.wifi_scan_status_disabled);
                            }
                        }
                        case WifiManager.WIFI_STATE_ENABLED ->
                        {
                            if (viewModel != null)
                            {
                                viewModel.setScanStatus(viewModel.getUiState().getValue().getUpdatesPaused()
                                        ? R.string.scan_status_paused
                                        : R.string.scan_status_scanning);
                            }
                            startAndBindToService();
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        requireActivity().registerReceiver(wifiBroadcastReceiver, filter);
    }

    /**
     * Unregisters the Wi-Fi receiver that is notified of state changes.
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
                    try
                    {
                        final Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                        panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(panelIntent);
                    } catch (ActivityNotFoundException e)
                    {
                        Timber.w(e, "Could not open the Wi-Fi settings panel");
                        Toast.makeText(requireContext(), getString(R.string.settings_not_available), Toast.LENGTH_SHORT).show();
                    }
                } else
                {
                    Toast.makeText(requireContext(), getString(R.string.turn_on_wifi), Toast.LENGTH_SHORT).show();
                    new Handler().postDelayed(() -> {
                        try
                        {
                            final Intent wifiSettingIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                            wifiSettingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(wifiSettingIntent);
                        } catch (Exception e)
                        {
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
     * Android 11+: use {@link WifiManager#isScanThrottleEnabled()} directly.
     */
    private void checkForScanThrottlingAndroid11()
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        Context context = getContext();
        if (context == null) return;

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
     * Pre-Android 11: notice that scans are slower than the requested rate.
     */
    private void checkForScanThrottling()
    {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return;

        Context context = getContext();
        if (context == null) return;

        boolean ignoreWarning = PreferenceUtils.getIgnoreWifiThrottlingWarningPreference(false, context);
        if (ignoreWarning) return;

        if (lastScanTime == 0)
        {
            lastScanTime = System.currentTimeMillis();
            return;
        }

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

    private void showScanThrottlingSnackbar()
    {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) return;

        String snackbarMessage;
        final boolean devOptionsEnabled = areDeveloperOptionsEnabled();

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P)
        {
            snackbarMessage = getString(R.string.android_9_throttling_information);
        } else
        {
            snackbarMessage = getString(R.string.android_10_throttling_information);
            if (!devOptionsEnabled)
            {
                snackbarMessage += "\n\n" + getString(R.string.enable_developer_options);
            }
        }

        // requireView()'s root must be a CoordinatorLayout (or any view inside one). This fragment
        // is hosted via Compose AndroidViewBinding (HomeScreen.kt#WifiFragmentInCompose), so the
        // parent chain doesn't reach android.R.id.content; without a CoordinatorLayout ancestor,
        // Snackbar.findSuitableParent falls back to the surrounding FragmentContainerView and
        // FragmentContainerView.addView throws. See fragment_wifi_networks_list.xml.
        final Snackbar snackbar = Snackbar.make(requireView(), snackbarMessage, Snackbar.LENGTH_INDEFINITE)
                .setAction("Open", v -> {
                    try
                    {
                        startActivity(new Intent(devOptionsEnabled ? Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS : Settings.ACTION_DEVICE_INFO_SETTINGS));
                    } catch (ActivityNotFoundException e)
                    {
                        Timber.w(e, "Could not open the settings screen");
                        Toast.makeText(requireContext(), getString(R.string.settings_not_available), Toast.LENGTH_SHORT).show();
                    }
                })
                .setBackgroundTint(getResources().getColor(R.color.alert_red, null))
                .setTextColor(getResources().getColor(R.color.body_text_1_dark, null));

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
     * Retained for future use if we re-introduce the "connected BSSID" indicator in the new UI.
     */
    @SuppressWarnings({"deprecation", "unused"})
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
