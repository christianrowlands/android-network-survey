package com.craxiom.networksurvey.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
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

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * The fragment that displays a list of Wi-Fi networks returned from the scan.
 *
 * @since 0.1.2
 */
public class WifiNetworksFragment extends Fragment implements IWifiSurveyRecordListener
{
    private static final String LOG_TAG = WifiNetworksFragment.class.getSimpleName();

    private final SortedList<WifiRecordWrapper> wifiRecordSortedList = new SortedList<>(WifiRecordWrapper.class, new WifiRecordSortedListCallback());

    private Context applicationContext;
    private NetworkSurveyService surveyService;
    private RecyclerView recyclerView;
    private MyWifiNetworkRecyclerViewAdapter wifiNetworkRecyclerViewAdapter;
    private TextView scanStatusView;
    private TextView scanNumberView;
    private TextView apsInScanView;

    private volatile boolean updatesPaused = false;

    private int scanNumber = 0;
    private int sortByIndex = 0;
    private long lastScanTime = 0;
    private boolean throttlingNotificationShown = false;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the fragment (e.g. upon screen orientation changes).
     */
    public WifiNetworksFragment()
    {
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        applicationContext = requireActivity().getApplicationContext();
        super.onCreate(savedInstanceState);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        sortByIndex = preferences.getInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_wifi_networks_list, container, false);

        recyclerView = view.findViewById(R.id.wifi_network_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        wifiNetworkRecyclerViewAdapter = new MyWifiNetworkRecyclerViewAdapter(wifiRecordSortedList, getContext());
        recyclerView.setAdapter(wifiNetworkRecyclerViewAdapter);

        final ImageButton pauseButton = view.findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(this::onPauseUiUpdatesToggle);

        final ImageButton sortButton = view.findViewById(R.id.sort_button);
        sortButton.setOnClickListener(v -> showSortByDialog());

        final Context context = requireContext();

        scanStatusView = view.findViewById(R.id.scan_status);
        scanStatusView.setText(context.getString(R.string.wifi_scan_status_scanning));

        scanNumberView = view.findViewById(R.id.scan_number);
        scanNumberView.setText(context.getString(R.string.wifi_scan_number, scanNumber));

        apsInScanView = view.findViewById(R.id.aps_in_scan);
        apsInScanView.setText(context.getString(R.string.wifi_aps_in_scan, 0));

        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        checkWifiEnabled();

        // Update the last scan time so that we don't trigger a false positive alert on the scan time interval.
        lastScanTime = System.currentTimeMillis();

        startAndBindToNetworkSurveyService();
    }

    @Override
    public void onPause()
    {
        if (surveyService != null) surveyService.unregisterWifiSurveyRecordListener(this);

        super.onPause();
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (updatesPaused) return;

        checkForScanThrottling();

        final Context context = requireContext();
        scanNumberView.setText(context.getString(R.string.wifi_scan_number, ++scanNumber));
        apsInScanView.setText(requireContext().getString(R.string.wifi_aps_in_scan, wifiBeaconRecords.size()));

        synchronized (wifiRecordSortedList)
        {
            wifiRecordSortedList.clear();
            wifiRecordSortedList.addAll(wifiBeaconRecords);
            if (wifiNetworkRecyclerViewAdapter != null) wifiNetworkRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Start the Network Survey Service (it won't start if it is already started), and then bind to the service.
     * <p>
     * Starting the service will cause the cellular records to be pulled from the Android system, and then once the
     * MQTT connection is made those cellular records will be sent over the connection to the MQTT Broker.
     */
    private void startAndBindToNetworkSurveyService()
    {
        // Start the service
        Log.i(LOG_TAG, "Binding to the Network Survey Service");
        final Intent serviceIntent = new Intent(applicationContext, NetworkSurveyService.class);
        applicationContext.startService(serviceIntent);

        // Bind to the service
        ServiceConnection surveyServiceConnection = new SurveyServiceConnection();
        final boolean bound = applicationContext.bindService(serviceIntent, surveyServiceConnection, Context.BIND_ABOVE_CLIENT);
        Log.i(LOG_TAG, "NetworkSurveyService bound in the MqttConnectionFragment: " + bound);
    }

    /**
     * Notification that UI updates have either been paused or resumed.  Toggle the paused state and update any UI
     * elements.
     *
     * @param view The ImageButton to update the paused/play icon on.
     */
    private void onPauseUiUpdatesToggle(View view)
    {
        scanStatusView.setText(requireContext().getString(updatesPaused ? R.string.wifi_scan_status_scanning : R.string.wifi_scan_status_paused));
        view.setBackgroundResource(updatesPaused ? R.drawable.ic_pause : R.drawable.ic_play);

        // If we are transitioning to un-pause scan updates, then artificially reset the last scan time so that we don't
        // think that scans are being throttled by the Android OS.
        if (updatesPaused) lastScanTime = System.currentTimeMillis();

        //noinspection NonAtomicOperationOnVolatileField
        updatesPaused = !updatesPaused;
    }

    /**
     * Show the Sort Dialog so the user can pick how they want to sort the list of Wi-Fi networks.
     */
    private void showSortByDialog()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null)
        {
            Log.wtf(LOG_TAG, "The Activity is null so we are unable to show the sorting dialog.");
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_option_sort_by);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        builder.setSingleChoiceItems(R.array.wifi_network_sort_options, sortByIndex,
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
     * @param selectedIndex The newly selected sort by index (from arrays.xml)
     */
    private void onSortByChanged(SharedPreferences preferences, int selectedIndex)
    {
        synchronized (wifiRecordSortedList)
        {
            preferences.edit().putInt(NetworkSurveyConstants.PROPERTY_WIFI_NETWORKS_SORT_ORDER, selectedIndex).apply();
            sortByIndex = selectedIndex;

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

            if (wifiNetworkRecyclerViewAdapter != null) wifiNetworkRecyclerViewAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Checks to see if the Wi-Fi manager is present, and if Wi-Fi is enabled.
     * <p>
     * After the check to see if Wi-Fi is enabled, if Wi-Fi is currently disabled the user is then prompted to turn on
     * Wi-Fi.
     */
    private void checkWifiEnabled()
    {
        final WifiManager wifiManager = (WifiManager) requireContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null && !wifiManager.isWifiEnabled())
        {
            Log.i(LOG_TAG, "Wi-Fi is disabled, prompting the user to enable it");

            if (Build.VERSION.SDK_INT >= 29)
            {
                final Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(panelIntent);
            } else
            {
                // Open the Wi-Fi setting pages after a few seconds
                Toast.makeText(requireContext(), getString(R.string.turn_on_wifi), Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(() -> {
                    final Intent wifiSettingIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    wifiSettingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(wifiSettingIntent);
                }, 3000);
            }
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
     * We have to make sure to handle pausing the UI updates in {@link #onPauseUiUpdatesToggle(View)} so we don't
     * artificially trigger this alert.
     */
    private void checkForScanThrottling()
    {
        // Scan throttling is new as of Android 9
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) return;

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

        if (!devOptionsEnabled || newScanTime - lastScanTime > NetworkSurveyService.WIFI_SCAN_RATE_MS * 3)
        {
            String snackbarMessage;

            // It appears we are not getting scan results as frequently as we are asking for them. It is possible that
            // the Wi-Fi scan rate is being throttled by the Android OS. https://developer.android.com/guide/topics/connectivity/wifi-scan#wifi-scan-throttling
            // Inform the user that they can disable scan throttling in Developer Options
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P)
            {
                snackbarMessage = getString(R.string.android_9_throttling_information);
            } else //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            {
                snackbarMessage = getString(R.string.android_10_throttling_information);
                if (!devOptionsEnabled) snackbarMessage += "\n\n" + getString(R.string.enable_developer_options);
            }

            final Snackbar snackbar = Snackbar.make(requireView(), snackbarMessage, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Open", v -> startActivity(new Intent(devOptionsEnabled ? Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS : Settings.ACTION_DEVICE_INFO_SETTINGS)))
                    .setBackgroundTint(getResources().getColor(R.color.alert_red, null));

            if (snackbar.isShown()) return;

            TextView snackTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            snackTextView.setMaxLines(12);

            snackbar.show();

            throttlingNotificationShown = true;
        }

        lastScanTime = newScanTime;
    }

    /**
     * @return True if the developer options are enabled, false otherwise.
     */
    private boolean areDeveloperOptionsEnabled()
    {
        return Settings.Secure.getInt(requireContext().getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
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
                    return record1.getWifiBeaconRecord().getSsid().compareTo(record2.getWifiBeaconRecord().getSsid());

                case 2: // BSSID
                    return record1.getWifiBeaconRecord().getBssid().compareTo(record2.getWifiBeaconRecord().getBssid());

                case 3: // Channel
                    return Integer.compare(record1.getWifiBeaconRecord().getChannel().getValue(), record2.getWifiBeaconRecord().getChannel().getValue());

                case 4: // Frequency
                    return Integer.compare(record1.getWifiBeaconRecord().getFrequency().getValue(), record2.getWifiBeaconRecord().getFrequency().getValue());

                case 5: // Security Type
                    return WifiBeaconMessageConstants.getEncryptionTypeString(record1.getWifiBeaconRecord().getEncryptionType())
                            .compareTo(WifiBeaconMessageConstants.getEncryptionTypeString(record2.getWifiBeaconRecord().getEncryptionType()));

                default: // Signal Strength
                    // Signal Strength is index 0 in the array, but we also use it as the default case
                    // Invert the sort so that the strongest records are at the top (descending)
                    return -1 * Float.compare(record1.getWifiBeaconRecord().getSignalStrength().getValue(), record2.getWifiBeaconRecord().getSignalStrength().getValue());
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

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link NetworkSurveyService}.
     * <p>
     * We need to bind to the {@link NetworkSurveyService} so that we can get notified about the Wi-Fi scan results.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder)
        {
            Log.i(LOG_TAG, name + " service connected");
            surveyService = ((NetworkSurveyService.SurveyServiceBinder) binder).getService();
            surveyService.registerWifiSurveyRecordListener(WifiNetworksFragment.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Log.i(LOG_TAG, name + " service disconnected");
            surveyService = null;
        }
    }
}
