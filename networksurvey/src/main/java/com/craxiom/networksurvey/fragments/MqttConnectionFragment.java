package com.craxiom.networksurvey.fragments;

import android.Manifest;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

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
import com.craxiom.networksurvey.listeners.IConnectionStateListener;
import com.craxiom.networksurvey.mqtt.MqttBrokerConnectionInfo;
import com.craxiom.networksurvey.services.NetworkSurveyService;

/**
 * A fragment for allowing the user to connect to an MQTT broker.  This fragment handles
 * the UI portion of the connection and delegates the actual connection logic to {@link NetworkSurveyService}.
 *
 * @since 0.1.1
 */
public class MqttConnectionFragment extends Fragment implements View.OnClickListener, IConnectionStateListener
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
    private CardView helpCardView;

    private NetworkSurveyService surveyService;

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

        helpCardView = view.findViewById(R.id.help_card_view);
        helpCardView.setOnClickListener(v -> {
            final ToggleButton expandArrow = view.findViewById(R.id.expand_toggle_button);
            final TextView connectionDescriptionText = view.findViewById(R.id.mqtt_connection_description);
            if (connectionDescriptionText.getVisibility() == View.VISIBLE)
            {
                connectionDescriptionText.setVisibility(View.GONE);
                expandArrow.setChecked(false);
            } else
            {
                connectionDescriptionText.setVisibility(View.VISIBLE);
                expandArrow.setChecked(true);
            }
        });

        restoreConnectionParameters();

        if (isMdmConfigPresent()) mdmOverrideCard.setVisibility(View.VISIBLE);

        mdmOverrideToggleSwitch.setChecked(mdmOverride);
        mqttHostAddressEdit.setText(host);
        mqttPortNumberEdit.setText(String.valueOf(portNumber));
        tlsToggleSwitch.setChecked(tlsEnabled);
        deviceNameEdit.setText(deviceName);
        usernameEdit.setText(mqttUsername);
        passwordEdit.setText(mqttPassword);

        mdmOverrideToggleSwitch.setOnClickListener(v -> onMdmOverride(mdmOverrideToggleSwitch.isChecked()));
        mqttConnectionToggleSwitch.setOnClickListener(this);

        startAndBindToNetworkSurveyService();

        return view;
    }

    @Override
    public void onDestroyView()
    {
        if (surveyService != null) surveyService.unregisterMqttConnectionStateListener(this);
        hideSoftInputFromWindow();
        super.onDestroyView();
    }

    @Override
    public void onClick(View view)
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

    @Override
    public void onConnectionStateChange(ConnectionState newConnectionState)
    {
        uiThreadHandler.post(() -> updateUiState(newConnectionState));
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

            final boolean hasBrokerUri = mdmProperties.containsKey(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_URL);
            if (!hasBrokerUri) return false;

            final String mqttBrokerUri = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_BROKER_URL);
            final String clientId = mdmProperties.getString(NetworkSurveyConstants.PROPERTY_MQTT_CLIENT_ID);

            return mqttBrokerUri != null && clientId != null;
        }

        return false;
    }

    /**
     * Sets the appropriate UI elements as either enabled or disabled based on the MDM override setting.
     *
     * @param mdmOverride True if the MDM configuration should be ignored, false if the MDM configuration should be used.
     */
    private void onMdmOverride(boolean mdmOverride)
    {
        this.mdmOverride = mdmOverride;
        setConnectionInputFieldsEditable(mdmOverride);
        setAllFieldsVisible(mdmOverride);

        if (!mdmOverride && surveyService.getMqttConnectionState() == ConnectionState.CONNECTED)
        {
            surveyService.disconnectFromMqttBroker();
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
                setConnectionInputFieldsEditable(true);
                break;

            case CONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnecting, null));
                connectionStatusText.setText(getString(R.string.status_connecting));
                mqttConnectionToggleSwitch.setEnabled(true);
                mqttConnectionToggleSwitch.setChecked(true);
                setConnectionInputFieldsEditable(false);
                break;

            case CONNECTED:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusConnected, null));
                connectionStatusText.setText(getString(R.string.status_connected));
                mqttConnectionToggleSwitch.setEnabled(true);
                mqttConnectionToggleSwitch.setChecked(true);
                setConnectionInputFieldsEditable(false);
                break;

            case DISCONNECTING:
                connectionStatusCardView.setCardBackgroundColor(getResources().getColor(R.color.connectionStatusDisconnected, null));
                connectionStatusText.setText(getString(R.string.status_disconnecting));
                mqttConnectionToggleSwitch.setEnabled(false);
                mqttConnectionToggleSwitch.setChecked(true);
                setConnectionInputFieldsEditable(false);
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
            Log.e(LOG_TAG, "An exception occurred when trying to connect to the MQTT broker");
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
            final String deviceNameEmptyMessage = "A device name must be specified";
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
        if (deviceName != null) edit.putString(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_NAME, deviceName);
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

        final String restoredDeviceName = preferences.getString(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_NAME, "");
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
        final String uriPrefix = tlsEnabled ? "ssl://" : "tcp://";
        final String mqttBrokerUri = uriPrefix + host + ":" + portNumber;
        final String clientId = deviceName;

        return new MqttBrokerConnectionInfo(mqttBrokerUri, clientId, mqttUsername, mqttPassword);
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
     *
     * @param editable True if the connection parameter fields should be editable, false otherwise.
     */
    private void setConnectionInputFieldsEditable(boolean editable)
    {
        mqttHostAddressEdit.setEnabled(editable);
        mqttPortNumberEdit.setEnabled(editable);
        tlsToggleSwitch.setEnabled(editable);
        deviceNameEdit.setEnabled(editable);
        usernameEdit.setEnabled(editable);
        passwordEdit.setEnabled(editable);
    }

    /**
     * Sets all the UI elements as either visible or hidden.
     *
     * @param visible True if the UI elements should be visible, false otherwise.
     */
    private void setAllFieldsVisible(boolean visible)
    {
        final int visibility = visible ? View.VISIBLE : View.GONE;

        final View view = requireView();
        view.findViewById(R.id.mqttHostAddressTextInputLayout).setVisibility(visibility);
        view.findViewById(R.id.mqttPortNumberTextInputLayout).setVisibility(visibility);
        view.findViewById(R.id.deviceNameTextInputLayout).setVisibility(visibility);
        tlsToggleSwitch.setVisibility(visibility);
        view.findViewById(R.id.usernameTextInputLayout).setVisibility(visibility);
        view.findViewById(R.id.passwordTextInputLayout).setVisibility(visibility);
        connectionStatusCardView.setVisibility(visibility);
        helpCardView.setVisibility(visibility);
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
