package com.craxiom.networksurvey.logging.db.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.craxiom.networksurvey.logging.db.model.NsAnalyticsConnectionEntity;
import com.craxiom.networksurvey.logging.db.model.NsAnalyticsQueueEntity;

import java.util.List;

/**
 * Data Access Object for NS Analytics database operations.
 */
@Dao
public interface NsAnalyticsDao
{
    // Queue operations

    @Insert
    void insertRecord(NsAnalyticsQueueEntity record);

    @Insert
    void insertRecords(List<NsAnalyticsQueueEntity> records);

    @Query("SELECT * FROM ns_analytics_queue WHERE uploaded = 0 ORDER BY timestamp ASC LIMIT :limit")
    @NonNull
    List<NsAnalyticsQueueEntity> getPendingRecords(int limit);

    @Query("SELECT * FROM ns_analytics_queue WHERE uploaded = 0 AND recordType = :type ORDER BY timestamp ASC LIMIT :limit")
    @NonNull
    List<NsAnalyticsQueueEntity> getPendingRecordsByType(String type, int limit);

    @Query("SELECT COUNT(*) FROM ns_analytics_queue WHERE uploaded = 0")
    int getPendingRecordCount();

    @Query("SELECT COUNT(*) FROM ns_analytics_queue WHERE uploaded = 0 AND recordType = :type")
    int getPendingRecordCountByType(String type);

    @Query("UPDATE ns_analytics_queue SET uploaded = 1, lastUploadAttempt = :timestamp WHERE id IN (:ids)")
    void markAsUploaded(List<Long> ids, long timestamp);

    @Query("UPDATE ns_analytics_queue SET retryCount = retryCount + 1, lastUploadAttempt = :timestamp WHERE id IN (:ids)")
    void incrementRetryCount(List<Long> ids, long timestamp);

    @Query("DELETE FROM ns_analytics_queue WHERE uploaded = 1 AND timestamp < :before")
    void cleanupOldUploadedRecords(long before);

    @Query("DELETE FROM ns_analytics_queue WHERE retryCount > :maxRetries")
    void deleteFailedRecords(int maxRetries);

    @Query("DELETE FROM ns_analytics_queue")
    void clearQueue();

    @Query("SELECT SUM(payloadSize) FROM ns_analytics_queue WHERE uploaded = 0")
    Long getTotalPendingPayloadSize();

    // Connection operations

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertConnection(NsAnalyticsConnectionEntity connection);

    @Query("SELECT * FROM ns_analytics_connection WHERE isActive = 1 LIMIT 1")
    NsAnalyticsConnectionEntity getActiveConnection();

    @Query("SELECT * FROM ns_analytics_connection WHERE workspaceId = :workspaceId")
    NsAnalyticsConnectionEntity getConnection(String workspaceId);

    @Query("UPDATE ns_analytics_connection SET lastUploadAt = :timestamp, totalRecordsUploaded = totalRecordsUploaded + :recordCount WHERE workspaceId = :workspaceId")
    void updateUploadStats(String workspaceId, long timestamp, int recordCount);

    @Query("UPDATE ns_analytics_connection SET isActive = 0 WHERE workspaceId = :workspaceId")
    void deactivateConnection(String workspaceId);

    @Query("UPDATE ns_analytics_connection SET uploadFrequencyMinutes = :minutes WHERE workspaceId = :workspaceId")
    void updateUploadFrequency(String workspaceId, int minutes);

    @Query("DELETE FROM ns_analytics_connection WHERE workspaceId = :workspaceId")
    void deleteConnection(String workspaceId);

    @Query("DELETE FROM ns_analytics_connection")
    void clearConnections();

    // Batch operations

    @Transaction
    @Query("SELECT DISTINCT batchId FROM ns_analytics_queue WHERE uploaded = 0 ORDER BY timestamp ASC LIMIT 1")
    String getNextBatchId();

    @Query("SELECT * FROM ns_analytics_queue WHERE batchId = :batchId AND uploaded = 0")
    @NonNull
    List<NsAnalyticsQueueEntity> getRecordsByBatchId(String batchId);

    // Statistics

    @Query("SELECT recordType, COUNT(*) as count FROM ns_analytics_queue WHERE uploaded = 0 GROUP BY recordType")
    @NonNull
    List<RecordTypeCount> getPendingRecordStats();

    /**
     * Helper class for record type statistics
     */
    class RecordTypeCount
    {
        public String recordType;
        public int count;
    }
}