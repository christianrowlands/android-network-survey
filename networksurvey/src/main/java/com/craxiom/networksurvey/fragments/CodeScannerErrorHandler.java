package com.craxiom.networksurvey.fragments;

import android.app.Activity;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.budiyev.android.codescanner.CodeScanner;
import com.craxiom.networksurvey.R;

import timber.log.Timber;

/**
 * Handles a QR code scanner that cannot use the camera (none on the device, or it is busy or
 * disabled). Without an error callback the scanner library rethrows on its own thread and the page
 * is left as a blank preview, so the scanner pages use this to say what happened and go back.
 *
 * @since 2.0
 */
public final class CodeScannerErrorHandler
{
    private CodeScannerErrorHandler()
    {
    }

    /**
     * Sets the error callback on {@code codeScanner} so a camera failure shows a message and leaves
     * the scanner page hosted by {@code fragment}.
     */
    public static void install(Fragment fragment, CodeScanner codeScanner)
    {
        codeScanner.setErrorCallback(error -> {
            Timber.w(error, "The code scanner could not use the camera");
            final Activity activity = fragment.getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (!fragment.isAdded()) return;
                Toast.makeText(activity, R.string.camera_open_failed, Toast.LENGTH_LONG).show();
                // A paused page restarts the preview when it resumes, so only a visible page leaves.
                if (fragment.isResumed())
                {
                    fragment.requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        });
    }
}
