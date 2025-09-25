package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room entity for NS Analytics upload queue.
 * Stores complete protobuf messages as JSON for upload to NS Analytics backend.
 */
@Entity(tableName = "ns_analytics_queue",
        indices = {@Index(value = "uploaded"), @Index(value = "timestamp")})
public class NsAnalyticsQueueEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Type of record: "lte", "wifi", "bluetooth", "gnss", etc.
     */
    public String recordType;

    /**
     * Full protobuf message serialized as JSON
     */
    public String protobufJson;

    /**
     * Timestamp when the record was created
     */
    public long timestamp;

    /**
     * Batch ID for grouping records together
     */
    public String batchId;

    /**
     * Whether this record has been successfully uploaded
     */
    public boolean uploaded = false;

    /**
     * Number of upload retry attempts
     */
    public int retryCount = 0;

    /**
     * Last upload attempt timestamp
     */
    public long lastUploadAttempt = 0;

    /**
     * Size of the JSON payload in bytes
     */
    public int payloadSize = 0;
}