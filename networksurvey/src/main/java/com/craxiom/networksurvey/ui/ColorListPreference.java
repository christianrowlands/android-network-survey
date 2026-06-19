package com.craxiom.networksurvey.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.DynamicDrawableSpan;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
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
    public void setValue(String value)
    {
        super.setValue(value);
        // Force the summary to update when value changes
        notifyChanged();
    }

    @Override
    public CharSequence getSummary()
    {
        // Get the selected entry
        CharSequence entry = getEntry();
        if (entry == null)
        {
            return super.getSummary();
        }

        // Create a spannable string with color box and text
        SpannableStringBuilder builder = new SpannableStringBuilder();

        // Add color box as a span
        String colorValue = getValue();
        Integer colorResId = COLOR_MAP.get(colorValue);
        if (colorResId != null)
        {
            // Create a small colored rectangle drawable
            GradientDrawable colorBox = new GradientDrawable();
            colorBox.setShape(GradientDrawable.RECTANGLE);
            colorBox.setColor(ContextCompat.getColor(getContext(), colorResId));
            colorBox.setCornerRadius(4);

            // Set the size of the color box (20dp x 20dp to better match text height)
            int size = (int) (20 * getContext().getResources().getDisplayMetrics().density);
            colorBox.setBounds(0, 0, size, size);

            // Add the color box as an image span with custom vertical centering
            SpannableString colorSpan = new SpannableString("  ");
            // Use a custom CenteredImageSpan for proper vertical alignment
            colorSpan.setSpan(new CenteredImageSpan(colorBox), 0, 1, 0);
            builder.append(colorSpan);
            builder.append(" ");
        }

        // Add the text
        builder.append(entry);

        return builder;
    }

    @Override
    protected void onClick()
    {
        final CharSequence[] entries = getEntries();
        final CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null) return;

        final FragmentManager fragmentManager = PreferenceDialogs.fragmentManagerFrom(getContext());
        if (fragmentManager == null) return;

        final String currentValue = getValue();
        int checkedItem = -1;
        final String[] names = new String[entries.length];
        final int[] colors = new int[entries.length];
        for (int i = 0; i < entries.length; i++)
        {
            names[i] = entries[i].toString();
            final Integer colorResId = COLOR_MAP.get(entryValues[i].toString());
            colors[i] = colorResId != null ? ContextCompat.getColor(getContext(), colorResId) : 0;
            if (entryValues[i].toString().equals(currentValue)) checkedItem = i;
        }

        final CharSequence dialogTitle = getDialogTitle();
        PreferenceDialogs.showColorChoiceDialog(fragmentManager,
                dialogTitle == null ? "" : dialogTitle.toString(), names, colors, checkedItem, which -> {
                    final String value = entryValues[which].toString();
                    if (callChangeListener(value))
                    {
                        setValue(value);
                    }
                });
    }

    /**
     * Custom ImageSpan that centers the drawable vertically with the text.
     */
    private static class CenteredImageSpan extends DynamicDrawableSpan
    {
        private final Drawable drawable;

        public CenteredImageSpan(Drawable drawable)
        {
            this.drawable = drawable;
        }

        @Override
        public Drawable getDrawable()
        {
            return drawable;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, @NonNull Paint paint)
        {
            Drawable b = getDrawable();
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            int transY = (y + fm.descent + y + fm.ascent) / 2 - b.getBounds().bottom / 2;

            canvas.save();
            canvas.translate(x, transY);
            b.draw(canvas);
            canvas.restore();
        }
    }
}