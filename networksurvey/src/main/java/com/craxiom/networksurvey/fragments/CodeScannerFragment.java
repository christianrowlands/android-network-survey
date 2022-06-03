package com.craxiom.networksurvey.fragments;

import static com.craxiom.mqttlibrary.MqttConstants.*;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.fragments.model.ScannedSettings;
import com.google.gson.Gson;

/**
 * Fragment responsible for QR code scanning. Leverages an open source code scanning library from
 * Yuriy Budiev.
 *
 * @since 1.7.0
 */
public class CodeScannerFragment extends Fragment
{
    private CodeScanner codeScanner;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        final Activity activity = getActivity();
        View root = inflater.inflate(R.layout.fragment_scanner, container, false);

        CodeScannerView scannerView = root.findViewById(R.id.scanner_view);
        codeScanner = new CodeScanner(activity, scannerView);
        codeScanner.setDecodeCallback(result -> activity.runOnUiThread(() -> {

            if (!result.getText().isEmpty())
            {
                SharedPreferences preferences = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                SharedPreferences.Editor edit = preferences.edit();
                edit.putBoolean(PROPERTY_MQTT_MDM_OVERRIDE, true);

                try
                {
                    ScannedSettings scannedSettings = new Gson().fromJson(result.getText(), ScannedSettings.class);
                    if (scannedSettings.getHost() != null)
                    {
                        edit.putString(PROPERTY_MQTT_CONNECTION_HOST, scannedSettings.getHost());
                    }
                    if (scannedSettings.getPort() != 0)
                    {
                        edit.putInt(PROPERTY_MQTT_CONNECTION_PORT, scannedSettings.getPort());
                    }
                    if (scannedSettings.getTlsEnabled() != null)
                    {
                        edit.putBoolean(PROPERTY_MQTT_CONNECTION_TLS_ENABLED, scannedSettings.getTlsEnabled());
                    }
                    if (scannedSettings.getDeviceName() != null)
                    {
                        edit.putString(PROPERTY_MQTT_CLIENT_ID, scannedSettings.getDeviceName());
                    }
                    if (scannedSettings.getMqttUsername() != null)
                    {
                        edit.putString(PROPERTY_MQTT_USERNAME, scannedSettings.getMqttUsername());
                    }
                    if (scannedSettings.getMqttPassword() != null)
                    {
                        edit.putString(PROPERTY_MQTT_PASSWORD, scannedSettings.getMqttPassword());
                    }

                    edit.apply();

                    final String scanSuccess = "Successfully scanned the MQTT settings";
                    Toast.makeText(getContext(), scanSuccess, Toast.LENGTH_SHORT).show();

                    Navigation.findNavController(requireActivity(), getId())
                            .navigate(CodeScannerFragmentDirections.actionScannerFragmentToMqttConnectionFragment());
                } catch (Exception e)
                {
                    final String scanFailed = "Failed to read the MQTT settings";
                    Toast.makeText(getContext(), scanFailed, Toast.LENGTH_SHORT).show();
                }
            }
        }));

        scannerView.setOnClickListener(view -> codeScanner.startPreview());
        return root;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        codeScanner.startPreview();
    }

    @Override
    public void onPause()
    {
        codeScanner.releaseResources();
        super.onPause();
    }
}