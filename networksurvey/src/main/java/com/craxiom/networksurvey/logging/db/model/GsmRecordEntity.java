package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "gsm_survey_records")
public class GsmRecordEntity extends BaseRecordEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    public boolean ocidUploaded = false;
    public boolean beaconDbUploaded = false;

    public int groupNumber;

    // GSM-specific fields (nullable due to protobuf Int32Value and FloatValue)
    public Integer mcc;
    public Integer mnc;
    public Integer lac;
    public Integer ci;
    public Integer arfcn;
    public Integer bsic;
    public Float signalStrength;
    public Integer ta;
    public Boolean servingCell;
    public String provider;
    public Integer slot;
}
