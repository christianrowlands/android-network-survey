package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "wifi_survey_records")
public class WifiBeaconRecordEntity extends BaseRecordEntity
{
    @PrimaryKey(autoGenerate = true)
    public long id;

    public boolean beaconDbUploaded = false;

    public String sourceAddress;
    public String destinationAddress;
    public String bssid;

    // Wi-Fi-specific fields (nullable due to protobuf Int32Value and FloatValue)
    public Integer beaconInterval;
    public String serviceSetType;  // Enum stored as string
    public String ssid;
    public String supportedRates;
    public String extendedSupportedRates;
    public String cipherSuites;  // Stored as a comma-separated string
    public String akmSuites;     // Stored as a comma-separated string
    public String encryptionType; // Enum stored as string
    public Boolean wps;
    public Boolean passpoint;
    public String bandwidth;  // Enum stored as string

    public Integer channel;
    public Integer frequencyMhz;
    public Float signalStrength;
    public Float snr;
    public String nodeType;  // Enum stored as string
    public String standard;  // Enum stored as string
}
