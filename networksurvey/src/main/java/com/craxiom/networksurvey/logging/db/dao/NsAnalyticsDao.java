package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

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

    /**
     * Pending records eligible for upload: rows at or under the retry cap. The
     * predicate (retryCount <= max) must stay the mirror image of
     * {@link #deleteFailedRecords(int)}'s (retryCount > max), otherwise rows at
     * exactly the cap would be invisible to upload but never purged.
     */
    @Query("SELECT * FROM ns_analytics_queue WHERE uploaded = 0 AND retryCount <= :maxRetries ORDER BY timestamp ASC LIMIT :limit")
    List<NsAnalyticsQueueEntity> getUploadableRecords(int limit, int maxRetries);

    @Query("SELECT COUNT(*) FROM ns_analytics_queue WHERE uploaded = 0 AND retryCount <= :maxRetries")
    int getUploadableRecordCount(int maxRetries);

    @Query("SELECT COUNT(*) FROM ns_analytics_queue WHERE uploaded = 0")
    int getPendingRecordCount();

    @Query("UPDATE ns_analytics_queue SET uploaded = 1, lastUploadAttempt = :timestamp WHERE id IN (:ids)")
    void markAsUploaded(List<Long> ids, long timestamp);

    @Query("UPDATE ns_analytics_queue SET retryCount = retryCount + 1, lastUploadAttempt = :timestamp WHERE id IN (:ids)")
    void incrementRetryCount(List<Long> ids, long timestamp);

    @Query("DELETE FROM ns_analytics_queue WHERE uploaded = 1 AND timestamp < :before")
    void cleanupOldUploadedRecords(long before);

    /**
     * Purges records that were definitively rejected by the server more than
     * maxRetries times. Returns the number of rows deleted.
     */
    @Query("DELETE FROM ns_analytics_queue WHERE retryCount > :maxRetries")
    int deleteFailedRecords(int maxRetries);

    @Query("DELETE FROM ns_analytics_queue")
    void clearQueue();

    // Connection operations

    @Query("UPDATE ns_analytics_connection SET lastUploadAt = :timestamp, totalRecordsUploaded = totalRecordsUploaded + :recordCount WHERE workspaceId = :workspaceId")
    void updateUploadStats(String workspaceId, long timestamp, int recordCount);
}