package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cdma_survey_records")
public class CdmaRecordEntity extends BaseRecordEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    public boolean ocidUploaded = false;
    public boolean beaconDbUploaded = false;

    public int groupNumber;

    // CDMA-specific fields (nullable due to protobuf Int32Value and FloatValue)
    public Integer sid;
    public Integer nid;
    public Integer zone;
    public Integer bsid;
    public Integer channel;
    public Integer pnOffset;
    public Float signalStrength;
    public Float ecio;
    public Boolean servingCell;
    public String provider;
    public Integer slot;
}
