package com.craxiom.networksurvey.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.util.PreferenceUtils;

/**
 * A dialog for notifying the user that their device does not support GNSS logging.
 */
public class GnssFailureDialogFragment extends DialogFragment
{
    private AlertDialog gnssFailureAlertDialog;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(R.layout.gnss_failure);
        builder.setPositiveButton(R.string.ok, (dialog, id) -> {
            CheckBox rememberDecisionCheckBox = gnssFailureAlertDialog.findViewById(R.id.failureRememberDecisionCheckBox);
            boolean checked = rememberDecisionCheckBox.isChecked();
            if (checked)
            {
                PreferenceUtils.saveBoolean(Application.get().getString(R.string.pref_key_ignore_raw_gnss_failure), true);
            }
        });

        gnssFailureAlertDialog = builder.create();
        return gnssFailureAlertDialog;
    }
}
