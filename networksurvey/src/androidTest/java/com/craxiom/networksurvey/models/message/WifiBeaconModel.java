package com.craxiom.networksurvey.models.message;

import java.util.Objects;

public class WifiBeaconModel
{
    private int id;
    private String geom;
    private long time;
    private int recordNumber;
    private String bssid;
    private String ssid;
    private int channel;
    private int frequency;
    private String cipherSuites;
    private String akmSuites;
    private String encryptionType;
    private Boolean wps;
    private Float signalStrength;

    public int getId()
    {
        return id;
    }

    public String getGeom()
    {
        return geom;
    }

    public long getTime()
    {
        return time;
    }

    public int getRecordNumber()
    {
        return recordNumber;
    }

    public String getBssid()
    {
        return bssid;
    }

    public String getSsid()
    {
        return ssid;
    }

    public int getChannel()
    {
        return channel;
    }

    public int getFrequency()
    {
        return frequency;
    }

    public String getCipherSuites()
    {
        return cipherSuites;
    }

    public String getAkmSuites()
    {
        return akmSuites;
    }

    public String getEncryptionType()
    {
        return encryptionType;
    }

    public Boolean getWps()
    {
        return wps;
    }

    public Float getSignalStrength()
    {
        return signalStrength;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WifiBeaconModel that = (WifiBeaconModel) o;
        return id == that.id &&
                time == that.time &&
                recordNumber == that.recordNumber &&
                channel == that.channel &&
                frequency == that.frequency &&
                Objects.equals(geom, that.geom) &&
                Objects.equals(bssid, that.bssid) &&
                Objects.equals(ssid, that.ssid) &&
                Objects.equals(cipherSuites, that.cipherSuites) &&
                Objects.equals(akmSuites, that.akmSuites) &&
                Objects.equals(encryptionType, that.encryptionType) &&
                Objects.equals(wps, that.wps) &&
                Objects.equals(signalStrength, that.signalStrength);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, bssid, ssid, channel, frequency, cipherSuites, akmSuites, encryptionType, wps, signalStrength);
    }

    @Override
    public String toString()
    {
        return "WifiBeaconModel{" +
                "id=" + id +
                ", geom='" + geom + '\'' +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", bssid='" + bssid + '\'' +
                ", ssid='" + ssid + '\'' +
                ", channel=" + channel +
                ", frequency=" + frequency +
                ", cipherSuites='" + cipherSuites + '\'' +
                ", akmSuites='" + akmSuites + '\'' +
                ", encryptionType='" + encryptionType + '\'' +
                ", wps=" + wps +
                ", signalStrength=" + signalStrength +
                '}';
    }

    public static final class WifiBeaconModelBuilder
    {
        private int id;
        private String geom;
        private long time;
        private int recordNumber;
        private String bssid;
        private String ssid;
        private int channel;
        private int frequency;
        private String cipherSuites;
        private String akmSuites;
        private String encryptionType;
        private Boolean wps;
        private Float signalStrength;

        public WifiBeaconModelBuilder()
        {
        }

        public static WifiBeaconModelBuilder aWifiBeaconModel()
        {
            return new WifiBeaconModelBuilder();
        }

        public int getId()
        {
            return id;
        }

        public WifiBeaconModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public String getGeom()
        {
            return geom;
        }

        public WifiBeaconModelBuilder setGeom(String geom)
        {
            this.geom = geom;
            return this;
        }

        public long getTime()
        {
            return time;
        }

        public WifiBeaconModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public int getRecordNumber()
        {
            return recordNumber;
        }

        public WifiBeaconModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public String getBssid()
        {
            return bssid;
        }

        public WifiBeaconModelBuilder setBssid(String bssid)
        {
            this.bssid = bssid;
            return this;
        }

        public String getSsid()
        {
            return ssid;
        }

        public WifiBeaconModelBuilder setSsid(String ssid)
        {
            this.ssid = ssid;
            return this;
        }

        public int getChannel()
        {
            return channel;
        }

        public WifiBeaconModelBuilder setChannel(int channel)
        {
            this.channel = channel;
            return this;
        }

        public int getFrequency()
        {
            return frequency;
        }

        public WifiBeaconModelBuilder setFrequency(int frequency)
        {
            this.frequency = frequency;
            return this;
        }

        public String getCipherSuites()
        {
            return cipherSuites;
        }

        public WifiBeaconModelBuilder setCipherSuites(String cipherSuites)
        {
            this.cipherSuites = cipherSuites;
            return this;
        }

        public String getAkmSuites()
        {
            return akmSuites;
        }

        public WifiBeaconModelBuilder setAkmSuites(String akmSuites)
        {
            this.akmSuites = akmSuites;
            return this;
        }

        public String getEncryptionType()
        {
            return encryptionType;
        }

        public WifiBeaconModelBuilder setEncryptionType(String encryptionType)
        {
            this.encryptionType = encryptionType;
            return this;
        }

        public Boolean getWps()
        {
            return wps;
        }

        public WifiBeaconModelBuilder setWps(Boolean wps)
        {
            this.wps = wps;
            return this;
        }

        public Float getSignalStrength()
        {
            return signalStrength;
        }

        public WifiBeaconModelBuilder setSignalStrength(Float signalStrength)
        {
            this.signalStrength = signalStrength;
            return this;
        }

        public WifiBeaconModel build()
        {
            WifiBeaconModel WifiBeaconModel = new WifiBeaconModel();
            WifiBeaconModel.bssid = this.bssid;
            WifiBeaconModel.cipherSuites = this.cipherSuites;
            WifiBeaconModel.id = this.id;
            WifiBeaconModel.recordNumber = this.recordNumber;
            WifiBeaconModel.geom = this.geom;
            WifiBeaconModel.time = this.time;
            WifiBeaconModel.channel = this.channel;
            WifiBeaconModel.ssid = this.ssid;
            WifiBeaconModel.akmSuites = this.akmSuites;
            WifiBeaconModel.frequency = this.frequency;
            WifiBeaconModel.encryptionType = this.encryptionType;
            WifiBeaconModel.wps = this.wps;
            WifiBeaconModel.signalStrength = this.signalStrength;
            return WifiBeaconModel;
        }
    }
}
