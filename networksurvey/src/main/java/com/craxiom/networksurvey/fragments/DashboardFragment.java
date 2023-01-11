package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.fragment.NavHostFragment;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.databinding.FragmentDashboardBinding;
import com.craxiom.networksurvey.fragments.model.DashboardViewModel;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.ToggleLoggingTask;

import java.text.DecimalFormat;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * This fragment displays a dashboard to the user with various status information
 *
 * @since 1.10.0
 */
public class DashboardFragment extends AServiceDataFragment implements LocationListener
{
    private final DecimalFormat locationFormat = new DecimalFormat("###.#####");

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        binding = FragmentDashboardBinding.inflate(inflater);

        final ViewModelStoreOwner viewModelStoreOwner = NavHostFragment.findNavController(this).getViewModelStoreOwner(R.id.nav_graph);
        final ViewModelProvider viewModelProvider = new ViewModelProvider(viewModelStoreOwner);
        viewModel = viewModelProvider.get(getClass().getName(), DashboardViewModel.class);

        initializeLocationTextView();

        initializeUiListeners();

        initializeObservers();

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

        startAndBindToService();
    }

    @Override
    public void onDestroyView()
    {
        removeObservers();

        super.onDestroyView();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        service.registerLocationListener(this);
        initializeLocationTextView(); // Refresh the location views because we might have missed something between the
        // initial call and when we registered as a listener.

        initializeLogging(service);
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        service.unregisterLocationListener(this);
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

    /**
     * Add click listeners to the appropriate places in the UI, such as on the file logging toggle
     * switches.
     */
    private void initializeUiListeners()
    {
        initializeLoggingSwitch(binding.cellularLoggingToggleSwitch, newEnabledState -> {
            viewModel.setCellularLoggingEnabled(newEnabledState);
            toggleCellularLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.wifiLoggingToggleSwitch, newEnabledState -> {
            viewModel.setWifiLoggingEnabled(newEnabledState);
            toggleWifiLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.bluetoothLoggingToggleSwitch, newEnabledState -> {
            viewModel.setBluetoothLoggingEnabled(newEnabledState);
            toggleBluetoothLogging(newEnabledState);
        });

        initializeLoggingSwitch(binding.gnssLoggingToggleSwitch, newEnabledState -> {
            viewModel.setGnssLoggingEnabled(newEnabledState);
            toggleGnssLogging(newEnabledState);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initializeLoggingSwitch(SwitchCompat loggingSwitch, Consumer<Boolean> switchAction)
    {
        loggingSwitch.setOnClickListener((buttonView) -> {
            if (buttonView.isPressed())
            {
                boolean newEnabledState = ((SwitchCompat) buttonView).isChecked();
                switchAction.accept(newEnabledState);
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

        viewModel.getProviderEnabled().observe(viewLifecycleOwner, this::updateLocationProviderStatus);
        viewModel.getLocation().observe(viewLifecycleOwner, this::updateLocationTextView);

        viewModel.getCellularLoggingEnabled().observe(viewLifecycleOwner, this::updateCellularLogging);
        viewModel.getWifiLoggingEnabled().observe(viewLifecycleOwner, this::updateWifiLogging);
        viewModel.getBluetoothLoggingEnabled().observe(viewLifecycleOwner, this::updateBluetoothLogging);
        viewModel.getGnssLoggingEnabled().observe(viewLifecycleOwner, this::updateGnssLogging);
    }

    /**
     * Cleans up by removing all the view model observers.
     */
    private void removeObservers()
    {
        final LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();

        viewModel.getProviderEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getLocation().removeObservers(viewLifecycleOwner);

        viewModel.getCellularLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getWifiLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getBluetoothLoggingEnabled().removeObservers(viewLifecycleOwner);
        viewModel.getGnssLoggingEnabled().removeObservers(viewLifecycleOwner);
    }

    private synchronized void initializeLogging(NetworkSurveyService networkSurveyService)
    {
        viewModel.setCellularLoggingEnabled(networkSurveyService.isCellularLoggingEnabled());
        viewModel.setWifiLoggingEnabled(networkSurveyService.isWifiLoggingEnabled());
        viewModel.setBluetoothLoggingEnabled(networkSurveyService.isBluetoothLoggingEnabled());
        viewModel.setGnssLoggingEnabled(networkSurveyService.isGnssLoggingEnabled());
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
        final TextView accuracyTextView = binding.locationCard.accuracy;
        if (latestLocation != null)
        {
            final String latLonString = locationFormat.format(latestLocation.getLatitude()) + ", " +
                    locationFormat.format(latestLocation.getLongitude());
            locationTextView.setText(latLonString);
            locationTextView.setTextColor(getResources().getColor(R.color.normalText, null));

            accuracyTextView.setText(getString(R.string.accuracy_value, Integer.toString(MathUtils.roundAccuracy(latestLocation.getAccuracy()))));
        } else
        {
            locationTextView.setText(R.string.low_gps_confidence);
            locationTextView.setTextColor(Color.YELLOW);

            accuracyTextView.setText(getString(R.string.accuracy_initial));
        }
    }

    /**
     * Starts or stops writing the Cellular log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleCellularLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleCellularLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.cellular_logging_toggle_failed);
            updateCellularLogging(enabled);
            return getString(enabled ? R.string.cellular_logging_start_toast : R.string.cellular_logging_stop_toast);
        }, getContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the Cellular log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleWifiLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleWifiLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.wifi_logging_toggle_failed);
            updateWifiLogging(enabled);
            return getString(enabled ? R.string.wifi_logging_start_toast : R.string.wifi_logging_stop_toast);
        }, getContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the Bluetooth log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleBluetoothLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleBluetoothLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.bluetooth_logging_toggle_failed);
            updateBluetoothLogging(enabled);
            return getString(enabled ? R.string.bluetooth_logging_start_toast : R.string.bluetooth_logging_stop_toast);
        }, getContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Starts or stops writing the GNSS log file based on the specified parameter.
     *
     * @param enable True if logging should be enabled, false if it should be turned off.
     */
    private void toggleGnssLogging(boolean enable)
    {
        new ToggleLoggingTask(() -> {
            if (service != null)
            {
                return service.toggleGnssLogging(enable);
            }
            return null;
        }, enabled -> {
            if (enabled == null) return getString(R.string.gnss_logging_toggle_failed);
            updateGnssLogging(enabled);
            return getString(enabled ? R.string.gnss_logging_start_toast : R.string.gnss_logging_stop_toast);
        }, getContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Updates the cellular logging UI to indicate if logging is enabled or disabled.
     *
     * @param enabled The new status indicating if logging is enabled.
     */
    private void updateCellularLogging(boolean enabled)
    {
        binding.cellularLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.logging_status_disabled);
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
        binding.wifiLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.logging_status_disabled);
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
        binding.bluetoothLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.logging_status_disabled);
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
        binding.gnssLoggingStatus.setText(enabled ? R.string.logging_status_enabled : R.string.logging_status_disabled);
        binding.gnssLoggingToggleSwitch.setChecked(enabled);

        ColorStateList colorStateList = null;
        if (enabled) colorStateList = ColorStateList.valueOf(Color.GREEN);

        binding.gnssIcon.setImageTintList(colorStateList);
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
}
