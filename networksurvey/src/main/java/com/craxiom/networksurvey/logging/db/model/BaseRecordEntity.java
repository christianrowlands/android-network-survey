package com.craxiom.networksurvey.logging.db.model;

import androidx.annotation.NonNull;

public class BaseRecordEntity
{
    @NonNull
    public String deviceSerialNumber;
    @NonNull
    public String deviceName;
    @NonNull
    public String deviceTime;
    public double latitude;
    public double longitude;
    public float altitude;
    public String missionId;
    public int recordNumber;
    public int accuracy;
    public int locationAge;
    public Float speed;
}
