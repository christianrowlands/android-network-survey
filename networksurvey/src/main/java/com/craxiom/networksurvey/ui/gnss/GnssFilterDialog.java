package com.craxiom.networksurvey.ui.gnss;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.model.GnssType;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class GnssFilterDialog extends DialogFragment
        implements DialogInterface.OnMultiChoiceClickListener,
        DialogInterface.OnClickListener
{

    public static final String ITEMS = ".items";

    public static final String CHECKS = ".checks";

    private boolean[] mChecks;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = getArguments();
        String[] items = args.getStringArray(ITEMS);
        mChecks = args.getBooleanArray(CHECKS);
        if (savedInstanceState != null)
        {
            mChecks = savedInstanceState.getBooleanArray(CHECKS);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        return builder.setTitle(R.string.filter_dialog_title)
                .setMultiChoiceItems(items, mChecks, this)
                .setPositiveButton(R.string.save, this)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putBooleanArray(CHECKS, mChecks);
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        Set<GnssType> filter = new LinkedHashSet<>();
        GnssType[] gnssTypes = GnssType.values();
        for (int i = 0; i < mChecks.length; i++)
        {
            if (mChecks[i])
            {
                filter.add(gnssTypes[i]);
            }
        }

        PreferenceUtils.saveGnssFilter(Application.get(), filter, Application.getPrefs());
        dialog.dismiss();
    }

    @Override
    public void onClick(DialogInterface arg0, int which, boolean isChecked)
    {
        mChecks[which] = isChecked;
    }
}
