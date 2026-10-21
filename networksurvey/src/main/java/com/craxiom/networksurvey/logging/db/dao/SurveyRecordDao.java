package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

import java.util.List;

@Dao
public interface SurveyRecordDao
{
    /**
     * A Cellular record still waiting for one of the targets in play ({@code :ocid} and
     * {@code :beaconDb} say which are), so a target that already took it, or isn't uploading, never
     * gets it again.
     */
    String WAITING_FOR_A_TARGET = "(:ocid AND ocidUploaded = 0) OR (:beaconDb AND beaconDbUploaded = 0)";

    /** A Cellular record every target in play has received. */
    String UPLOADED_TO_EVERY_TARGET = "(ocidUploaded = 1 OR NOT :ocid) AND (beaconDbUploaded = 1 OR NOT :beaconDb)";

    // GSM
    @Query("SELECT COUNT(*) FROM gsm_survey_records WHERE " + WAITING_FOR_A_TARGET)
    int getGsmRecordCountForUpload(boolean ocid, boolean beaconDb);

    @Query("SELECT * FROM gsm_survey_records WHERE " + WAITING_FOR_A_TARGET + " LIMIT :limit")
    List<GsmRecordEntity> getGsmRecordsForUpload(boolean ocid, boolean beaconDb, int limit);

    @Query("UPDATE gsm_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markGsmRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE gsm_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markGsmRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // CDMA
    @Query("SELECT COUNT(*) FROM cdma_survey_records WHERE " + WAITING_FOR_A_TARGET)
    int getCdmaRecordCountForUpload(boolean ocid, boolean beaconDb);

    @Query("SELECT * FROM cdma_survey_records WHERE " + WAITING_FOR_A_TARGET + " LIMIT :limit")
    List<CdmaRecordEntity> getCdmaRecordsForUpload(boolean ocid, boolean beaconDb, int limit);

    @Query("UPDATE cdma_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markCdmaRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE cdma_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markCdmaRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // UMTS
    @Query("SELECT COUNT(*) FROM umts_survey_records WHERE " + WAITING_FOR_A_TARGET)
    int getUmtsRecordCountForUpload(boolean ocid, boolean beaconDb);

    @Query("SELECT * FROM umts_survey_records WHERE " + WAITING_FOR_A_TARGET + " LIMIT :limit")
    List<UmtsRecordEntity> getUmtsRecordsForUpload(boolean ocid, boolean beaconDb, int limit);

    @Query("UPDATE umts_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markUmtsRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE umts_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markUmtsRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // LTE
    @Query("SELECT COUNT(*) FROM lte_survey_records WHERE " + WAITING_FOR_A_TARGET)
    int getLteRecordCountForUpload(boolean ocid, boolean beaconDb);

    @Query("SELECT * FROM lte_survey_records WHERE " + WAITING_FOR_A_TARGET + " LIMIT :limit")
    List<LteRecordEntity> getLteRecordsForUpload(boolean ocid, boolean beaconDb, int limit);

    @Query("UPDATE lte_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markLteRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE lte_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markLteRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // NR (5G)
    @Query("SELECT COUNT(*) FROM nr_survey_records WHERE " + WAITING_FOR_A_TARGET)
    int getNrRecordCountForUpload(boolean ocid, boolean beaconDb);

    @Query("SELECT * FROM nr_survey_records WHERE " + WAITING_FOR_A_TARGET + " LIMIT :limit")
    List<NrRecordEntity> getNrRecordsForUpload(boolean ocid, boolean beaconDb, int limit);

    @Query("UPDATE nr_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markNrRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE nr_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markNrRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // Wifi
    @Query("SELECT COUNT(*) FROM wifi_survey_records WHERE :beaconDb AND beaconDbUploaded = 0")
    int getWifiRecordCountForUpload(boolean beaconDb);

    @Query("SELECT * FROM wifi_survey_records WHERE :beaconDb AND beaconDbUploaded = 0 LIMIT :limit")
    List<WifiBeaconRecordEntity> getWifiRecordsForUpload(boolean beaconDb, int limit);

    @Query("UPDATE wifi_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markWifiRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    @Query("DELETE FROM gsm_survey_records WHERE " + UPLOADED_TO_EVERY_TARGET)
    void deleteUploadedGsmRecords(boolean ocid, boolean beaconDb);

    @Query("DELETE FROM cdma_survey_records WHERE " + UPLOADED_TO_EVERY_TARGET)
    void deleteUploadedCdmaRecords(boolean ocid, boolean beaconDb);

    @Query("DELETE FROM umts_survey_records WHERE " + UPLOADED_TO_EVERY_TARGET)
    void deleteUploadedUmtsRecords(boolean ocid, boolean beaconDb);

    @Query("DELETE FROM lte_survey_records WHERE " + UPLOADED_TO_EVERY_TARGET)
    void deleteUploadedLteRecords(boolean ocid, boolean beaconDb);

    @Query("DELETE FROM nr_survey_records WHERE " + UPLOADED_TO_EVERY_TARGET)
    void deleteUploadedNrRecords(boolean ocid, boolean beaconDb);

    @Query("DELETE FROM wifi_survey_records WHERE beaconDbUploaded = 1")
    void deleteUploadedWifiRecords();

    /**
     * Deletes the records every target in play has received. A Cellular record OpenCelliD took but
     * BeaconDB still waits for (or the other way round) stays until both have it.
     *
     * @param ocid     True if OpenCelliD is a target, so a Cellular record must have reached it.
     * @param beaconDb True if BeaconDB is a target, so a Cellular record must have reached it.
     */
    @Transaction
    default void deleteAllUploadedRecords(boolean ocid, boolean beaconDb)
    {
        // With no target in play every record would count as uploaded to all of them
        if (!ocid && !beaconDb) return;
        deleteUploadedGsmRecords(ocid, beaconDb);
        deleteUploadedCdmaRecords(ocid, beaconDb);
        deleteUploadedUmtsRecords(ocid, beaconDb);
        deleteUploadedLteRecords(ocid, beaconDb);
        deleteUploadedNrRecords(ocid, beaconDb);
        deleteUploadedWifiRecords();
    }

    @Query("DELETE FROM gsm_survey_records")
    void deleteAllGsmRecords();

    @Query("DELETE FROM cdma_survey_records")
    void deleteAllCdmaRecords();

    @Query("DELETE FROM umts_survey_records")
    void deleteAllUmtsRecords();

    @Query("DELETE FROM lte_survey_records")
    void deleteAllLteRecords();

    @Query("DELETE FROM nr_survey_records")
    void deleteAllNrRecords();

    @Query("DELETE FROM wifi_survey_records")
    void deleteAllWifiRecords();

    @Transaction
    default void deleteAllRecords()
    {
        deleteAllGsmRecords();
        deleteAllCdmaRecords();
        deleteAllUmtsRecords();
        deleteAllLteRecords();
        deleteAllNrRecords();
        deleteAllWifiRecords();
    }
}
