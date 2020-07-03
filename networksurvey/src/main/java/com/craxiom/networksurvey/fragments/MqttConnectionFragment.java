package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
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

import com.craxiom.networksurvey.ConnectionState;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.HelpCardListener;
import com.craxiom.networksurvey.listeners.IConnectionStateListener;
import com.craxiom.networksurvey.mqtt.MqttBrokerConnectionInfo;
import com.craxiom.networksurvey.services.NetworkSurveyService;

/**
 * A fragment for allowing the user to connect to an MQTT broker.  This fragment handles
 * the UI portion of the connection and delegates the actual connection logic to {@link NetworkSurveyService}.
 *
 * @since 0.1.1
 */
public class MqttConnectionFragment extends Fragment implements IConnectionStateListener
{
    private static final String LOG_TAG = MqttConnectionFragment.class.getSimpleName();

    private static final int ACCESS_PERMISSION_REQUEST_ID = 10;

    private final Handler uiThreadHandler;

    private Context applicationContext;

    private SwitchCompat mdmOverrideToggleSwitch;
    private CardView connectionStatusCardView;
    private TextView connectionStatusText;
    private SwitchCompat mqttConnectionToggleSwitch;
    private EditText mqttHostAddressEdit;
    private EditText mqttPortNumberEdit;
    private SwitchCompat tlsToggleSwitch;
    private EditText deviceNameEdit;
    private EditText usernameEdit;
    private EditText passwordEdit;

    private NetworkSurveyService surveyService;

    private boolean mdmConfigPresent;
    private boolean mdmOverride = false;
    private String host = "";
    private Integer portNumber = NetworkSurveyConstants.DEFAULT_MQTT_PORT;
    private boolean tlsEnabled = true;
    private String deviceName = "";
    private String mqttUsername = "";
    private String mqttPassword = "";

    public MqttConnectionFragment()
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
        final View view = inflater.inflate(R.layout.fragment_mqtt_connection, container, false);

        final CardView mdmOverrideCard = view.findViewById(R.id.mdm_override_card_view);
        mdmOverrideToggleSwitch = view.findViewById(R.id.mdm_override_toggle_switch);
        connectionStatusCardView = view.findViewById(R.id.connection_status_card_view);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        mqttConnectionToggleSwitch = view.findViewById(R.id.mqttConnectToggleSwitch);
        mqttHostAddressEdit = view.findViewById(R.id.mqttHostAddress);
        mqttPortNumberEdit = view.findViewById(R.id.mqttPortNumber);
        tlsToggleSwitch = view.findViewById(R.id.tlsToggleSwitch);
        deviceNameEdit = view.findViewById(R.id.deviceName);
        usernameEdit = view.findViewById(R.id.mqttUsername);
        passwordEdit = view.findViewById(R.id.mqttPassword);

        final CardView helpCardView = view.findViewById(R.id.help_card_view);
        helpCardView.setOnClickListener(new HelpCardListener(view, R.string.mqtt_connection_description));

        restoreConnectionParameters();

        mdmConfigPresent = isMdmConfigPresent();
        if (mdmConfigPresent)
        {
            Log.i(LOG_TAG, "MDM Configuration is present");

            mdmOverrideCard.setVisibility(View.VISIBLE);

            // Must use this mdmOverride flag after calling restoreConnectionParameters() above
            if (!mdmOverride)
            {
                readMdmConfig();
                setConnectionInputFieldsEditable(false, true);
            }
        }

        updateUiFieldsFromStoredValues();

        mdmOverrideToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onMdmOverride(mdmOverrideToggleSwitch.isChecked()));
        tlsToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) onTlsSwitchToggled();
        });

        // Adding the OnTouchListener as well so that we can reject drag events since those are much harder to deal with
        // Also checking for buttonView.isPressed() so that we don't trigger the onConnectionSwitchToggled call when we
        // programmatically set the toggle switch position.
        mqttConnectionToggleSwitch.setOnClickListener((buttonView) -> {
            if (buttonView.isPressed()) onConnectionSwitchToggled();
        });
        mqttConnectionToggleSwitch.setOnTouchListener((buttonView, motionEvent) ->
                motionEvent.getActionMasked() == MotionEvent.ACTION_MOVE);

        return view;
    }

    /**
     * Update the UI fields from the instance variables in this class.
     *
     * @since 0.1.5
     */
    private void updateUiFieldsFromStoredValues()
    {
        mdmOverrideToggleSwitch.setChecked(mdmOverride);
        mqttHostAddressEdit.setText(host);
        mqttPortNumberEdit.setText(String.valueOf(portNumber));
        tlsToggleSwitch.setChecked(tlsEnabled);
        deviceNameEdit.setText(deviceName);
        usernameEdit.setText(mqttUsername);
        passwordEdit.setText(mqttPassword);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        startAndBindToNetworkSurveyService();
    }

    @Override
    public void onPause()
    {
        if (surveyService != null) surveyService.unregisterMqttConnectionStateListener(this);

        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        hideSoftInputFromWindow();
        super.onDestroyView();
    }

    @Override
    public void onConnectionStateChange(ConnectionState newConnectionState)
    {
        uiThreadHandler.post(() -> updateUiState(newConnectionState));
    }

    /**
     * Checks the current state of the TLS toggle switch and updates the port number to reflect the selection.
     */
    private void onTlsSwitchToggled()
    {
        if (tlsToggleSwitch.isChecked())
        {
            mqttPortNumberEdit.setText(String.valueOf(NetworkSurveyConstants.MQTT_SSL_PORT));
        } else
        {
            mqttPortNumberEdit.setText(String.valueOf(NetworkSurveyConstants.MQTT_PLAIN_TEXT_PORT));
        }
    }

    /**
     * Checks the current state of the connection toggle switch and initiates either the connection or disconnection.
     */
    private void onConnectionSwitchToggled()
    {
        if (!hasInternetPermission()) return;

        if (mqttConnectionToggleSwitch.isChecked())
        {
            connectToMqttBroker();
        } else
        {
            disconnectFromMqttBroker();
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

        Log.d(LOG_TAG, "Has Internet permission: " + hasPermission);

        if (hasPermission) return true;

        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.INTERNET},
                ACCESS_PERMISSION_REQUEST_ID);
        return false;
    }

    /**
     * @return True if MQTT broker connection parameters are found in the MDM configuration, false if they are not found.
     */
    private boolean isMdmConfigPresent()
    {
        final RestrictionsManager restrictionsManager = (RestrictionsManager) requireActivity().getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager != null)
        {
            final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

            final boolean hasBrokerHost = mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_HOST);
            if (!hasBrokerHost) return false;

            final String mqttBrokerHost = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_HOST);
            final String clientId = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_CLIENT_ID);

            return mqttBrokerHost != null && clientId != null;
        }

        return false;
    }

    /**
     * Reads the MDM configuration from the restrictions manager. This method assumes that {@link #isMdmConfigPresent()}
     * has already been called to validate if the MDM config should be restored. Calling this method overrides the user
     * entered values, so it should only be called if an actual valid MDM config is present, and if the user has not
     * overridden the MDM config {@link NetworkSurveyConstants#PROPERTY_MQTT_MDM_OVERRIDE}.
     *
     * @since 0.1.3
     */
    private void readMdmConfig()
    {
        Log.d(LOG_TAG, "Reading the MDM Config from the RestrictionsManager");

        final RestrictionsManager restrictionsManager = (RestrictionsManager) requireActivity().getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null)
        {
            Log.wtf(LOG_TAG, "The MDM config was indicated as present but the restrictions manager is null");
            return;
        }

        final Bundle mdmProperties = restrictionsManager.getApplicationRestrictions();

        host = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_HOST);
        portNumber = mdmProperties.getInt(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_PORT, NetworkSurveyConstants.DEFAULT_MQTT_PORT);
        deviceName = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_CLIENT_ID, "");
        tlsEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_TLS_SETTING);
        mqttUsername = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_USERNAME);
        mqttPassword = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD);
    }

    /**
     * Sets the appropriate UI elements as either enabled or disabled based on the MDM override setting.
     *
     * @param mdmOverride True if the MDM configuration should be ignored, false if the MDM configuration should be used.
     */
    private void onMdmOverride(boolean mdmOverride)
    {
        this.mdmOverride = mdmOverride;
        storeMdmOverrideParameter();
        setConnectionInputFieldsEditable(mdmOverride, true);

        // If the user is toggling off the MDM override option, we need to re-attempt a connection to the MDM configured
        // MQTT broker.  In the event it does not work, we need to force a disconnect since the user could have
        // connected to their own MQTT broker while the MDM override was enabled.
        if (!mdmOverride)
        {
            readMdmConfig(); // Read the MDM config back into the UI since the user has returned control back to the MDM server
            updateUiFieldsFromStoredValues();
            surveyService.attemptMqttConnectWithMdmConfig(true);
        }
    }

    /**
     * Updates the UI based on the different states of the server connection.
     *
     * @param connectionState The new state of the server connection to update the UI for.
     */
    private void updateUiState(ConnectionState connectionState)
    {
        Log.d(LOG_TAG, "Updating the UI state for: " + connectionState);

        switch (connectionState)
        {
            case DISCONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnected));
                mqttConnectionToggleSwitch.setEnabled(true);
                mqttConnectionToggleSwitch.setChecked(false);
                setConnectionInputFieldsEditable(true, false);
                break;

            case CONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnecting, null));
                connectionStatusText.setText(getString(R.string.status_connecting));
                mqttConnectionToggleSwitch.setEnabled(true);
                mqttConnectionToggleSwitch.setChecked(true);
                setConnectionInputFieldsEditable(false, false);
                break;

            case CONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnected, null));
                connectionStatusText.setText(getString(R.string.status_connected));
                mqttConnectionToggleSwitch.setEnabled(true);
                mqttConnectionToggleSwitch.setChecked(true);
                setConnectionInputFieldsEditable(false, false);
                break;

            case DISCONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnecting));
                mqttConnectionToggleSwitch.setEnabled(false);
                mqttConnectionToggleSwitch.setChecked(true);
                setConnectionInputFieldsEditable(false, false);
                break;
        }
    }

    /**
     * Read the connection values from the UI, and then pass the values to the {@link NetworkSurveyService} so the
     * connection can be established.
     * <p>
     * If the connection values from the UI are invalid, then the connection is not started and a Toast is displayed to
     * the user.
     */
    private void connectToMqttBroker()
    {
        try
        {
            if (!areConnectionParametersValid())
            {
                updateUiState(ConnectionState.DISCONNECTED);
                Log.w(LOG_TAG, "Can't connect because one ore more of the connection parameters are invalid");
                return;
            }

            updateUiState(ConnectionState.CONNECTING);

            host = mqttHostAddressEdit.getText().toString();
            final String portString = mqttPortNumberEdit.getText().toString();
            portNumber = Integer.valueOf(portString);
            tlsEnabled = tlsToggleSwitch.isChecked();
            deviceName = deviceNameEdit.getText().toString();
            mqttUsername = usernameEdit.getText().toString();
            mqttPassword = passwordEdit.getText().toString();

            storeConnectionParameters();

            hideSoftInputFromWindow();

            surveyService.connectToMqttBroker(getMqttBrokerConnectionInfo());
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the MQTT broker", e);
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
        if (mqttHostAddressEdit.getText().toString().isEmpty())
        {
            final String hostEmptyMessage = "Host address must be specified";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, hostEmptyMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        if (deviceNameEdit.getText().toString().isEmpty())
        {
            final String deviceNameEmptyMessage = "A client ID must be specified";
            uiThreadHandler.post(() -> Toast.makeText(applicationContext, deviceNameEmptyMessage, Toast.LENGTH_SHORT).show());
            return false;
        }

        try
        {
            final int portNumber = Integer.parseInt(mqttPortNumberEdit.getText().toString());
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

        return true;
    }

    /**
     * Store the MDM override option to the shared preferences.
     *
     * @since 0.1.5
     */
    private void storeMdmOverrideParameter()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        final SharedPreferences.Editor edit = preferences.edit();

        edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_MDM_OVERRIDE, mdmOverride);

        edit.apply();
    }

    /**
     * Store the connection host address and port number so they can be used on app restart.
     */
    private void storeConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        final SharedPreferences.Editor edit = preferences.edit();

        edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_MDM_OVERRIDE, mdmOverride);
        if (host != null) edit.putString(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_HOST, host);
        edit.putInt(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_PORT, portNumber);
        edit.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, tlsEnabled);
        if (deviceName != null) edit.putString(NetworkSurveyConstants.PROPERTY_MQTT_CLIENT_ID, deviceName);
        if (mqttUsername != null) edit.putString(NetworkSurveyConstants.PROPERTY_MQTT_USERNAME, mqttUsername);
        if (mqttPassword != null) edit.putString(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD, mqttPassword);

        edit.apply();
    }

    /**
     * Restore the connection host address and port number.
     */
    private void restoreConnectionParameters()
    {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        mdmOverride = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_MDM_OVERRIDE, false);

        final String restoredHost = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_HOST, "");
        if (!restoredHost.isEmpty()) host = restoredHost;

        final int restoredPortNumber = preferences.getInt(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_PORT, NetworkSurveyConstants.DEFAULT_MQTT_PORT);
        if (restoredPortNumber != -1) portNumber = restoredPortNumber;

        tlsEnabled = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_CONNECTION_TLS_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_TLS_SETTING);

        final String restoredDeviceName = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_CLIENT_ID, "");
        if (!restoredDeviceName.isEmpty()) deviceName = restoredDeviceName;

        final String restoredUsername = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_USERNAME, "");
        if (!restoredUsername.isEmpty()) mqttUsername = restoredUsername;

        final String restoredPassword = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_PASSWORD, "");
        if (!restoredPassword.isEmpty()) mqttPassword = restoredPassword;
    }

    /**
     * Get the MQTT broker connection information to use to establish the connection.
     * <p>
     * It is assumed that {@link #areConnectionParametersValid()} has already been called, and the connection
     * information has been pulled from the UI and stored in the instance variables.
     *
     * @return The connection settings to use for the MQTT broker.
     * @since 0.1.1
     */
    private MqttBrokerConnectionInfo getMqttBrokerConnectionInfo()
    {
        return new MqttBrokerConnectionInfo(host, portNumber, tlsEnabled, deviceName, mqttUsername, mqttPassword);
    }

    /**
     * Disconnect from the MQTT broker if it is connected.  If it is not connected, then do nothing.
     */
    private void disconnectFromMqttBroker()
    {
        if (surveyService != null) surveyService.disconnectFromMqttBroker();
    }

    /**
     * Sets all the connection settings input fields as either editable or disable.
     * <p>
     * There is an edge case for this method where calling it will not update the editable status of the UI elements
     * because the UI is under MDM control. In that case, the UI will remain disabled until MDM control is released or
     * the MDM override toggle switch is enabled.
     *
     * @param editable True if the connection parameter fields should be editable, false otherwise.
     * @param force    Force the updating of the input fields regardless of the current MDM status.
     */
    private void setConnectionInputFieldsEditable(boolean editable, boolean force)
    {
        // Skip updating the UI elements if this device is under MDM control and the user has not turned on the MDM override option.
        if (!force && mdmConfigPresent && !mdmOverride) return;

        mqttHostAddressEdit.setEnabled(editable);
        mqttPortNumberEdit.setEnabled(editable);
        deviceNameEdit.setEnabled(editable);
        tlsToggleSwitch.setEnabled(editable);
        usernameEdit.setEnabled(editable);
        passwordEdit.setEnabled(editable);
    }

    /**
     * Hides the input keyboard on the current view.
     */
    private void hideSoftInputFromWindow()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null)
        {
            Log.e(LOG_TAG, "Unable to get the activity from the MQTT Connection Fragment");
        } else
        {
            final InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null)
            {
                inputMethodManager.hideSoftInputFromWindow(mqttHostAddressEdit.getWindowToken(), 0);
            }
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
     * A {@link ServiceConnection} implementation for binding to the {@link NetworkSurveyService}.
     */
    private class SurveyServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder)
        {
            Log.i(LOG_TAG, name + " service connected");
            surveyService = ((NetworkSurveyService.SurveyServiceBinder) binder).getService();
            surveyService.registerMqttConnectionStateListener(MqttConnectionFragment.this);

            updateUiState(surveyService.getMqttConnectionState());
        }

        @Override
        public void onServiceDisconnected(final ComponentName name)
        {
            Log.i(LOG_TAG, name + " service disconnected");
            surveyService = null;
        }
    }
}
