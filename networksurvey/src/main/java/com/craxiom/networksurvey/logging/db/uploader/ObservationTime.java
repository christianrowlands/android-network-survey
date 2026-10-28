package com.craxiom.networksurvey.logging.db.uploader;

import androidx.annotation.Nullable;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Reads the time a survey record was observed, for the community database uploads. Both
 * OpenCelliD (measured_at) and BeaconDB (the geosubmit report timestamp) want the time the
 * measurement was taken, not the time it was uploaded, which can be hours or days later.
 */
public final class ObservationTime
{
    private ObservationTime()
    {
    }

    /**
     * Converts a record's device time to milliseconds since the Unix epoch.
     *
     * @param deviceTime The record's device time, an ISO 8601 offset date time (the format
     *                   {@code NsUtils.getRfc3339String} writes, e.g. "2026-10-28T09:15:42.123-04:00").
     * @return The observation time in epoch milliseconds, or null when the device time is missing
     * or cannot be read.
     */
    @Nullable
    public static Long toEpochMillis(@Nullable String deviceTime)
    {
        if (deviceTime == null || deviceTime.isEmpty()) return null;

        try
        {
            return OffsetDateTime.parse(deviceTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
        } catch (DateTimeParseException e)
        {
            return null;
        }
    }
}
