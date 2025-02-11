package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "lte_survey_records")
public class LteRecordEntity extends BaseRecordEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    public boolean ocidUploaded = false;
    public boolean beaconDbUploaded = false;

    public int groupNumber;

    // LTE-specific fields with nullable support
    public Integer mcc;
    public Integer mnc;
    public Integer tac;
    public Integer eci;
    public Integer earfcn;
    public Integer pci;
    public Float rsrp;
    public Float rsrq;
    public Integer ta;
    public Boolean servingCell;
    public String lteBandwidth; // Consider storing as a string or mapping to an enum
    public String provider;
    public Float signalStrength;
    public Integer cqi;
    public Integer slot;
    public Float snr;
}

