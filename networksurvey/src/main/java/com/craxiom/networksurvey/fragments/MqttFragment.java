package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.util.PreferenceUtils.populatePrefsFromMqttConnectionSettings;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.craxiom.mqttlibrary.ICredentialStorage;
import com.craxiom.mqttlibrary.MqttConstants;
import com.craxiom.mqttlibrary.connection.BrokerConnectionInfo;
import com.craxiom.mqttlibrary.ui.AConnectionFragment;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.craxiom.networksurvey.mqtt.MqttConnectionInfo;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver;
import com.craxiom.networksurvey.survey.model.Destination;
import com.craxiom.networksurvey.survey.model.RecordType;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.craxiom.networksurvey.util.CredentialSecureStorage;
import com.craxiom.networksurvey.util.LocalNetworkPermissionUtil;
import com.craxiom.networksurvey.util.MdmUtils;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Set;

import timber.log.Timber;

/**
 * A fragment for allowing the user to connect to an MQTT broker. This fragment handles
 * the UI portion of the connection and delegates the actual connection logic to {@link NetworkSurveyService}.
 */
public class MqttFragment extends AConnectionFragment<NetworkSurveyService.SurveyServiceBinder>
{
    // The record types streamed over MQTT are the survey's record types, chosen on the Survey
    // screen. Only the device status heartbeat keeps its own switch (and MDM key) here.
    private MaterialSwitch deviceStatusStreamToggleSwitch;

    private Button qrCodeScanButton;

    private boolean deviceStatusStreamEnabled = NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING;

    private final ActivityResultLauncher<String> cameraPermissionRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted)
                {
                    FragmentActivity activity = getActivity();
                    if (activity == null) return;
                    SharedViewModel viewModel = new ViewModelProvider(activity).get(SharedViewModel.class);
                    viewModel.triggerNavigationToQrCodeScanner(getCurrentMqttConnectionSettings());
                } else
                {
                    Toast.makeText(getContext(), getString(R.string.grant_camera_permission), Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (view != null)
        {
            Button changeTopicPrefixButton = view.findViewById(R.id.changePrefixButton);
            changeTopicPrefixButton.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.md_theme_primaryContainer));
            changeTopicPrefixButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onPrimaryContainer));
        }

        return view;
    }

    public void setMqttConnectionSettings(MqttConnectionSettings mqttConnectionSettings)
    {
        // The mqttConnectionSettings will be passed as an argument if the QR code scanner fragment
        // was just displayed. A nuance to this is that even if the user did not scan a QR code, we
        // will still get mqttConnectionSettings. This is because when the user clicks on the "Scan
        // Code" button we don't want them to lose all of the settings they have already entered in
        // the event they immediately cancel the QR code scan, so we might just be getting the
        // settings that were already entered instead of new QR scanned settings.
        if (mqttConnectionSettings != null)
        {
            populatePrefsFromMqttConnectionSettings(mqttConnectionSettings, getContext());
        }
    }

    @Override
    protected void inflateAdditionalFieldsViewStub(LayoutInflater layoutInflater, ViewStub viewStub)
    {
        viewStub.setLayoutResource(R.layout.fragment_stream_options);
        View inflatedStub = viewStub.inflate();

        deviceStatusStreamToggleSwitch = inflatedStub.findViewById(R.id.streamDeviceStatusToggleSwitch);
        qrCodeScanButton = inflatedStub.findViewById(R.id.code_scan_button);

        Context context = getContext();
        if (context != null)
        {
            boolean underMdmControl = MdmUtils.isUnderMdmControl(context, MqttConstants.PROPERTY_MQTT_PASSWORD);
            if (!underMdmControl)
            {
                // For security reasons, we don't want to expose the MDM password via the share QR code,
                // so only enable the button if the password is not under MDM control.
                Button codeShareButton = inflatedStub.findViewById(R.id.code_share_button);
                codeShareButton.setVisibility(View.VISIBLE);
                codeShareButton.setOnClickListener(v -> {
                    storeConnectionParameters(); // Store the parameters so that the latest values are shared
                    SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                    viewModel.triggerNavigationToQrCodeShare(getCurrentMqttConnectionSettings());
                });
            }

            boolean underMdmControlAndEnabled = MdmUtils.isUnderMdmControlAndEnabled(context, MqttConstants.PROPERTY_MQTT_CONNECTION_HOST);
            updateQrCodeScanButtonVisibility(!underMdmControlAndEnabled);
        }
    }

    @Override
    protected Context getApplicationContext()
    {
        return requireActivity().getApplicationContext();
    }

    @Override
    protected ICredentialStorage getCredentialStorage()
    {
        return new ICredentialStorage()
        {
            @Override
            public void storeCredentials(String username, String password)
            {
                CredentialSecureStorage.INSTANCE.storeMqttCredentials(
                        getApplicationContext(), username, password);
            }

            @Override
            public String getUsername()
            {
                String username = CredentialSecureStorage.INSTANCE.getMqttUsername(getApplicationContext());
                return username != null ? username : "";
            }

            @Override
            public String getPassword()
            {
                String password = CredentialSecureStorage.INSTANCE.getMqttPassword(getApplicationContext());
                return password != null ? password : "";
            }
        };
    }

    @Override
    protected Class<?> getServiceClass()
    {
        return NetworkSurveyService.class;
    }

    @Override
    protected void readMdmConfigAdditionalProperties(Bundle mdmProperties)
    {
        deviceStatusStreamEnabled = mdmProperties.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
    }

    /**
     * Update the UI fields from the instance variables in this class.
     */
    @Override
    protected void updateUiFieldsFromStoredValues()
    {
        super.updateUiFieldsFromStoredValues();

        deviceStatusStreamToggleSwitch.setChecked(deviceStatusStreamEnabled);
    }

    @Override
    protected void readUIAdditionalFields()
    {
        deviceStatusStreamEnabled = deviceStatusStreamToggleSwitch.isChecked();
    }

    @Override
    protected void storeAdditionalParameters(SharedPreferences.Editor editor)
    {
        editor.putBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, deviceStatusStreamEnabled);
    }

    @Override
    protected void restoreAdditionalParameters(SharedPreferences sharedPreferences)
    {
        deviceStatusStreamEnabled = sharedPreferences.getBoolean(NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED, NetworkSurveyConstants.DEFAULT_MQTT_DEVICE_STATUS_STREAM_SETTING);
    }

    @Override
    protected void setConnectionInputFieldsEditable(boolean editable, boolean force)
    {
        super.setConnectionInputFieldsEditable(editable, force);

        deviceStatusStreamToggleSwitch.setEnabled(editable);
    }

    @Override
    protected BrokerConnectionInfo getBrokerConnectionInfo()
    {
        // Required at API 37+ for LAN-hosted brokers. Auto-granted when Bluetooth scan/connect is
        // already granted (same NEARBY_DEVICES permission group), so most existing users see no
        // dialog. We do not block the connect on denial: public-internet brokers continue to work
        // without it.
        LocalNetworkPermissionUtil.ensureLocalNetworkPermission(getActivity());

        // The record type flags are left off: the survey service streams the survey's record types.
        return new MqttConnectionInfo(host,
                portNumber,
                tlsEnabled,
                deviceName,
                mqttUsername,
                mqttPassword,
                false,
                false,
                false,
                false,
                deviceStatusStreamEnabled,
                false,
                topicPrefix,
                null,
                mqttQos);
    }

    @Override
    protected void onMdmOverride(boolean mdmOverride)
    {
        // Sending the immediate device status and copying the organization's broker into the
        // user's settings are done by the survey service when the override preference changes,
        // so they happen no matter which screen changed it.
        updateQrCodeScanButtonVisibility(mdmOverride);

        super.onMdmOverride(mdmOverride);
    }

    /**
     * Update the visibility of the QR code scan button based on the provide visibility flag, which
     * should be set based on the MDM override setting.
     *
     * @param visible True if the QR code scan button should be visible, false otherwise.
     */
    private void updateQrCodeScanButtonVisibility(boolean visible)
    {
        if (visible)
        {
            // Hiding the scan button is not a security setting, just a UX choice. We don't want
            // the user to override the MDM settings by scanning a QR code.
            qrCodeScanButton.setVisibility(View.VISIBLE);
            qrCodeScanButton.setOnClickListener(v -> {
                if (hasCameraPermission())
                {
                    SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                    viewModel.triggerNavigationToQrCodeScanner(getCurrentMqttConnectionSettings());
                } else
                {
                    cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA);
                }
            });
        } else
        {
            qrCodeScanButton.setVisibility(View.GONE);
        }
    }

    /**
     * Read current values from the MQTT Connection Fragment and return an instance of {@link MqttConnectionSettings}
     * object with those values.
     */
    private MqttConnectionSettings getCurrentMqttConnectionSettings()
    {
        int portNumber;
        try
        {
            portNumber = Integer.parseInt(mqttPortNumberEdit.getText().toString());
        } catch (NumberFormatException e)
        {
            portNumber = 1883;
        }

        final Set<RecordType> streamTypes = SurveyTypeResolver.typesForCurrentSetup(requireContext(), Destination.MQTT);

        return new MqttConnectionSettings.Builder()
                .host(mqttHostAddressEdit.getText().toString())
                .port(portNumber)
                .tlsEnabled(tlsToggleSwitch.isChecked())
                .deviceName(deviceNameEdit.getText().toString())
                .mqttUsername(usernameEdit.getText().toString())
                .mqttPassword(passwordEdit.getText().toString())
                .mqttTopicPrefix(topicPrefix)
                .mqttQos(mqttQos != null ? mqttQos.getValue() : MqttConstants.DEFAULT_MQTT_QOS.getValue())
                // Older app versions still read these flags from a shared QR code, so they are
                // filled in from the survey's record types. This app ignores them when importing.
                .cellularStreamEnabled(streamTypes.contains(RecordType.CELLULAR))
                .wifiStreamEnabled(streamTypes.contains(RecordType.WIFI))
                .bluetoothStreamEnabled(streamTypes.contains(RecordType.BLUETOOTH))
                .gnssStreamEnabled(streamTypes.contains(RecordType.GNSS))
                .deviceStatusStreamEnabled(deviceStatusStreamEnabled)
                .phoneStateStreamEnabled(streamTypes.contains(RecordType.PHONE_STATE))
                .build();
    }

    /**
     * @return True if the {@link Manifest.permission#CAMERA} permission has been granted. False otherwise.
     */
    private boolean hasCameraPermission()
    {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The CAMERA permission has not been granted");
            return false;
        }

        return true;
    }

}
