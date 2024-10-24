package com.craxiom.networksurvey.fragments;

import static com.craxiom.networksurvey.util.PreferenceUtils.populatePrefsFromMqttConnectionSettings;

import android.app.Activity;
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
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
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
        View root = inflater.inflate(R.layout.fragment_scanner, container, false);

        final Activity activity = getActivity();
        if (activity == null) return null;

        CodeScannerView scannerView = root.findViewById(R.id.scanner_view);
        codeScanner = new CodeScanner(activity, scannerView);
        codeScanner.setDecodeCallback(result -> activity.runOnUiThread(() -> {
            if (!result.getText().isEmpty())
            {
                try
                {
                    MqttConnectionSettings mqttConnectionSettings = new Gson().fromJson(result.getText(), MqttConnectionSettings.class);
                    populatePrefsFromMqttConnectionSettings(mqttConnectionSettings, getContext());

                    final String scanSuccess = "Successfully scanned the MQTT settings";
                    Toast.makeText(getContext(), scanSuccess, Toast.LENGTH_SHORT).show();

                    // TODO Navigate to Mqtt Fragment
                    Navigation.findNavController(requireActivity(), getId())
                            .navigate(CodeScannerFragmentDirections.actionScannerFragmentToMqttConnectionFragment()
                                    .setMqttConnectionSettings(mqttConnectionSettings)
                            );
                } catch (Exception e)
                {
                    final String scanFailed = "Failed to read the MQTT settings";
                    Toast.makeText(getContext(), scanFailed, Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireActivity(), getId())
                            .navigate(CodeScannerFragmentDirections.actionScannerFragmentToMqttConnectionFragment()
                                    .setMqttConnectionSettings(CodeScannerFragmentArgs.fromBundle(getArguments()).getMqttConnectionSettings())
                            );
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