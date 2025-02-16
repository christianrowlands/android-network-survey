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
    // GSM
    @Query("SELECT COUNT(*) FROM gsm_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0")
    int getGsmRecordCountForUpload();

    @Query("SELECT * FROM gsm_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0 LIMIT :limit")
    List<GsmRecordEntity> getGsmRecordsForUpload(int limit);

    @Query("UPDATE gsm_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markGsmRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE gsm_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markGsmRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // CDMA
    @Query("SELECT COUNT(*) FROM cdma_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0")
    int getCdmaRecordCountForUpload();

    @Query("SELECT * FROM cdma_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0 LIMIT :limit")
    List<CdmaRecordEntity> getCdmaRecordsForUpload(int limit);

    @Query("UPDATE cdma_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markCdmaRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE cdma_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markCdmaRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // UMTS
    @Query("SELECT COUNT(*) FROM umts_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0")
    int getUmtsRecordCountForUpload();

    @Query("SELECT * FROM umts_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0 LIMIT :limit")
    List<UmtsRecordEntity> getUmtsRecordsForUpload(int limit);

    @Query("UPDATE umts_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markUmtsRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE umts_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markUmtsRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // LTE
    @Query("SELECT COUNT(*) FROM lte_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0")
    int getLteRecordCountForUpload();

    @Query("SELECT * FROM lte_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0 LIMIT :limit")
    List<LteRecordEntity> getLteRecordsForUpload(int limit);

    @Query("UPDATE lte_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markLteRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE lte_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markLteRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // NR (5G)
    @Query("SELECT COUNT(*) FROM nr_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0")
    int getNrRecordCountForUpload();

    @Query("SELECT * FROM nr_survey_records WHERE ocidUploaded = 0 OR beaconDbUploaded = 0 LIMIT :limit")
    List<NrRecordEntity> getNrRecordsForUpload(int limit);

    @Query("UPDATE nr_survey_records SET ocidUploaded = 1 WHERE id IN (:recordIds)")
    void markNrRecordsAsUploadedToOcid(List<Long> recordIds);

    @Query("UPDATE nr_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markNrRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    // Wifi
    @Query("SELECT COUNT(*) FROM wifi_survey_records WHERE beaconDbUploaded = 0")
    int getWifiRecordCountForUpload();

    @Query("SELECT * FROM wifi_survey_records WHERE beaconDbUploaded = 0 LIMIT :limit")
    List<WifiBeaconRecordEntity> getWifiRecordsForUpload(int limit);

    @Query("UPDATE wifi_survey_records SET beaconDbUploaded = 1 WHERE id IN (:recordIds)")
    void markWifiRecordsAsUploadedToBeaconDb(List<Long> recordIds);

    @Query("DELETE FROM gsm_survey_records WHERE ocidUploaded = 1 OR beaconDbUploaded = 1")
    void deleteUploadedGsmRecords();

    @Query("DELETE FROM cdma_survey_records WHERE ocidUploaded = 1 OR beaconDbUploaded = 1")
    void deleteUploadedCdmaRecords();

    @Query("DELETE FROM umts_survey_records WHERE ocidUploaded = 1 OR beaconDbUploaded = 1")
    void deleteUploadedUmtsRecords();

    @Query("DELETE FROM lte_survey_records WHERE ocidUploaded = 1 OR beaconDbUploaded = 1")
    void deleteUploadedLteRecords();

    @Query("DELETE FROM nr_survey_records WHERE ocidUploaded = 1 OR beaconDbUploaded = 1")
    void deleteUploadedNrRecords();

    @Query("DELETE FROM wifi_survey_records WHERE beaconDbUploaded = 1")
    void deleteUploadedWifiRecords();

    @Transaction
    default void deleteAllUploadedRecords()
    {
        deleteUploadedGsmRecords();
        deleteUploadedCdmaRecords();
        deleteUploadedUmtsRecords();
        deleteUploadedLteRecords();
        deleteUploadedNrRecords();
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

    @Query("SELECT COUNT(*) FROM gsm_survey_records")
    int getGsmRecordCount();

    @Query("SELECT COUNT(*) FROM cdma_survey_records")
    int getCdmaRecordCount();

    @Query("SELECT COUNT(*) FROM umts_survey_records")
    int getUmtsRecordCount();

    @Query("SELECT COUNT(*) FROM lte_survey_records")
    int getLteRecordCount();

    @Query("SELECT COUNT(*) FROM nr_survey_records")
    int getNrRecordCount();

    @Query("SELECT COUNT(*) FROM wifi_survey_records")
    int getWifiRecordCount();

    @Transaction
    default int getTotalRecordCount()
    {
        return getGsmRecordCount() + getCdmaRecordCount() + getUmtsRecordCount() + getLteRecordCount() + getNrRecordCount() + getWifiRecordCount();
    }
}
