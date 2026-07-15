package com.craxiom.networksurvey.util;

import android.telephony.ServiceState;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Helpers for working with the per-carrier cell bandwidth array reported by
 * {@link ServiceState#getCellBandwidths()} (values are in kHz). Used by the carrier aggregation
 * display on the cellular details screen.
 */
public final class CellularBandwidthUtils
{
    private CellularBandwidthUtils()
    {
    }

    /**
     * Filters a cell bandwidth array down to plausible entries. Some devices report 0 or the
     * framework "unavailable" sentinel for carriers they cannot measure; counting or summing those
     * would produce nonsense like "2 carriers, 0 MHz".
     *
     * @param cellBandwidthsKhz The bandwidths in kHz; may be null/empty.
     * @return The entries greater than 0 and less than Integer.MAX_VALUE, never null.
     */
    @NonNull
    public static int[] validBandwidthsKhz(int[] cellBandwidthsKhz)
    {
        if (cellBandwidthsKhz == null) return new int[0];
        int count = 0;
        for (int khz : cellBandwidthsKhz)
        {
            if (isValid(khz)) count++;
        }
        final int[] valid = new int[count];
        int i = 0;
        for (int khz : cellBandwidthsKhz)
        {
            if (isValid(khz)) valid[i++] = khz;
        }
        return valid;
    }

    /**
     * Sums the per-carrier bandwidths, ignoring invalid entries.
     *
     * @param cellBandwidthsKhz The bandwidths in kHz; may be null/empty.
     * @return The aggregate bandwidth in kHz, or 0 if there is nothing to sum.
     */
    public static int aggregateBandwidthKhz(int[] cellBandwidthsKhz)
    {
        int totalKhz = 0;
        for (int khz : validBandwidthsKhz(cellBandwidthsKhz)) totalKhz += khz;
        return totalKhz;
    }

    /**
     * Formats a kHz bandwidth as an MHz display value: "20" for 20000, "1.4" for 1400 (LTE's
     * 1.4 MHz carriers must not truncate to "1").
     *
     * @param khz The bandwidth in kHz.
     * @return The MHz value without a unit suffix.
     */
    @NonNull
    public static String formatBandwidthMhz(int khz)
    {
        if (khz % 1_000 == 0) return String.valueOf(khz / 1_000);
        return String.format(Locale.getDefault(), "%.1f", khz / 1_000.0);
    }

    private static boolean isValid(int khz)
    {
        return khz > 0 && khz < Integer.MAX_VALUE;
    }
}
