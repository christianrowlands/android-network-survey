package com.craxiom.networksurvey.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.data.api.NsAnalyticsQrData;
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage;
import com.google.gson.Gson;

import timber.log.Timber;

/**
 * Fragment responsible for scanning NS Analytics QR codes for device registration.
 * The QR code should contain JSON with workspace ID, API URL, and registration token.
 */
public class NsAnalyticsQrScannerFragment extends Fragment
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
                Context context = getContext();
                if (context == null)
                {
                    Timber.e("Context is null, cannot process QR code");
                    return;
                }
                try
                {
                    // Parse the QR code data
                    NsAnalyticsQrData qrData = new Gson().fromJson(result.getText(), NsAnalyticsQrData.class);

                    // Validate the QR data
                    // FIXME Validate the actual values not just the non-null check
                    if (qrData.getWorkspaceId() == null || qrData.getApiUrl() == null || qrData.getToken() == null)
                    {
                        throw new IllegalArgumentException("Invalid QR code - missing required fields");
                    }

                    // Store the QR data temporarily
                    NsAnalyticsSecureStorage.INSTANCE.storeQrData(context, qrData);

                    // Stop the scanner to freeze on the successful scan
                    codeScanner.stopPreview();

                    // Show success message
                    Toast.makeText(context, "QR code scanned successfully", Toast.LENGTH_SHORT).show();

                    // Navigate back to NS Analytics connection screen
                    // Using a slight delay to let the user see the success message
                    activity.runOnUiThread(() -> {
                        new android.os.Handler().postDelayed(() -> {
                            requireActivity().onBackPressed();
                        }, 500);
                    });
                } catch (Exception e)
                {
                    Timber.e(e, "Failed to read NS Analytics QR code");
                    Toast.makeText(context, "Invalid NS Analytics QR code", Toast.LENGTH_LONG).show();
                    // Continue scanning for valid QR codes
                    codeScanner.startPreview();
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