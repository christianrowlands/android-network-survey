package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "nr_survey_records")
public class NrRecordEntity extends BaseRecordEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    public boolean ocidUploaded = false;
    public boolean beaconDbUploaded = false;

    public int groupNumber;

    // NR specific fields (nullable due to protobuf Int32Value and FloatValue)
    public Integer mcc;
    public Integer mnc;
    public Integer tac;
    public Long nci;
    public Integer narfcn;
    public Integer pci;
    public Float ssRsrp;
    public Float ssRsrq;
    public Float ssSinr;
    public Float csiRsrp;
    public Float csiRsrq;
    public Float csiSinr;
    public Integer ta;
    public Boolean servingCell;
    public String provider;
    public Integer slot;
}

