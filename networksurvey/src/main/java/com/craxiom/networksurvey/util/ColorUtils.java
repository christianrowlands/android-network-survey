package com.craxiom.networksurvey.util;

import android.graphics.Color;

import androidx.annotation.ColorInt;

import com.craxiom.networksurvey.R;

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
    //private static final int[] COLOR_BINS = generateRssiColors(0xFFc20606, 0xFF338a37); // Red and Green
    //private static final int[] COLOR_BINS = generateRssiColors(0xFF8a0606, 0xFF215c24); // Red and Green
    private static final int[] COLOR_BINS = generateRssiColors(0xFFb50d0d, 0xFF2d8031); // Red and Green

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

        float[] minHSL = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(minColor, minHSL);

        float[] maxHSL = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(maxColor, maxHSL);

        // Calculate the differences in the HSL components
        float deltaH = (maxHSL[0] - minHSL[0]) / NUMBER_VALUES;
        float deltaS = (maxHSL[1] - minHSL[1]) / NUMBER_VALUES;
        float deltaL = (maxHSL[2] - minHSL[2]) / NUMBER_VALUES;

        for (int i = 0; i < NUMBER_VALUES; i++)
        {
            float[] hsl = {
                    minHSL[0] + deltaH * i,
                    Math.max(0f, Math.min(1f, minHSL[1] + deltaS * i)), // Ensure the saturation stays within bounds
                    Math.max(0f, Math.min(1f, minHSL[2] + deltaL * i))  // Ensure the lightness stays within bounds
            };

            // Adjust saturation and lightness to improve the colors
            //hsl[1] = adjustSaturation(hsl[1], i);
            //hsl[2] = adjustLightness(hsl[2], i);

            colors[i] = androidx.core.graphics.ColorUtils.HSLToColor(hsl);
        }

        // Add the max color to the list (since it was not added as part of the calculation)
        colors[NUMBER_VALUES] = maxColor;

        return colors;
    }

    /**
     * Adjusts the saturation based on the position in the gradient.
     */
    private static float adjustSaturation(float saturation, int position)
    {
        // Example adjustment: Increase saturation in the middle of the gradient
        if (position > NUMBER_VALUES / 4 && position < 3 * NUMBER_VALUES / 4)
        {
            saturation += 0.1F; // Increase saturation by 10%
        }
        return Math.max(0f, Math.min(saturation, 1f)); // Ensure saturation remains within bounds
    }

    /**
     * Adjusts the lightness based on the position in the gradient.
     * Similar to adjustSaturation, this method can be customized to enhance visual appeal.
     */
    private static float adjustLightness(float lightness, int position)
    {
        // Example adjustment: Slightly lighten the middle of the gradient
        if (position > NUMBER_VALUES / 4 && position < 3 * NUMBER_VALUES / 4)
        {
            lightness += 0.05F; // Increase lightness by 5%
        }
        return Math.max(0f, Math.min(lightness, 1f)); // Ensure lightness remains within bounds
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
        if (signalStrength <= 0 || maxValue <= 0) return COLOR_BINS[0];

        // Divide the delta by the number of spaces between values (which is one less than the number of values)
        final double step = (double) (maxValue) / (NUMBER_VALUES - 1);
        int index = Math.min((int) ((signalStrength) / step), NUMBER_VALUES);

        if (index < 0) index = 0;

        return COLOR_BINS[index];
    }

    /**
     * @param signalStrength The signal strength value in dBm.
     * @return The resource ID for the color that should be used for the signal strength text.
     */
    public static int getColorForSignalStrength(float signalStrength)
    {
        final int colorResourceId;
        if (signalStrength > -60)
        {
            colorResourceId = R.color.rssi_green;
        } else if (signalStrength > -70)
        {
            colorResourceId = R.color.rssi_yellow;
        } else if (signalStrength > -80)
        {
            colorResourceId = R.color.rssi_orange;
        } else if (signalStrength > -90)
        {
            colorResourceId = R.color.rssi_red;
        } else
        {
            colorResourceId = R.color.rssi_deep_red;
        }

        return colorResourceId;
    }

    /**
     * Creates a faded version of the input color by adjusting its alpha value.
     *
     * @param color The original color in ARGB format (e.g., 0xFFRRGGBB).
     * @return The faded color with reduced alpha.
     */
    public static int getFadedColor(int color)
    {
        // Set the alpha to 10% of its full value to make the color really faded
        int fadedAlpha = (int) (255 * 0.2); // You can adjust the 0.2 to make it more or less faded

        // Use Color.argb to create a new color with the modified alpha
        // Maintain the original RGB values
        return Color.argb(fadedAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
