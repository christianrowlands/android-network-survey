package com.craxiom.networksurvey.fragments;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
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
import com.craxiom.networksurvey.util.NsAnalyticsDeepLinkHandler;
import com.craxiom.networksurvey.util.NsAnalyticsDeepLinkHandler.DeepLinkResult;
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage;

import timber.log.Timber;

/**
 * Fragment responsible for scanning NS Analytics QR codes for device registration.
 * The QR code should contain a URL in the format:
 * https://networksurvey.app/app/register?token=xxx&workspace_id=yyy&api_url=https://...
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
                // Parse the scanned URL using the deep link handler
                Uri scannedUri = Uri.parse(result.getText());
                DeepLinkResult parseResult = NsAnalyticsDeepLinkHandler.INSTANCE.parseUri(scannedUri);

                if (parseResult instanceof DeepLinkResult.Success)
                {
                    NsAnalyticsQrData qrData = ((DeepLinkResult.Success) parseResult).getQrData();

                    // Store the QR data temporarily
                    NsAnalyticsSecureStorage.INSTANCE.storeQrData(context, qrData);

                    // Stop the scanner to freeze on the successful scan
                    codeScanner.stopPreview();

                    // Back to the NS Analytics page, which asks to confirm the workspace
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } else if (parseResult instanceof DeepLinkResult.Error)
                {
                    int reasonRes = ((DeepLinkResult.Error) parseResult).getMessageRes();
                    String errorMessage = context.getString(reasonRes);
                    Timber.w("QR code validation failed: %s", context.getResources().getResourceEntryName(reasonRes));
                    Toast.makeText(context, context.getString(R.string.ns_analytics_qr_invalid, errorMessage), Toast.LENGTH_LONG).show();
                    // Continue scanning for valid QR codes
                    codeScanner.startPreview();
                } else
                {
                    // DeepLinkResult.NotApplicable - not a valid NS Analytics URL
                    Timber.w("Scanned QR code is not an NS Analytics registration URL: %s", result.getText());
                    Toast.makeText(context, R.string.ns_analytics_qr_not_nsa, Toast.LENGTH_LONG).show();
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