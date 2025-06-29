package com.craxiom.networksurvey.listeners;

/**
 * Listener interface for tracking the count of records that pass upload filters
 * and are written to the upload database.
 * <p>
 * This is used to provide accurate counts in the Survey Monitor screen, showing
 * both total records processed and records eligible for upload.
 */
public interface IUploadRecordCountListener
{
    /**
     * Called when cellular records have been filtered and written to the upload database.
     *
     * @param recordCount The number of cellular records that passed all filters and were written
     */
    void onCellularUploadRecordsWritten(int recordCount);

    /**
     * Called when Wi-Fi records have been filtered and written to the upload database.
     *
     * @param recordCount The number of Wi-Fi records that passed all filters and were written
     */
    void onWifiUploadRecordsWritten(int recordCount);
}