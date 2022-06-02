package com.craxiom.networksurvey.fragments;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
 * @since 0.2.5
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
                edit.putBoolean("mqtt_mdm_override", true);
                final ScannedSettings scannedSettings = new Gson().fromJson(result.getText(), ScannedSettings.class);

                if (scannedSettings.getHost() != null)
                {
                    edit.putString("mqtt_connection_host", scannedSettings.getHost());
                }
                if (scannedSettings.getPort() != 0)
                {
                    edit.putInt("mqtt_connection_port", scannedSettings.getPort());
                }
                if (scannedSettings.getTlsEnabled() != null)
                {
                    edit.putBoolean("mqtt_tls_enabled", scannedSettings.getTlsEnabled());
                }
                if (scannedSettings.getDeviceName() != null)
                {
                    edit.putString("mqtt_client_id", scannedSettings.getDeviceName());
                }
                if (scannedSettings.getMqttUsername() != null)
                {
                    edit.putString("mqtt_username", scannedSettings.getMqttUsername());
                }
                if (scannedSettings.getMqttPassword() != null)
                {
                    edit.putString("mqtt_password", scannedSettings.getMqttPassword());
                }

                edit.apply();

                Navigation.findNavController(requireActivity(), getId())
                        .navigate(CodeScannerFragmentDirections.actionScannerFragmentToMqttConnectionFragment());
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