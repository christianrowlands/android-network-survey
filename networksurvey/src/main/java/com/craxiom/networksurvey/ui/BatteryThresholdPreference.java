package com.craxiom.networksurvey.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.R;

import timber.log.Timber;

/**
 * A custom Preference for battery threshold that shows a Material3 slider dialog.
 */
public class BatteryThresholdPreference extends Preference
{
    private int currentValue = 0;
    
    public BatteryThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    
    public BatteryThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }
    
    public BatteryThresholdPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }
    
    public BatteryThresholdPreference(Context context)
    {
        super(context);
    }
    
    @Override
    protected void onClick()
    {
        currentValue = getPersistedInt(0);

        final FragmentManager fragmentManager = PreferenceDialogs.fragmentManagerFrom(getContext());
        if (fragmentManager == null) return;

        final CharSequence title = getTitle();
        PreferenceDialogs.showBatteryThresholdDialog(fragmentManager,
                title == null ? "" : title.toString(), currentValue, newValue -> {
                    if (newValue != currentValue)
                    {
                        persistInt(newValue);
                        currentValue = newValue;
                        notifyChanged();
                    }
                });
    }

    @Override
    protected void onSetInitialValue(Object defaultValue)
    {
        currentValue = getPersistedInt(defaultValue == null ? 0 : (Integer) defaultValue);
    }
    
    @Override
    protected int getPersistedInt(int defaultReturnValue)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        try
        {
            // First try to get as int (new format)
            return prefs.getInt(getKey(), defaultReturnValue);
        } catch (ClassCastException e)
        {
            // If that fails, it's probably stored as String (old format)
            try
            {
                String stringValue = prefs.getString(getKey(), String.valueOf(defaultReturnValue));
                int intValue = Integer.parseInt(stringValue);
                
                // Migrate to int for future use
                prefs.edit().remove(getKey()).putInt(getKey(), intValue).apply();
                
                return intValue;
            } catch (Exception ex)
            {
                Timber.e(ex, "Failed to get battery threshold preference");
                return defaultReturnValue;
            }
        }
    }
    
    @Override
    protected boolean persistInt(int value)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.edit().putInt(getKey(), value).apply();
        return true;
    }
    
    @Override
    public CharSequence getSummary()
    {
        currentValue = getPersistedInt(0);
        if (currentValue == 0)
        {
            return getContext().getString(R.string.battery_management_disabled);
        } else
        {
            return getContext().getString(R.string.battery_threshold_summary, currentValue);
        }
    }
}