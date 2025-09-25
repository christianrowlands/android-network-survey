package com.craxiom.networksurvey.logging.db.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing NS Analytics connection information.
 * This stores the registration details after successful QR code scan and registration.
 */
@Entity(tableName = "ns_analytics_connection")
public class NsAnalyticsConnectionEntity
{
    @PrimaryKey
    @NonNull
    public String workspaceId = "";

    /**
     * Device token for authentication (stored encrypted)
     */
    public String deviceToken;

    /**
     * API URL for the NS Analytics backend
     */
    public String apiUrl;

    /**
     * Device ID that was registered
     */
    public String deviceId;

    /**
     * Timestamp when the device was registered
     */
    public long registeredAt;

    /**
     * Timestamp of last successful upload
     */
    public long lastUploadAt = 0;

    /**
     * Total number of records uploaded
     */
    public long totalRecordsUploaded = 0;

    /**
     * Whether the connection is currently active
     */
    public boolean isActive = true;

    /**
     * Upload frequency setting in minutes (0 for real-time)
     */
    public int uploadFrequencyMinutes = 15;
}