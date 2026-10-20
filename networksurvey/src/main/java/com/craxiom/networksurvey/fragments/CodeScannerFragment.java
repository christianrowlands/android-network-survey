package com.craxiom.networksurvey.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.fragments.model.MqttConnectionSettings;
import com.craxiom.networksurvey.ui.main.SharedViewModel;
import com.google.gson.Gson;

import timber.log.Timber;

/**
 * Scans an MQTT broker QR code and hands it to the MQTT page, which adds it as a broker profile.
 * Leverages an open source code scanning library from Yuriy Budiev.
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
            final MqttConnectionSettings settings = parseBrokerCode(result.getText());
            if (settings == null)
            {
                Toast.makeText(activity, R.string.mqtt_qr_not_broker, Toast.LENGTH_LONG).show();
                codeScanner.startPreview();
                return;
            }

            // The MQTT page adds the broker and says what happened.
            SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
            viewModel.onMqttQrCodeScanned(settings);
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        }));

        scannerView.setOnClickListener(view -> codeScanner.startPreview());
        return root;
    }

    /**
     * @return The broker in a scanned code, or null if the code is not an MQTT broker code (it is not
     * the broker JSON, or it has no host).
     */
    @Nullable
    private static MqttConnectionSettings parseBrokerCode(String text)
    {
        if (text == null || text.isEmpty()) return null;
        try
        {
            final MqttConnectionSettings settings = new Gson().fromJson(text, MqttConnectionSettings.class);
            if (settings == null || settings.host() == null || settings.host().trim().isEmpty()) return null;
            return settings;
        } catch (Exception e)
        {
            Timber.i(e, "The scanned code is not an MQTT broker code");
            return null;
        }
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