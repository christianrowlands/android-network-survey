package com.craxiom.networksurvey.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.R;
import com.google.android.material.slider.Slider;

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
        // Load current value
        currentValue = getPersistedInt(0);
        
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getTitle());
        
        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.dialog_battery_slider, null);
        builder.setView(dialogView);
        
        // Get views
        TextView percentageText = dialogView.findViewById(R.id.battery_percentage_text);
        TextView statusText = dialogView.findViewById(R.id.battery_status_text);
        Slider slider = dialogView.findViewById(R.id.battery_threshold_slider);
        
        // Set initial values
        slider.setValue(currentValue);
        updateTexts(percentageText, statusText, currentValue);
        
        // Set up slider listener
        slider.addOnChangeListener((slider1, value, fromUser) -> {
            int intValue = Math.round(value);
            updateTexts(percentageText, statusText, intValue);
        });
        
        // Set dialog buttons
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int newValue = Math.round(slider.getValue());
            if (newValue != currentValue)
            {
                persistInt(newValue);
                currentValue = newValue;
                notifyChanged();
            }
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        
        // Show dialog
        builder.create().show();
    }
    
    private void updateTexts(TextView percentageText, TextView statusText, int value)
    {
        if (value == 0)
        {
            percentageText.setText(getContext().getString(R.string.battery_management_disabled));
            percentageText.setTextSize(48);
            statusText.setText(R.string.battery_management_disabled_description);
        } else
        {
            percentageText.setText(value + "%");
            percentageText.setTextSize(48);
            statusText.setText(getContext().getString(R.string.battery_pause_active_description, value));
        }
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