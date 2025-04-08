package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.constants.CdrPermissions.CDR_OPTIONAL_PERMISSIONS;
import static com.craxiom.networksurvey.constants.CdrPermissions.CDR_REQUIRED_PERMISSIONS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.craxiom.mqttlibrary.IConnectionStateListener;
import com.craxiom.mqttlibrary.MqttConstants;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.databinding.FragmentDashboardBinding;
import com.craxiom.networksurvey.databinding.MqttStreamItemBinding;
import com.craxiom.networksurvey.fragments.model.DashboardViewModel;
import com.craxiom.networksurvey.listeners.ILoggingChangeListener;
import com.craxiom.networksurvey.logging.db.SurveyDatabase;
import com.craxiom.networksurvey.logging.db.dao.SurveyRecordDao;
import com.craxiom.networksurvey.logging.db.uploader.NsUploaderWorker;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.MdmUtils;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.craxiom.networksurvey.util.ToggleLoggingTask;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Strings;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import timber.log.Timber;

/**
 * This fragment displays a dashboard to the user with various status information
 *
 * @since 1.10.0
 */
public class DashboardFragment extends AServiceDataFragment implements LocationListener, IConnectionStateListener,
        ILoggingChangeListener, SharedPreferences.OnSharedPreferenceChangeListener
{
    public static final int ACCESS_REQUIRED_PERMISSION_REQUEST_ID = 20;
    public static final int ACCESS_OPTIONAL_PERMISSION_REQUEST_ID = 21;
    private static final int ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID = 22;

    private final DecimalFormat locationFormat = new DecimalFormat("###.#####");
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private boolean scrolledToBottom;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateUploadCountsRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            queryUploadQueueCount();

            // Re-run every 6 seconds as long as the UI is visible
            handler.postDelayed(this, 6_000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        binding = FragmentDashboardBinding.inflate(inflater);

        viewModel = new ViewModelProvider(requireActivity()).get(DashboardViewModel.class);

        initializeLocationTextView();
        initializeUiListeners();
        initializeObservers();
        initializeUploadUiState();
        queryUploadQueueCount();

        binding.dashboardScrollView.getViewTreeObserver().addOnPreDrawListener(() -> {
            scrolledToBottom = isScrolledToBottom();
            return true;
        });

        binding.dashboardScrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (scrolledToBottom)
            {
                binding.dashboardScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // In the edge case event where the user has just granted the location permission but has not restarted the app,
        // we need to update the UI to show the new location in this onResume method. There might be better approaches
        // instead of recalling the initialize view method each time the fragment is resumed.
        initializeLocationTextView();

        observeUploadWork();

        startAndBindToService();

        handler.post(updateUploadCountsRunnable);
    }

    @Override
    public void onPause()
    {
        handler.removeCallbacks(updateUploadCountsRunnable);
        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        removeObservers();

        super.onDestroyView();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        service.registerLocationListener(this);
        service.registerMqttConnectionStateListener(this);
        service.registerLoggingChangeListener(this);

        // Refresh the location views because we might have missed something between the
        // initial call and when we registered as a listener, but only if the location is not null
        // because the initializeLocationTextView method might have set the UI to indicate that the
        // location provider is disabled or that the location permission is missing and we don't
        // want to override that.
        Location latestLocation = service.getPrimaryLocationListener().getLatestLocation();
        if (latestLocation != null) updateLocationTextView(latestLocation);

        Context context = getContext();
        if (context != null)
        {
            PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);
        }

        updateMqttUiState(service.getMqttConnectionState());
        readMqttStreamEnabledProperties();
        updateLoggingState(service);
        viewModel.setUploadScanningActive(service.isUploadScanningActive());
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        Context context = getContext();
        if (context != null)
        {
            PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
        }

        service.unregisterLocationListener(this);
        service.unregisterLoggingChangeListener(this);
        service.unregisterMqttConnectionStateListener(this);

        super.onSurveyServiceDisconnecting(service);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider)
    {
        if (LocationManager.GPS_PROVIDER.equals(provider)) viewModel.setProviderEnabled(true);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider)
    {
        if (LocationManager.GPS_PROVIDER.equals(provider)) viewModel.setProviderEnabled(false);
    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        viewModel.setLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        // No-op, old method that is no longer called on Android Q and above.
    }

    @Override
    public void onConnectionStateChange(ConnectionState connectionState)
    {
        viewModel.setMqttConnectionState(connectionState);
    }

    @Override
    public void onLoggingChanged()
    {
        if (service != null) updateLoggingState(service);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if (key == null) return;
        switch (key)
        {
            case NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED:
            case NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED:
            case NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED:
            case NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED:
            case NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED:
                readMqttStreamEnabledProperties();
                break;
            default:
        }
    }

    /**
     * Add click listeners to the appropriate places in the UI, such as on the file logging toggle
     * switches.
     */
    private void initializeUiListeners()
    {
        binding.startScanningButton.setOnClickListener(v -> startSavingRecordsForUpload());
        binding.stopScanningButton.setOnClickListener(v -> stopSavingRecordsForUpload());
        binding.uploadSettingsButton.setOnClickListener(v -> navigateToUploadSettings());
        binding.uploadButton.setOnClickListener(v -> showUploadDialog());
        binding.uploadCancelButton.setOnClickListener(v -> cancelUploads());

        initializeLoggingSwitch(binding.cellularLoggingToggleSwitch, (newEnabledState, toggleSwitch) -> {
            viewModel.setCellularLoggingEnabled(newEnabledState);
            toggleCellularLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.wifiLoggingToggleSwitch, (newEnabledState, toggleSwitch) -> {
            viewModel.setWifiLoggingEnabled(newEnabledState);
            toggleWifiLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.bluetoothLoggingToggleSwitch, (newEnabledState, toggleSwitch) -> {
            if (newEnabledState && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && missingAnyPermissions(NetworkSurveyActivity.BLUETOOTH_PERMISSIONS)))
            {
                toggleSwitch.setChecked(false);
                showBluetoothPermissionRationaleAndRequestPermissions();
                return;
            }

            viewModel.setBluetoothLoggingEnabled(newEnabledState);
            toggleBluetoothLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.gnssLoggingToggleSwitch, (newEnabledState, toggleSwitch) -> {
            viewModel.setGnssLoggingEnabled(newEnabledState);
            toggleGnssLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.cdrLoggingToggleSwitch, (newEnabledState, toggleSwitch) -> {
            if (newEnabledState && (missingAnyPermissions(CDR_REQUIRED_PERMISSIONS) || missingAnyPermissions(CDR_OPTIONAL_PERMISSIONS)))
            {
                toggleSwitch.setChecked(false);
                showCdrPermissionRationaleAndRequestPermissions();
                return;
            }

            viewModel.setCdrLoggingEnabled(newEnabledState);
            toggleCdrLogging(newEnabledState);
        });

        final Context context = getContext();
        if (context != null)
        {
            boolean underMdmControl = MdmUtils.isUnderMdmControlAndEnabled(context, MqttConstants.PROPERTY_MQTT_CONNECTION_HOST);
            binding.mqttConnectionToggleSwitch.setVisibility(underMdmControl ? View.INVISIBLE : View.VISIBLE);
        }

        initializeLoggingSwitch(binding.mqttConnectionToggleSwitch, (newEnabledState, toggleSwitch) -> {
            if (service == null)
            {
                Timber.w("The service is null when trying to make an MQTT connection from the Dashboard.");
                Toast.makeText(getContext(), "The App is not ready to make an MQTT connection, try again later", Toast.LENGTH_LONG).show();
                return;
            }
            if (newEnabledState)
            {
                boolean attempting = service.connectToMqttBrokerUsingSavedConnectionInfo();
                if (!attempting)
                {
                    toggleSwitch.setChecked(false);
                    final Snackbar snackbar = Snackbar.make(requireView(), "Could not try to connect to the MQTT broker because the connection information is not set", Snackbar.LENGTH_LONG)
                            .setAction("Open", v -> navigateToMqttFragment())
                            .setBackgroundTint(getResources().getColor(R.color.rssi_orange, null));

                    if (snackbar.isShown()) return;

                    TextView snackTextView = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                    snackTextView.setMaxLines(12);

                    snackbar.show();
                }
            } else
            {
                service.disconnectFromMqttBroker();
                updateMqttUiState(ConnectionState.DISCONNECTED);
            }
        });

        binding.mqttFragmentButton.setOnClickListener(c -> navigateToMqttFragment());

        binding.uploadHelpIcon.setOnClickListener(c -> showUploadHelpDialog());
        binding.cdrHelpIcon.setOnClickListener(c -> showCdrHelpDialog());
        binding.fileHelpIcon.setOnClickListener(c -> showFileMqttHelpDialog());
        binding.mqttHelpIcon.setOnClickListener(c -> showFileMqttHelpDialog());
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
            alertBuilder.setTitle(context.getString(R.string.bluetooth_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.bluetooth_permissions_rationale));
            alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> requestBluetoothPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
        }
    }

    /**
     * Check to see if we should show the rationale for any of the CDR permissions. If so, then display a dialog that
     * explains what permissions we need for this app to work properly.
     * <p>
     * If we should not show the rationale, then just request the permissions.
     */
    private void showCdrPermissionRationaleAndRequestPermissions()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;

        final Context context = getContext();
        if (context == null) return;

        if (missingAnyPermissions(CDR_REQUIRED_PERMISSIONS))
        {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(context.getString(R.string.cdr_required_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.cdr_required_permissions_rationale));
            alertBuilder.setPositiveButton(R.string.request, (dialog, which) -> requestRequiredCdrPermissions());

            AlertDialog permissionsExplanationDialog = alertBuilder.create();
            permissionsExplanationDialog.show();
            return;
        }

        if (missingAnyPermissions(CDR_OPTIONAL_PERMISSIONS))
        {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle(context.getString(R.string.cdr_optional_permissions_rationale_title));
            alertBuilder.setMessage(getText(R.string.cdr_optional_permissions_rationale));
            alertBuilder.setPositiveButton(R.string.request, (dialog, which) -> requestOptionalCdrPermissions());
            alertBuilder.setNegativeButton(R.string.ignore, (dialog, which) -> {
                viewModel.setCdrLoggingEnabled(true);
                toggleCdrLogging(true);
            });

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
        if (missingAnyPermissions(NetworkSurveyActivity.BLUETOOTH_PERMISSIONS))
        {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            ActivityCompat.requestPermissions(activity, NetworkSurveyActivity.BLUETOOTH_PERMISSIONS, ACCESS_BLUETOOTH_PERMISSION_REQUEST_ID);
        }
    }

    /**
     * Request the permissions needed for this app if any of them have not yet been granted.  If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestRequiredCdrPermissions()
    {
        if (missingAnyPermissions(CDR_REQUIRED_PERMISSIONS))
        {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            ActivityCompat.requestPermissions(activity, CDR_REQUIRED_PERMISSIONS, ACCESS_REQUIRED_PERMISSION_REQUEST_ID);
        }
    }

    /**
     * Request the optional permissions for this app if any of them have not yet been granted. If all of the permissions
     * are already granted then don't request anything.
     */
    private void requestOptionalCdrPermissions()
    {
        if (missingAnyPermissions(CDR_OPTIONAL_PERMISSIONS))
        {
            FragmentActivity activity = getActivity();
            if (activity == null) return;
            ActivityCompat.requestPermissions(activity, CDR_OPTIONAL_PERMISSIONS, ACCESS_OPTIONAL_PERMISSION_REQUEST_ID);
        }
    }

    /**
     * Displays a dialog with some information about uploading records.
     */
    private void showUploadHelpDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_upload_help, null);
        TextView helpTextView = dialogView.findViewById(R.id.tvUploadHelpText);
        helpTextView.setText(Html.fromHtml(context.getString(R.string.upload_help), Html.FROM_HTML_MODE_LEGACY));
        helpTextView.setMovementMethod(LinkMovementMethod.getInstance()); // Enable link clicking

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setView(dialogView);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(context.getString(R.string.upload_help_title));
        alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
        });
        alertBuilder.setNeutralButton(R.string.view_manual, (dialog, which) -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://networksurvey.app/manual#data-upload"));
            context.startActivity(browserIntent);
        });
        alertBuilder.create().show();
    }

    /**
     * Displays a dialog with some information about what a CDR is to the user.
     */
    private void showCdrHelpDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_cdr_help, null);

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setView(dialogView);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(context.getString(R.string.cdr_help_title));
        alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
        });
        alertBuilder.create().show();
    }

    /**
     * Displays a dialog with some information about the difference between file logging and MQTT.
     */
    private void showFileMqttHelpDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
        alertBuilder.setCancelable(true);
        alertBuilder.setTitle(context.getString(R.string.file_help_title));
        alertBuilder.setMessage(getText(R.string.file_help));
        alertBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
        });
        alertBuilder.create().show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initializeLoggingSwitch(SwitchCompat loggingSwitch, BiConsumer<Boolean, SwitchCompat> switchAction)
    {
        loggingSwitch.setOnClickListener((buttonView) -> {
            if (buttonView.isPressed())
            {
                SwitchCompat switchCompat = (SwitchCompat) buttonView;
                boolean newEnabledState = switchCompat.isChecked();
                switchAction.accept(newEnabledState, switchCompat);
            }
        });
        loggingSwitch.setOnTouchListener((buttonView, motionEvent) -> motionEvent.getActionMasked() == 2);
    }

    /**
     * Initialize the model view observers. These observers look for changes to the model view
     * values, and then update the UI based on any changes.
     */
    private void initializeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        viewModel.getCellularUploadQueueCount().observe(viewLifecycleOwner, this::updateCellularUploadQueueCountUI);
        viewModel.getWifiUploadQueueCount().observe(viewLifecycleOwner, this::updateWifiUploadQueueCountUI);

        viewModel.getProviderEnabled().observe(viewLifecycleOwner, this::updateLocationProviderStatus);
        viewModel.getLocation().observe(viewLifecycleOwner, this::updateLocationTextView);

        viewModel.getUploadScanningActive().observe(viewLifecycleOwner, this::updateUploadRecordSavingUi);

        viewModel.getCellularLoggingEnabled().observe(viewLifecycleOwner, this::updateCellularLogging);
        viewModel.getWifiLoggingEnabled().observe(viewLifecycleOwner, this::updateWifiLogging);
        viewModel.getBluetoothLoggingEnabled().observe(viewLifecycleOwner, this::updateBluetoothLogging);
        viewModel.getGnssLoggingEnabled().observe(viewLifecycleOwner, this::updateGnssLogging);
        viewModel.getCdrLoggingEnabled().observe(viewLifecycleOwner, this::updateCdrLogging);

        viewModel.getMqttConnectionState().observe(viewLifecycleOwner, this::updateMqttUiState);
        viewModel.getCellularMqttStreamEnabled().observe(viewLifecycleOwner, enabled -> updateStreamUi(binding.mqttCellular, enabled));
        viewModel.getWifiMqttStreamEnabled().observe(viewLifecycleOwner, enabled -> updateStreamUi(binding.mqttWifi, enabled));
        viewModel.getBluetoothMqttStreamEnabled().observe(viewLifecycleOwner, enabled -> updateStreamUi(binding.mqttBluetooth, enabled));
        viewModel.getGnssMqttStreamEnabled().observe(viewLifecycleOwner, enabled -> updateStreamUi(binding.mqttGnss, enabled));
        viewModel.getDeviceStatusMqttStreamEnabled().observe(viewLifecycleOwner, enabled -> updateStreamUi(binding.mqttDeviceStatus, enabled));
    }

    /**
     * Cleans up by removing all the view model observers.
     */
    private void removeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        viewModel.getProviderEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getLocation().removeObservers(viewLifecycleOwner);

        viewModel.getUploadScanningActive().removeObservers(viewLifecycleOwner);

        viewModel.getCellularLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getWifiLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getBluetoothLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getGnssLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getCdrLoggingEnabled().removeObservers(viewLifecycleOwner);

        viewModel.getMqttConnectionState().removeObservers(viewLifecycleOwner);
        viewModel.getCellularMqttStreamEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getWifiMqttStreamEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getBluetoothMqttStreamEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getGnssMqttStreamEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getDeviceStatusMqttStreamEnabled().removeObservers(viewLifecycleOwner);
    }

    private void initializeUploadUiState()
    {
        final Context context = getContext();
        if (context == null) return;

        if (MdmUtils.isExternalDataUploadAllowed(context))
        {
            binding.dbLoggingCardView.setVisibility(View.VISIBLE);
        } else
        {
            binding.dbLoggingCardView.setVisibility(View.GONE);
        }
    }

    private synchronized void updateLoggingState(NetworkSurveyService networkSurveyService)
    {
        viewModel.setCellularLoggingEnabled(networkSurveyService.isCellularLoggingEnabled());
        viewModel.setWifiLoggingEnabled(networkSurveyService.isWifiLoggingEnabled());
        viewModel.setBluetoothLoggingEnabled(networkSurveyService.isBluetoothLoggingEnabled());
        viewModel.setGnssLoggingEnabled(networkSurveyService.isGnssLoggingEnabled());
        viewModel.setCdrLoggingEnabled(networkSurveyService.isCdrLoggingEnabled());
    }

    /**
     * Initialize the location text view based on the phone's state.
     */
    private void initializeLocationTextView()
    {
        final TextView tvLocation = binding.locationCard.location;

        final String displayText;
        final int textColor;

        if (!hasLocationPermission())
        {
            tvLocation.setText(getString(R.string.missing_location_permission));
            tvLocation.setTextColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
            return;
        }

        final Location location = viewModel.getLocation().getValue();
        if (location != null)
        {
            updateLocationTextView(location);
            return;
        }

        final LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
        {
            Timber.wtf("Could not get the location manager.");
            displayText = getString(R.string.no_gps_device);
            textColor = R.color.connectionStatusDisconnected;
        } else
        {
            final LocationProvider locationProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
            if (locationProvider == null)
            {
                displayText = getString(R.string.no_gps_device);
            } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            {
                // gps exists, but isn't on
                displayText = getString(R.string.turn_on_gps);
            } else
            {
                displayText = getString(R.string.searching_for_location);
            }

            textColor = R.color.connectionStatusConnecting;
        }

        tvLocation.setText(displayText);
        tvLocation.setTextColor(getResources().getColor(textColor, null));
    }

    /**
     * Updates the location text view with the latest latitude and longitude, or if the latest location is below the
     * accuracy threshold then the text view is updated to notify the user of such.
     *
     * @param latestLocation The latest location if available, or null if the accuracy is not good enough.
     */
    private void updateLocationTextView(Location latestLocation)
    {
        final TextView locationTextView = binding.locationCard.location;
        final TextView altitudeTextView = binding.locationCard.altitude;
        final TextView accuracyTextView = binding.locationCard.accuracy;
        if (latestLocation != null)
        {
            final String latLonString = locationFormat.format(latestLocation.getLatitude()) + ", " +
                    locationFormat.format(latestLocation.getLongitude());
            locationTextView.setText(latLonString);
            locationTextView.setTextColor(getResources().getColor(R.color.normalText, null));

            altitudeTextView.setText(getString(R.string.altitude_value, Long.toString(Math.round(latestLocation.getAltitude()))));

            accuracyTextView.setText(getString(R.string.accuracy_value, Integer.toString(MathUtils.roundAccuracy(latestLocation.getAccuracy()))));
        } else
        {
            locationTextView.setText(R.string.low_gps_confidence);
            locationTextView.setTextColor(Color.YELLOW);

            altitudeTextView.setText(getString(R.string.altitude_initial));

            accuracyTextView.setText(getString(R.string.accuracy_initial));
        }
    }

    /**
     * Updates the UI based on the different states of the server connection.
     *
     * @param connectionState The new state of the server connection to update the UI for.
     */
    private void updateMqttUiState(ConnectionState connectionState)
    {
        Timber.d("Updating the UI state for: %s", connectionState);

        try
        {
            switch (connectionState)
            {
                case DISCONNECTED:
                case DISCONNECTING:
                    binding.mqttStatusIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.connectionStatusDisconnected, null)));
                    binding.mqttStatusText.setText(R.string.mqtt_off);
                    binding.mqttStreamingGroup.setVisibility(View.GONE);
                    binding.mqttConnectionToggleSwitch.setChecked(false);
                    break;

                case CONNECTING:
                    binding.mqttStatusIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.connectionStatusConnecting, null)));
                    binding.mqttStatusText.setText(R.string.mqtt_connecting);
                    binding.mqttStreamingGroup.setVisibility(View.VISIBLE);
                    binding.mqttConnectionToggleSwitch.setChecked(true);
                    break;

                case CONNECTED:
                    binding.mqttStatusIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.connectionStatusConnected, null)));
                    binding.mqttStatusText.setText(R.string.mqtt_connected);
                    binding.mqttStreamingGroup.setVisibility(View.VISIBLE);
                    binding.mqttConnectionToggleSwitch.setChecked(true);
                    break;
            }
        } catch (Exception e)
        {
            // An IllegalStateException can occur if the fragment has been moved away from.
            Timber.w(e, "Caught an exception when trying to update the MQTT Connection Status in the Dashboard UI");
        }
    }

    /**
     * Reads the MQTT streaming settings that indicate which protocol streaming is enabled, and then
     * updates the view model with that information.
     */
    private void readMqttStreamEnabledProperties()
    {
        final Context context = getContext();
        if (context == null)
        {
            Timber.w("Could not get the context to read the MQTT streaming preferences, " +
                    "maybe the dashboard fragment has been removed");
            return;
        }
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean cellularStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        viewModel.setCellularMqttStreamEnabled(cellularStreamEnabled);

        boolean wifiStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        viewModel.setWifiMqttStreamEnabled(wifiStreamEnabled);

        boolean bluetoothStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        viewModel.setBluetoothMqttStreamEnabled(bluetoothStreamEnabled);

        boolean gnssStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        viewModel.setGnssMqttStreamEnabled(gnssStreamEnabled);

        boolean deviceStatusStreamEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
        viewModel.setDeviceStatusMqttStreamEnabled(deviceStatusStreamEnabled);
    }

    /**
     * Starts or stops writing the Cellular log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleCellularLogging(boolean enable)
    {
        Context context = getContext();
        if (context == null) return;

        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleCellularLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return context.getString(R.string.cellular_logging_toggle_failed);
            updateCellularLogging(enabled);
            return context.getString(enabled ? R.string.cellular_logging_start_toast : R.string.cellular_logging_stop_toast);
        }, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the Wi-Fi log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleWifiLogging(boolean enable)
    {
        Context context = getContext();
        if (context == null) return;

        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleWifiLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return context.getString(R.string.wifi_logging_toggle_failed);
            updateWifiLogging(enabled);
            return context.getString(enabled ? R.string.wifi_logging_start_toast : R.string.wifi_logging_stop_toast);
        }, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the Bluetooth log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleBluetoothLogging(boolean enable)
    {
        Context context = getContext();
        if (context == null) return;

        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleBluetoothLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return context.getString(R.string.bluetooth_logging_toggle_failed);
            updateBluetoothLogging(enabled);
            return context.getString(enabled ? R.string.bluetooth_logging_start_toast : R.string.bluetooth_logging_stop_toast);
        }, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the GNSS log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleGnssLogging(boolean enable)
    {
        Context context = getContext();
        if (context == null) return;

        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleGnssLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return context.getString(R.string.gnss_logging_toggle_failed);
            updateGnssLogging(enabled);
            return context.getString(enabled ? R.string.gnss_logging_start_toast : R.string.gnss_logging_stop_toast);
        }, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the CDR log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleCdrLogging(boolean enable)
    {
        Context context = getContext();
        if (context == null) return;

        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleCdrLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return context.getString(R.string.cdr_logging_toggle_failed);
            updateCdrLogging(enabled);
            return context.getString(enabled ? R.string.cdr_logging_start_toast : R.string.cdr_logging_stop_toast);
        }, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Updates the cellular logging UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if logging is enabled.
     */
    private void updateCellularLogging(boolean enabled)
    {
        binding.cellularLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.status_disabled);
        binding.cellularLoggingToggleSwitch.setChecked(enabled);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        binding.cellularIcon.setImageTintList(colorStateList);
    }

    /**
     * Updates the Wi-Fi logging UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if logging is enabled.
     */
    private void updateWifiLogging(boolean enabled)
    {
        binding.wifiLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.status_disabled);
        binding.wifiLoggingToggleSwitch.setChecked(enabled);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        binding.wifiIcon.setImageTintList(colorStateList);
    }

    /**
     * Updates the bluetooth logging UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if logging is enabled.
     */
    private void updateBluetoothLogging(boolean enabled)
    {
        binding.bluetoothLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.status_disabled);
        binding.bluetoothLoggingToggleSwitch.setChecked(enabled);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        binding.bluetoothIcon.setImageTintList(colorStateList);
    }

    /**
     * Updates the gnss logging UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if logging is enabled.
     */
    private void updateGnssLogging(boolean enabled)
    {
        binding.gnssLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.status_disabled);
        binding.gnssLoggingToggleSwitch.setChecked(enabled);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        binding.gnssIcon.setImageTintList(colorStateList);
    }

    /**
     * Updates the CDR logging UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if logging is enabled.
     */
    private void updateCdrLogging(boolean enabled)
    {
        binding.cdrLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.status_disabled);
        binding.cdrLoggingToggleSwitch.setChecked(enabled);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        binding.cdrIcon.setImageTintList(colorStateList);
    }

    /**
     * Updates a specific stream item (e.g. Cellular) to the specified status.
     *
     * @param streamItem The item to update.
     * @param enabled    True if streaming is enabled for the specified item, false otherwise.
     */
    private void updateStreamUi(MqttStreamItemBinding streamItem, boolean enabled)
    {
        streamItem.value.setText(enabled ? R.string.status_on : R.string.status_disabled);
        if (enabled)
        {
            streamItem.mqttStatusIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorAccent, null)));
        } else
        {
            streamItem.mqttStatusIcon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.inactiveTabColor, null)));
        }
    }

    /**
     * Updates the location UI based on the provided location provider status. If this method is called, it always
     * results in the clearing of the lat/lon from the UI. Therefore, it should only be called when the location
     * provider is enabled or disabled.
     *
     * @param enabled The new status of the location provider; true for enabled, false for disabled.
     */
    private void updateLocationProviderStatus(boolean enabled)
    {
        final TextView locationTextView = binding.locationCard.location;

        locationTextView.setTextColor(getResources().getColor(R.color.connectionStatusConnecting, null));
        locationTextView.setText(enabled ? R.string.searching_for_location : R.string.turn_on_gps);
    }

    private void queryUploadQueueCount()
    {
        executorService.execute(() -> {
            SurveyRecordDao surveyRecordDao = SurveyDatabase.getInstance(getContext()).surveyRecordDao();
            int totalCellularRecordsForUpload = NsUploaderWorker.getTotalCellularRecordsForUpload(surveyRecordDao);
            viewModel.setCellularUploadQueueCount(totalCellularRecordsForUpload);

            int wifiRecordCountForUpload = surveyRecordDao.getWifiRecordCountForUpload();
            viewModel.setWifiUploadQueueCount(wifiRecordCountForUpload);
        });
    }

    private void updateCellularUploadQueueCountUI(int count)
    {
        binding.cellularUploadQueueCount.setText(getString(R.string.cellular_upload_queue_count, count));
    }

    private void updateWifiUploadQueueCountUI(int count)
    {
        binding.wifiUploadQueueCount.setText(getString(R.string.wifi_upload_queue_count, count));
    }

    private void navigateToMqttFragment()
    {
        try
        {
            FragmentActivity activity = getActivity();
            if (activity == null) return;

            SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
            viewModel.triggerNavigationToMqttConnection();
        } catch (Exception e)
        {
            // It is possible that the user has tried to connect, the snackbar message is displayed,
            // and then they navigated away from the dashboard fragment and then clicked on the
            // snackbar "Open" button. In this case we will get an IllegalStateException.
            Timber.e(e, "Could not navigate to the MQTT Connection fragment");
        }
    }

    private void navigateToUploadSettings()
    {
        try
        {
            FragmentActivity activity = getActivity();
            if (activity == null) return;

            SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
            viewModel.triggerNavigationToUploadSettings();
        } catch (Exception e)
        {
            Timber.e(e, "Could not navigate to the Upload Preferences fragment");
        }
    }

    /**
     * Updates the UI and starts the cellular and wifi scanning, and writing those results to the
     * internal DB so that the records can be saved for upload.
     */
    private void startSavingRecordsForUpload()
    {
        Context context = getContext();
        if (context == null) return;

        binding.startScanningButton.setEnabled(false);
        toggleUploadRecordSaving(true);
        resetUploadUi();
    }

    /**
     * Updates the UI and stops the writing the cellular and wifi records to the internal DB, and
     * stops the cellular and wifi scanning if it is no longer being used by anything else.
     */
    private void stopSavingRecordsForUpload()
    {
        Context context = getContext();
        if (context == null) return;

        binding.stopScanningButton.setEnabled(false);
        toggleUploadRecordSaving(false);
    }

    private void toggleUploadRecordSaving(boolean enable)
    {
        Context context = getContext();
        if (context == null) return;

        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleUploadRecordSaving(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null)
            {
                viewModel.setUploadScanningActive(false);
                return context.getString(R.string.upload_saving_toggle_failed);
            }
            viewModel.setUploadScanningActive(enabled);
            return context.getString(enabled ? R.string.upload_saving_start_toast : R.string.upload_saving_stop_toast);
        }, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Updates the upload record scanning and saving UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if it is enabled.
     */
    private void updateUploadRecordSavingUi(boolean enabled)
    {
        Context context = getContext();
        if (context == null) return;

        if (enabled)
        {
            binding.uploadScanningStatus.setText(R.string.scanning_active);
            binding.uploadScanningStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));
            binding.uploadScanningSpinner.setVisibility(View.VISIBLE);
            binding.startScanningButton.setVisibility(View.GONE);
            binding.stopScanningButton.setVisibility(View.VISIBLE);
            binding.stopScanningButton.setEnabled(true);
        } else
        {
            binding.uploadScanningStatus.setText(R.string.scanning_inactive);
            binding.uploadScanningStatus.setTextColor(ContextCompat.getColor(context, R.color.normalText));
            binding.uploadScanningSpinner.setVisibility(View.GONE);
            binding.startScanningButton.setVisibility(View.VISIBLE);
            binding.startScanningButton.setEnabled(true);
            binding.stopScanningButton.setVisibility(View.GONE);
        }
    }

    /**
     * Display the upload dialog to the user, and then handle the upload based on the user's choices.
     */
    private void showUploadDialog()
    {
        final Context context = getContext();
        if (context == null) return;

        resetUploadUi();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean prefUploadToOpenCellId = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID);
        boolean prefAnonymously = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_OPENCELLID);
        boolean prefUploadToBeaconDb = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, NetworkSurveyConstants.DEFAULT_UPLOAD_TO_BEACONDB);
        boolean prefRetryUpload = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, NetworkSurveyConstants.DEFAULT_UPLOAD_RETRY_ENABLED);
        boolean showDialogEveryTime = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, true);

        if (!showDialogEveryTime)
        {
            startUploadWorker(prefUploadToOpenCellId, prefAnonymously, prefUploadToBeaconDb, prefRetryUpload);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_upload, null);
        builder.setView(dialogView);

        CheckBox ocidUploadCheckbox = dialogView.findViewById(R.id.checkOpenCellId);
        CheckBox anonymousOcidUploadCheckbox = dialogView.findViewById(R.id.checkAnonymously);
        TextView accessTokenWarningMessage = dialogView.findViewById(R.id.accessTokenWarningMessage);
        CheckBox beaconDbUploadCheckbox = dialogView.findViewById(R.id.checkBeaconDB);
        CheckBox retryUploadCheckbox = dialogView.findViewById(R.id.checkRetry);
        CheckBox dontShowAgainCheckbox = dialogView.findViewById(R.id.checkDontShowAgain);

        anonymousOcidUploadCheckbox.setEnabled(prefUploadToOpenCellId);
        ocidUploadCheckbox.setOnCheckedChangeListener((buttonView, uploadToOcid) -> {
            anonymousOcidUploadCheckbox.setEnabled(uploadToOcid);
            updateOcidKeyWarningMessage(uploadToOcid, anonymousOcidUploadCheckbox.isChecked(), accessTokenWarningMessage, context);
        });

        anonymousOcidUploadCheckbox.setOnCheckedChangeListener((buttonView, anonymousOcidUpload) ->
                updateOcidKeyWarningMessage(ocidUploadCheckbox.isChecked(), anonymousOcidUpload, accessTokenWarningMessage, context));

        ocidUploadCheckbox.setChecked(prefUploadToOpenCellId);
        anonymousOcidUploadCheckbox.setChecked(prefAnonymously);
        beaconDbUploadCheckbox.setChecked(prefUploadToBeaconDb);
        retryUploadCheckbox.setChecked(prefRetryUpload);

        builder.setTitle(context.getString(R.string.upload_survey_records))
                .setPositiveButton("Upload", (dialog, which) -> {
                    boolean uploadToOpenCellId = ocidUploadCheckbox.isChecked();
                    boolean anonymously = anonymousOcidUploadCheckbox.isChecked();
                    boolean uploadToBeaconDB = beaconDbUploadCheckbox.isChecked();
                    boolean enableRetry = retryUploadCheckbox.isChecked();
                    boolean dontShowAgain = dontShowAgainCheckbox.isChecked();

                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                    final SharedPreferences.Editor edit = sharedPreferences.edit();
                    edit.putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_CONFIG_UPLOAD_DIALOG, !dontShowAgain);
                    edit.apply();

                    startUploadWorker(uploadToOpenCellId, anonymously, uploadToBeaconDB, enableRetry);
                })
                .setNeutralButton(R.string.preferences, (dialog, which) -> navigateToUploadSettings())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void cancelUploads()
    {
        WorkManager workManager = WorkManager.getInstance(requireContext());
        workManager.cancelAllWorkByTag(NsUploaderWorker.WORKER_TAG);
    }

    private void updateOcidKeyWarningMessage(boolean uploadToOcid, boolean anonymousOcidUpload, TextView accessTokenWarningMessage, Context context)
    {
        if (uploadToOcid && !anonymousOcidUpload)
        {
            String userOcidApiKey = PreferenceUtils.getUserOcidApiKey(context);
            if (!PreferenceUtils.isApiKeyValid(userOcidApiKey))
            {
                accessTokenWarningMessage.setVisibility(View.VISIBLE);
            }
        } else
        {
            accessTokenWarningMessage.setVisibility(View.GONE);
        }
    }

    /**
     * Observes all upload tasks associated with the specific work tag.
     * Updates UI when an upload is active, ongoing, or finishes.
     * Logs an error if multiple upload tasks are found (should only be one).
     */
    private void observeUploadWork()
    {
        Context context = getContext();
        if (context == null) return;

        WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(NsUploaderWorker.WORKER_TAG)
                .observe(getViewLifecycleOwner(), workInfos -> {
                    Timber.d("observeUploadWork(): Found %s work tasks with the tag: %s", workInfos.size(), NsUploaderWorker.WORKER_TAG);

                    if (workInfos.size() > 1)
                    {
                        Timber.e("observeUploadWork(): Multiple active upload tasks found! Expected only one.");
                    }

                    boolean hasActiveUpload = false;
                    UUID activeWorkId = null;

                    for (WorkInfo workInfo : workInfos)
                    {
                        if (workInfo.getState() == WorkInfo.State.ENQUEUED || workInfo.getState() == WorkInfo.State.RUNNING)
                        {
                            hasActiveUpload = true;
                            activeWorkId = workInfo.getId();
                            break; // Stop iterating after finding one active upload
                        }
                    }

                    if (hasActiveUpload)
                    {
                        showUploadProgress(activeWorkId); // Update UI for the active task
                    } else
                    {
                        binding.uploadButton.setEnabled(true);
                        binding.uploadProgressGroup.setVisibility(View.GONE);
                    }
                });
    }

    /**
     * Trigger the upload worker to upload the data to the specified services.
     */
    private void startUploadWorker(boolean uploadToOpenCellId, boolean anonymouslyToOpencelliD, boolean uploadToBeaconDB, boolean retry)
    {
        final Context context = getContext();
        if (context == null) return;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOpenCellId);
        edit.putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymouslyToOpencelliD);
        edit.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDB);
        edit.putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry);
        edit.apply();

        if (!NsUtils.isNetworkAvailable(context))
        {
            new android.app.AlertDialog.Builder(context)
                    .setTitle(R.string.uploader_no_internet_title)
                    .setMessage(R.string.uploader_no_internet_message)
                    .setCancelable(true)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        binding.uploadButton.setEnabled(false);

        Data inputData = new Data.Builder()
                .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_OPENCELLID, uploadToOpenCellId)
                .putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymouslyToOpencelliD)
                .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_TO_BEACONDB, uploadToBeaconDB)
                .putBoolean(NetworkSurveyConstants.PROPERTY_UPLOAD_RETRY_ENABLED, retry)
                .build();

        OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(NsUploaderWorker.class)
                .addTag(NsUploaderWorker.WORKER_TAG)
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();
        showUploadProgress(uploadWorkRequest.getId());

        WorkManager.getInstance(context).enqueue(uploadWorkRequest);
    }

    /**
     * Resets the upload progress and results UI to the default state.
     */
    private void resetUploadUi()
    {
        binding.uploadProgressGroup.setVisibility(View.GONE);
        binding.uploadProgressStatus.setVisibility(View.GONE);
        binding.uploadResultsGroup.setVisibility(View.GONE);
        binding.opencellidUploadStatus.setText("");
        binding.beacondbUploadStatus.setText("");
        binding.ocidResultMessage.setText("");
        binding.beacondbResultMessage.setText("");
    }

    private void showUploadProgress(UUID workId)
    {
        Context context = getContext();
        if (context == null) return;

        binding.uploadProgressGroup.setVisibility(View.VISIBLE);
        binding.uploadProgressBar.setProgress(0);

        WorkManager.getInstance(context)
                .getWorkInfoByIdLiveData(workId)
                .observe(getViewLifecycleOwner(), new Observer<>()
                {
                    private final String innerTag = NetworkSurveyActivity.class.getSimpleName() + "." + NsUploaderWorker.class.getSimpleName();

                    @Override
                    public void onChanged(WorkInfo workInfo)
                    {
                        if (workInfo == null)
                        {
                            Timber.tag(innerTag).w("onChanged(): WorkInfo is null");
                            binding.uploadProgressGroup.setVisibility(View.GONE);
                            return;
                        }

                        Data progress = workInfo.getProgress();
                        int currentPercent = progress.getInt(NsUploaderWorker.PROGRESS, NsUploaderWorker.PROGRESS_MIN_VALUE);
                        int maxPercent = progress.getInt(NsUploaderWorker.PROGRESS_MAX, NsUploaderWorker.PROGRESS_MAX_VALUE);
                        String statusMessage = progress.getString(NsUploaderWorker.PROGRESS_STATUS_MESSAGE);
                        Timber.tag(innerTag).d("onChanged(): Updating progress: current=%s max=%s", currentPercent, maxPercent);
                        currentPercent = Math.min(currentPercent, maxPercent);
                        binding.uploadProgressBar.setProgress(currentPercent);
                        binding.uploadPercentage.setText(context.getString(R.string.upload_percentage, currentPercent));

                        if (!Strings.isNullOrEmpty(statusMessage) || workInfo.getState() == WorkInfo.State.ENQUEUED)
                        {
                            if (workInfo.getState() == WorkInfo.State.ENQUEUED)
                            {
                                statusMessage = context.getString(R.string.uploader_enqueued);
                            }
                            binding.uploadProgressStatus.setVisibility(View.VISIBLE);
                            binding.uploadProgressStatus.setText(statusMessage);
                        } else
                        {
                            binding.uploadProgressStatus.setVisibility(View.GONE);
                        }

                        if (workInfo.getState().isFinished())
                        {
                            showUploaderFinished(workInfo);
                        }
                    }
                });
    }

    private void showUploaderFinished(WorkInfo workInfo)
    {
        queryUploadQueueCount();

        binding.uploadButton.setEnabled(true);
        binding.uploadProgressGroup.setVisibility(View.GONE);

        binding.uploadResultsGroup.setVisibility(View.VISIBLE);

        Context context = getContext();
        if (context == null) return;

        if (workInfo.getState() == WorkInfo.State.CANCELLED)
        {
            Toast.makeText(context, R.string.uploader_canceled, Toast.LENGTH_LONG).show();
            binding.opencellidUploadStatus.setText(R.string.uploader_canceled);
            binding.opencellidUploadStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_error));

            binding.beacondbUploadStatus.setText(R.string.uploader_canceled);
            binding.beacondbUploadStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_error));
        } else
        {
            String ocidResult = workInfo.getOutputData().getString(NsUploaderWorker.OCID_RESULT);
            binding.opencellidUploadStatus.setText(ocidResult);
            binding.opencellidUploadStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_tertiary));

            String beaconDbResult = workInfo.getOutputData().getString(NsUploaderWorker.BEACONDB_RESULT);
            binding.beacondbUploadStatus.setText(beaconDbResult);
            binding.beacondbUploadStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_tertiary));

            String ocidResultMessage = workInfo.getOutputData().getString(NsUploaderWorker.OCID_RESULT_MESSAGE);
            if (Strings.isNullOrEmpty(ocidResultMessage))
            {
                binding.ocidResultMessage.setVisibility(View.GONE);
            } else
            {
                binding.ocidResultMessage.setVisibility(View.VISIBLE);
            }
            binding.ocidResultMessage.setText(ocidResultMessage);

            String beaconDbResultMessage = workInfo.getOutputData().getString(NsUploaderWorker.BEACONDB_RESULT_MESSAGE);
            if (Strings.isNullOrEmpty(beaconDbResultMessage))
            {
                binding.beacondbResultMessage.setVisibility(View.GONE);
            } else
            {
                binding.beacondbResultMessage.setVisibility(View.VISIBLE);
            }
            binding.beacondbResultMessage.setText(beaconDbResultMessage);
        }

        WorkManager.getInstance(context).pruneWork();
    }

    /**
     * @return True if the {@link Manifest.permission#ACCESS_FINE_LOCATION} permission has been granted.  False otherwise.
     */
    private boolean hasLocationPermission()
    {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The ACCESS_FINE_LOCATION permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * @return True if the NestedScrollView is scrolled to the bottom. False otherwise.
     */
    private boolean isScrolledToBottom()
    {
        Rect scrollBounds = new Rect();
        binding.dashboardScrollView.getDrawingRect(scrollBounds);
        int bottom = binding.dashboardScrollView.getChildAt(0).getBottom() + binding.dashboardScrollView.getPaddingBottom();
        int delta = bottom - scrollBounds.bottom;
        return delta == 0;
    }
}
