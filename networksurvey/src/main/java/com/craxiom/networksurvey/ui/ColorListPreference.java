package com.craxiom.networksurvey.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;

import com.craxiom.networksurvey.R;

import java.util.HashMap;
import java.util.Map;

/**
 * A custom ListPreference that displays a colored indicator next to each color option.
 */
public class ColorListPreference extends ListPreference
{

    private static final Map<String, Integer> COLOR_MAP = new HashMap<>();

    static
    {
        COLOR_MAP.put("default", R.color.serving_cell_dark);
        COLOR_MAP.put("red", R.color.coverage_circle_red);
        COLOR_MAP.put("green", R.color.coverage_circle_green);
        COLOR_MAP.put("orange", R.color.coverage_circle_orange);
        COLOR_MAP.put("purple", R.color.coverage_circle_purple);
        COLOR_MAP.put("yellow", R.color.coverage_circle_yellow);
        COLOR_MAP.put("cyan", R.color.coverage_circle_cyan);
        COLOR_MAP.put("white", R.color.coverage_circle_white);
    }

    public ColorListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ColorListPreference(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public ColorListPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ColorListPreference(Context context)
    {
        super(context);
    }

    @Override
    protected void onClick()
    {
        // Create and show a custom dialog with colored entries
        createColorDialog();
    }

    private void createColorDialog()
    {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();

        if (entries == null || entryValues == null)
        {
            return;
        }

        // Find the currently selected item
        String currentValue = getValue();
        int checkedItem = -1;
        for (int i = 0; i < entryValues.length; i++)
        {
            if (entryValues[i].toString().equals(currentValue))
            {
                checkedItem = i;
                break;
            }
        }

        ColorAdapter adapter = new ColorAdapter(getContext(), entries, entryValues, checkedItem);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle(getDialogTitle());
        builder.setSingleChoiceItems(adapter, checkedItem, (dialog, which) -> {
            String value = entryValues[which].toString();
            if (callChangeListener(value))
            {
                setValue(value);
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private class ColorAdapter extends ArrayAdapter<CharSequence>
    {
        private final CharSequence[] entryValues;
        private int checkedPosition;

        public ColorAdapter(Context context, CharSequence[] entries, CharSequence[] entryValues, int checkedPosition)
        {
            super(context, 0, entries);
            this.entryValues = entryValues;
            this.checkedPosition = checkedPosition;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent)
        {
            if (convertView == null)
            {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.color_preference_item, parent, false);
            }

            android.widget.RadioButton radioButton = convertView.findViewById(R.id.radio_button);
            View colorIndicator = convertView.findViewById(R.id.color_indicator);
            TextView colorName = convertView.findViewById(R.id.color_name);

            // Set the color name
            colorName.setText(getItem(position));

            // Set the radio button state
            radioButton.setChecked(position == checkedPosition);

            // Set the color indicator
            String colorValue = entryValues[position].toString();
            Integer colorResId = COLOR_MAP.get(colorValue);
            if (colorResId != null)
            {
                int color = ContextCompat.getColor(getContext(), colorResId);
                colorIndicator.setBackgroundColor(color);
            }

            return convertView;
        }

        public void setCheckedPosition(int position)
        {
            this.checkedPosition = position;
            notifyDataSetChanged();
        }
    }
}