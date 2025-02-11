package com.craxiom.networksurvey.logging.db.model;

import java.util.Collections;
import java.util.List;

public record UploadRecordsWrapper(List<GsmRecordEntity> gsmRecords,
                                   List<UmtsRecordEntity> umtsRecords,
                                   List<LteRecordEntity> lteRecords,
                                   List<NrRecordEntity> nrRecords,
                                   List<WifiBeaconRecordEntity> wifiRecords)
{
    /**
     * @noinspection unchecked
     */
    public static UploadRecordsWrapper createRecordsWrapper(List<?> records)
    {
        if (records == null || records.isEmpty())
        {
            return new UploadRecordsWrapper(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        if (records.get(0) instanceof GsmRecordEntity)
        {
            return new UploadRecordsWrapper(
                    (List<GsmRecordEntity>) records,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        } else if (records.get(0) instanceof CdmaRecordEntity)
        {
            return new UploadRecordsWrapper(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        } else if (records.get(0) instanceof UmtsRecordEntity)
        {
            return new UploadRecordsWrapper(
                    Collections.emptyList(),
                    (List<UmtsRecordEntity>) records,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        } else if (records.get(0) instanceof LteRecordEntity)
        {
            return new UploadRecordsWrapper(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    (List<LteRecordEntity>) records,
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        } else if (records.get(0) instanceof NrRecordEntity)
        {
            return new UploadRecordsWrapper(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    (List<NrRecordEntity>) records,
                    Collections.emptyList()
            );
        } else if (records.get(0) instanceof WifiBeaconRecordEntity)
        {
            return new UploadRecordsWrapper(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    (List<WifiBeaconRecordEntity>) records
            );
        }

        throw new IllegalArgumentException("Unsupported record type: " + records.get(0).getClass().getName());
    }
}
