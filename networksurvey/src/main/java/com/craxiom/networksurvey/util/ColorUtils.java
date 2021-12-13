package com.craxiom.networksurvey.util;

import android.graphics.Color;

import androidx.annotation.ColorInt;

/**
 * Utility methods for working with colors in this app.
 *
 * @since 1.6.0
 */
public class ColorUtils
{
    /**
     * The number of colors used.  The first color will be minColor and the last color will be maxColor, with n - 2
     * colors evenly spaced in between the two.
     */
    private static final int NUMBER_COLORS = 20;

    /**
     * The number of comparison values.  The number of values is one less than the number of colors, since the values
     * act as boundaries between the colors.
     */
    private static final int NUMBER_VALUES = NUMBER_COLORS - 1;

    @ColorInt
    private static final int[] COLOR_BINS = generateRssiColors(0xFFD50000, 0xFF388E3C); // Red and Green

    /**
     * Generates NUMBER_COLORS colors between the minColor and maxColor (inclusive).
     *
     * @param minColor The color used for values below the minimum
     * @param maxColor The color used for values above the maximum
     * @return Array of NUMBER_COLORS colors
     */
    private static int[] generateRssiColors(@ColorInt int minColor, @ColorInt int maxColor)
    {
        int[] colors = new int[NUMBER_COLORS];

        final int minRed = Color.red(minColor);
        final int minGreen = Color.green(minColor);
        final int minBlue = Color.blue(minColor);

        // Determine the difference between the min and max colors
        int deltaRed = Color.red(maxColor) - minRed;
        int deltaGreen = Color.green(maxColor) - minGreen;
        int deltaBlue = Color.blue(maxColor) - minBlue;

        // Divide the deltas by the number of spaces between colors (which is one less than the number of colors)
        double stepRed = (double) deltaRed / NUMBER_VALUES;
        double stepGreen = (double) deltaGreen / NUMBER_VALUES;
        double stepBlue = (double) deltaBlue / NUMBER_VALUES;

        // Don't recalculate the max color; it will not be the same due to rounding error
        for (int i = 0; i < NUMBER_VALUES; i++)
        {
            int red = (int) (minRed + stepRed * i);
            int green = (int) (minGreen + stepGreen * i);
            int blue = (int) (minBlue + stepBlue * i);

            colors[i] = lightenColor(Color.rgb(red, green, blue), 0.1f);
        }

        // Add the max color to the list (since it was not added as part of the calculation)
        colors[NUMBER_VALUES] = maxColor;

        return colors;
    }

    /**
     * Lightens a color by the specified {@code value}.
     *
     * @param color The color to lighten
     * @param value A value to lighten the color by in the range of 0.0 to 1.0.
     * @return The lightened color.
     */
    @ColorInt
    public static int lightenColor(@ColorInt int color,
                                   float value)
    {
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl);
        hsl[2] += value;
        hsl[2] = Math.max(0f, Math.min(hsl[2], 1f));
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
    }

    /**
     * Returns the color from the "bin" that aligns with the provided signal strength value. In other words, this
     * method returns a graded signal value based on the provided signal value.This signal strength value
     * is not a value in dBm, but instead is a normalized value starting at zero and ending at the provided
     * {@code maxValue}.
     *
     * @param signalStrength The normalized signal value starting at 0 and ending at {@code maxValue}.
     * @param maxValue       The maximum value.
     * @return The color that correlates to teh specified values
     */
    public static int getSignalColorForValue(int signalStrength, int maxValue)
    {
        if (signalStrength <= 0) return COLOR_BINS[0];

        // Divide the delta by the number of spaces between values (which is one less than the number of values)
        final double step = (double) (maxValue) / (NUMBER_VALUES - 1);
        final int index = Math.min((int) ((signalStrength) / step), NUMBER_VALUES);

        return COLOR_BINS[index];
    }
}
