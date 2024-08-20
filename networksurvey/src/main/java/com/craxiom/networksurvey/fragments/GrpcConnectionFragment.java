package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.craxiom.mqttlibrary.IConnectionStateListener;
import com.craxiom.mqttlibrary.connection.ConnectionState;
import com.craxiom.mqttlibrary.ui.HelpCardListener;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings;
import com.craxiom.networksurvey.services.GrpcConnectionService;

import java.net.URI;

import timber.log.Timber;

/**
 * A fragment for allowing the user to connect to a remote gRPC based server.  This fragment handles
 * the UI portion of the connection and delegates the actual connection logic to {@link GrpcConnectionService}.
 *
 * @since 0.0.4
 */
public class GrpcConnectionFragment extends Fragment implements IConnectionStateListener
{
    private static final int ACCESS_PERMISSION_REQUEST_ID = 10;

    private final Handler uiThreadHandler;

    private Context applicationContext;

    private CardView connectionStatusCardView;
    private TextView connectionStatusText;
    private SwitchCompat grpcConnectionToggleSwitch;
    private EditText grpcHostAddressEdit;
    private EditText grpcPortNumberEdit;
    private EditText deviceNameEdit;

    private GrpcConnectionService grpcConnectionService;

    private String host = "";
    private Integer portNumber = NetworkSurveyConstants.DEFAULT_GRPC_PORT;
    private String deviceName = "";

    private SwitchCompat cellularStreamToggleSwitch;
    private SwitchCompat phoneStateStreamToggleSwitch;
    private SwitchCompat wifiStreamToggleSwitch;
    private SwitchCompat bluetoothStreamToggleSwitch;
    private SwitchCompat gnssStreamToggleSwitch;
    private SwitchCompat deviceStatusStreamToggleSwitch;

    private boolean cellularStreamEnabled = true;
    private boolean phoneStateStreamEnabled = true;
    private boolean wifiStreamEnabled = true;
    private boolean bluetoothStreamEnabled = true;
    private boolean gnssStreamEnabled = true;
    private boolean deviceStatusStreamEnabled = true;

    public GrpcConnectionFragment()
    {
        uiThreadHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        applicationContext = requireActivity().getApplicationContext();
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View view = inflater.inflate(R.layout.fragment_grpc_connection, container, false);

        connectionStatusCardView = view.findViewById(R.id.connection_status_card_view);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        grpcConnectionToggleSwitch = view.findViewById(R.id.grpcConnectToggleSwitch);
        grpcHostAddressEdit = view.findViewById(R.id.grpcHostAddress);
        grpcPortNumberEdit = view.findViewById(R.id.grpcPortNumber);
        deviceNameEdit = view.findViewById(R.id.deviceName);

        cellularStreamToggleSwitch = view.findViewById(R.id.streamCellularToggleSwitch);
        phoneStateStreamToggleSwitch = view.findViewById(R.id.streamPhoneStateToggleSwitch);
        wifiStreamToggleSwitch = view.findViewById(R.id.streamWifiToggleSwitch);
        bluetoothStreamToggleSwitch = view.findViewById(R.id.streamBluetoothToggleSwitch);
        gnssStreamToggleSwitch = view.findViewById(R.id.streamGnssToggleSwitch);
        deviceStatusStreamToggleSwitch = view.findViewById(R.id.streamDeviceStatusToggleSwitch);

        final CardView helpCardView = view.findViewById(R.id.help_card_view);
        helpCardView.setOnClickListener(new HelpCardListener(view, R.string.grpc_connection_description));

        restoreConnectionParameters();
        grpcHostAddressEdit.setText(host);
        grpcPortNumberEdit.setText(String.valueOf(portNumber));
        deviceNameEdit.setText(deviceName);

        readSettingsAndUpdateStreamToggleSwitches();

        // Adding the OnTouchListener as well so that we can reject drag events since those are much harder to deal with
        // Also checking for buttonView.isPressed() so that we don't trigger the onConnectionSwitchToggled call when we
        // programmatically set the toggle switch position.
        grpcConnectionToggleSwitch.setOnClickListener((buttonView) -> {
            if (buttonView.isPressed()) onConnectionSwitchToggled();
        });
        grpcConnectionToggleSwitch.setOnTouchListener((buttonView, motionEvent) ->
                motionEvent.getActionMasked() == MotionEvent.ACTION_MOVE);

        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);

        return view;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        initializeFragmentBasedOnConnectionState();
    }

    @Override
    public void onPause()
    {
        if (grpcConnectionService != null)
        {
            grpcConnectionService.unregisterConnectionStateListener(this);
        }

        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        if (grpcConnectionService != null)
        {
            grpcConnectionService.unregisterConnectionStateListener(this);
        }
        hideSoftInputFromWindow();
        super.onDestroyView();
    }

    @Override
    public void onConnectionStateChange(ConnectionState newConnectionState)
    {
        uiThreadHandler.post(() -> updateUiState(newConnectionState));
    }

    /**
     * Reads the current connection state, and initializes the UI and other fragment items.
     */
    private void initializeFragmentBasedOnConnectionState()
    {
        final ConnectionState connectionState = GrpcConnectionService.getConnectedState();
        updateUiState(connectionState);

        if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING)
        {
            startService(false);
        }
    }

    /**
     * Checks the current state of the connection toggle switch and initiates either the connection or disconnection.
     *
     * @since 0.1.1
     */
    private void onConnectionSwitchToggled()
    {
        if (!hasInternetPermission()) return;

        if (grpcConnectionToggleSwitch.isChecked())
        {
            connectToGrpcServer();
        } else
        {
            disconnectFromGrpcServer();
        }
    }

    /**
     * Checks to see if the Internet permission has been granted.  If it has not, false is returned, but a request is put in to get
     * access to the Internet permission.
     *
     * @return True if the Internet permission has already been granted, false otherwise.
     */
    private boolean hasInternetPermission()
    {
        final boolean hasPermission = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;

        Timber.d("Has Internet permission: %s", hasPermission);

        if (hasPermission) return true;

        ActivityCompat.requestPermissions(requireActivity(), new String[]{
                        Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);
        return false;
    }

    /**
     * Updates the UI based on the different states of the server connection.
     *
     * @param connectionState The new state of the server connection to update the UI for.
     */
    private synchronized void updateUiState(ConnectionState connectionState)
    {
        Timber.d("Updating the UI state for: %s", connectionState);

        // It is possible that the user has switched away from the view during a connection attempt, and in that event
        // the view will be refreshed in the onResume method.
        if (!isVisible()) return;

        switch (connectionState)
        {
            case DISCONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnected));
                grpcConnectionToggleSwitch.setEnabled(true);
                grpcConnectionToggleSwitch.setChecked(false);
                setFieldsEditable(true);
                break;

            case CONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnecting, null));
                connectionStatusText.setText(getString(R.string.status_connecting));
                grpcConnectionToggleSwitch.setEnabled(true);
                grpcConnectionToggleSwitch.setChecked(true);
                setFieldsEditable(false);
                break;

            case CONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnected, null));
                connectionStatusText.setText(getString(R.string.status_connected));
                grpcConnectionToggleSwitch.setEnabled(true);
                grpcConnectionToggleSwitch.setChecked(true);
                setFieldsEditable(false);
                break;

            case DISCONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnecting));
                grpcConnectionToggleSwitch.setEnabled(false);
                grpcConnectionToggleSwitch.setChecked(true);
                setFieldsEditable(false);
                break;
        }
    }

    /**
     * Read the connection values from the UI, and then start the {@link GrpcConnectionService} and pass it the
     * parameters so it can establish the connection to the gRPC server.
     * <p>
     * If the connection values from the UI are invalid, then the connection is not started and a Toast is dislayed to
     * the user.
     */
    private void connectToGrpcServer()
    {
        Timber.d("Attempting to connect to a gRPC server");

        try
        {
            if (!areConnectionParametersValid())
            {
                updateUiState(ConnectionState.DISCONNECTED);
                Timber.w("Can't connect because one ore more of the connection parameters are invalid");
                return;
            }

            updateUiState(ConnectionState.CONNECTING);

            host = grpcHostAddressEdit.getText().toString();
            final String portString = grpcPortNumberEdit.getText().toString();
            portNumber = Integer.valueOf(portString);
            deviceName = deviceNameEdit.getText().toString();

            cellularStreamEnabled = cellularStreamToggleSwitch.isChecked();
            phoneStateStreamEnabled = phoneStateStreamToggleSwitch.isChecked();
            wifiStreamEnabled = wifiStreamToggleSwitch.isChecked();
            bluetoothStreamEnabled = bluetoothStreamToggleSwitch.isChecked();
            gnssStreamEnabled = gnssStreamToggleSwitch.isChecked();
            deviceStatusStreamEnabled = deviceStatusStreamToggleSwitch.isChecked();

            storeConnectionParameters();
            storeStreamSettings();

            hideSoftInputFromWindow();

            startService(true);
        } catch (Exception e)
        {
            Timber.e("An exception occurred when trying to connect to the remote gRPC server");
            updateUiState(ConnectionState.DISCONNECTED);
        }
    }

    /**
     * Validates the user-specified server connection parameters.
     * <p>
     * For the host name, all non empty strings are 'valid'.
     * For the port number, all numeric numbers between 0 and 65535 are 'valid'.
     * For the device name, it must not be empty.
     *
     * @return True if all parameters are valid according to the above criteria.
     */
    private boolean areConnectionParametersValid()
    {
        final String hostAddress = grpcHostAddressEdit.getText().toString();
        if (hostAddress.isEmpty())
        {
            final String hostEmptyMessage = "Host address must be specified";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, hostEmptyMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        if (deviceNameEdit.getText().toString().isEmpty())
        {
            final String deviceNameEmptyMessage = "A device name must be specified";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, deviceNameEmptyMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        final int portNumber;
        try
        {
            portNumber = Integer.parseInt(grpcPortNumberEdit.getText().toString());
            if (portNumber < 0 || portNumber > 65535)
            {
                final String invalidPortNumberMessage = "Port number must be between 0 and 65535";
                uiThreadHandler.post(() -> Toast.makeText(applicationContext, invalidPortNumberMessage, Toast.LENGTH_SHORT).show());
                return false;
            }
        } catch (Exception e)
        {
            final String portNotANumberMessage = "Port must be a number";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, portNotANumberMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        try
        {
            @SuppressWarnings("unused") final URI uri = new URI(null, null, hostAddress, portNumber, null, null, null);
        } catch (Exception e)
        {
            final String hostInvalidMessage = "Host address must be in a valid format, usually just a domain name or an IP address";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, hostInvalidMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        // Make sure at least one streaming option is enabled
        if (!cellularStreamToggleSwitch.isChecked() && !phoneStateStreamToggleSwitch.isChecked() && !wifiStreamToggleSwitch.isChecked() &&
                !bluetoothStreamToggleSwitch.isChecked() && !gnssStreamToggleSwitch.isChecked() && !deviceStatusStreamToggleSwitch.isChecked())
        {
            final String noStreamsEnabledMessage = "At least one stream must be enabled";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, noStreamsEnabledMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        return true;
    }

    /**
     * Store the connection host address and port number so they can be used on app restart.
     */
    private void storeConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        final SharedPreferences.Editor edit = preferences.edit();
        if (host != null)
        {
            edit.putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, host);
        }
        edit.putInt(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT, portNumber);
        if (deviceName != null)
        {
            edit.putString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME, deviceName);
        }
        edit.apply();
    }

    /**
     * Restore the connection host address and port number.
     */
    private void restoreConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        final String restoredHost = preferences.getString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_HOST, "");
        if (!restoredHost.isEmpty()) host = restoredHost;

        final int restoredPortNumber = preferences.getInt(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_CONNECTION_PORT, NetworkSurveyConstants.DEFAULT_GRPC_PORT);
        if (restoredPortNumber != -1) portNumber = restoredPortNumber;

        final String restoredDeviceName = preferences.getString(NetworkSurveyConstants.PROPERTY_NETWORK_SURVEY_DEVICE_NAME, "");
        if (!restoredDeviceName.isEmpty()) deviceName = restoredDeviceName;
    }

    private void storeStreamSettings()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_GRPC_CELLULAR_STREAM_ENABLED, cellularStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_GRPC_PHONE_STATE_STREAM_ENABLED, phoneStateStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_GRPC_WIFI_STREAM_ENABLED, wifiStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_GRPC_BLUETOOTH_STREAM_ENABLED, bluetoothStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_GRPC_GNSS_STREAM_ENABLED, gnssStreamEnabled);
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_GRPC_DEVICE_STATUS_STREAM_ENABLED, deviceStatusStreamEnabled);
        editor.apply();
    }

    private void readSettingsAndUpdateStreamToggleSwitches()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        // Use the same MQTT defaults.
        cellularStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_GRPC_CELLULAR_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        phoneStateStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_GRPC_PHONE_STATE_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_CELLULAR_STREAM_SETTING);
        wifiStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_GRPC_WIFI_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_WIFI_STREAM_SETTING);
        bluetoothStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_GRPC_BLUETOOTH_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_BLUETOOTH_STREAM_SETTING);
        gnssStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_GRPC_GNSS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_GNSS_STREAM_SETTING);
        deviceStatusStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_GRPC_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);

        cellularStreamToggleSwitch.setChecked(cellularStreamEnabled);
        phoneStateStreamToggleSwitch.setChecked(phoneStateStreamEnabled);
        wifiStreamToggleSwitch.setChecked(wifiStreamEnabled);
        bluetoothStreamToggleSwitch.setChecked(bluetoothStreamEnabled);
        gnssStreamToggleSwitch.setChecked(gnssStreamEnabled);
        deviceStatusStreamToggleSwitch.setChecked(deviceStatusStreamEnabled);
    }

    /**
     * Disconnect from the gRPC server if it is connected.  If it is not connected, then do nothing.
     */
    private void disconnectFromGrpcServer()
    {
        GrpcConnectionService.disconnectFromGrpcServer(applicationContext);
    }

    /**
     * Sets all the appropriate fields as either editable or disable.
     *
     * @param editable True if the connection parameter fields should be editable, false otherwise.
     */
    private void setFieldsEditable(boolean editable)
    {
        grpcHostAddressEdit.setEnabled(editable);
        grpcPortNumberEdit.setEnabled(editable);
        deviceNameEdit.setEnabled(editable);

        cellularStreamToggleSwitch.setEnabled(editable);
        phoneStateStreamToggleSwitch.setEnabled(editable);
        wifiStreamToggleSwitch.setEnabled(editable);
        bluetoothStreamToggleSwitch.setEnabled(editable);
        gnssStreamToggleSwitch.setEnabled(editable);
        deviceStatusStreamToggleSwitch.setEnabled(editable);
    }

    /**
     * Hides the input keyboard on the current view.
     */
    private void hideSoftInputFromWindow()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null)
        {
            Timber.e("Unable to get the activity from the gRPC Connection Fragment");
        } else
        {
            final InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null)
            {
                inputMethodManager.hideSoftInputFromWindow(grpcHostAddressEdit.getWindowToken(), 0);
            }
        }
    }

    /**
     * Starts and then binds to the {@link GrpcConnectionService}.  If the connect parameter is true, then we also try
     * to start the connection to the gRPC server.
     *
     * @param connect If true, then after the service is started and bound, then a gRPC server connection is opened
     *                using the current instance host and port variables.
     */
    private synchronized void startService(boolean connect)
    {
        // Start the service
        Timber.i("Starting the gRPC Connection Service");
        final Intent serviceIntent = new Intent(applicationContext, GrpcConnectionService.class);
        applicationContext.startService(serviceIntent);

        // Bind to the service
        ServiceConnection serviceConnection = new GrpcServiceConnection(connect);
        final boolean bound = applicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_ABOVE_CLIENT);
        Timber.i("GrpcConnectionService bound: %s", bound);
    }

    /**
     * A {@link ServiceConnection} implementation for binding to the {@link GrpcConnectionService}.
     */
    private class GrpcServiceConnection implements ServiceConnection
    {
        private final boolean connect;

        /**
         * @param connect If true, then after the service is started and bound, then a gRPC server connection is opened
         *                using the current instance host and port variables.
         */
        GrpcServiceConnection(boolean connect)
        {
            this.connect = connect;
        }

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder iBinder)
        {
            Timber.i("%s service connected", name);
            final GrpcConnectionService.ConnectionServiceBinder binder = (GrpcConnectionService.ConnectionServiceBinder) iBinder;
            grpcConnectionService = binder.getService();
            grpcConnectionService.registerConnectionStateListener(GrpcConnectionFragment.this);

            if (connect)
            {
                GrpcConnectionSettings connectionSettings = new GrpcConnectionSettings.Builder()
                        .host(host)
                        .port(portNumber)
                        .deviceName(deviceName)
                        .cellularStreamEnabled(cellularStreamEnabled)
                        .phoneStateStreamEnabled(phoneStateStreamEnabled)
                        .wifiStreamEnabled(wifiStreamEnabled)
                        .bluetoothStreamEnabled(bluetoothStreamEnabled)
                        .gnssStreamEnabled(gnssStreamEnabled)
                        .deviceStatusStreamEnabled(deviceStatusStreamEnabled)
                        .build();
                GrpcConnectionService.connectToGrpcServer(applicationContext, connectionSettings);
            } else
            {
                // Update the UI state just in case the static variable had become stale in the GrpcConnectionService
                // due to the service being stopped by the Android system without calling the onDestory() method.
                updateUiState(GrpcConnectionService.getConnectedState());
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Timber.i("%s service disconnected", name);
            grpcConnectionService = null;
        }
    }
}
