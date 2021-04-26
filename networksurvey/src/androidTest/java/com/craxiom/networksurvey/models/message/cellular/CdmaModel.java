package com.craxiom.networksurvey.models.message.cellular;

import mil.nga.sf.Point;

import java.util.Objects;

public class CdmaModel
{
    private final int id;
    private Point geom;
    private long time;
    private int recordNumber;
    private int groupNumber;
    private Boolean servingCell;
    private String provider;
    private int sid;
    private int nid;
    private int bsid;
    private int channel;
    private int pnOffset;
    private float signalStrength;
    private float ecIo;
    private double baseLatitude;
    private double baseLongitude;

    private CdmaModel(int id, Point geom, long time, int recordNumber, int groupNumber, Boolean servingCell, String provider, int sid, int nid, int bsid, int channel, int pnOffset, float signalStrength, float ecIo, double baseLatitude, double baseLongitude)
    {
        this.id = id;
        this.geom = geom;
        this.time = time;
        this.recordNumber = recordNumber;
        this.groupNumber = groupNumber;
        this.servingCell = servingCell;
        this.provider = provider;
        this.sid = sid;
        this.nid = nid;
        this.bsid = bsid;
        this.channel = channel;
        this.pnOffset = pnOffset;
        this.signalStrength = signalStrength;
        this.ecIo = ecIo;
        this.baseLatitude = baseLatitude;
        this.baseLongitude = baseLongitude;
    }

    public Point getGeom()
    {
        return geom;
    }

    public void setGeom(Point geom)
    {
        this.geom = geom;
    }

    public long getTime()
    {
        return time;
    }

    public void setTime(long time)
    {
        this.time = time;
    }

    public int getRecordNumber()
    {
        return recordNumber;
    }

    public void setRecordNumber(int recordNumber)
    {
        this.recordNumber = recordNumber;
    }

    public int getGroupNumber()
    {
        return groupNumber;
    }

    public void setGroupNumber(int groupNumber)
    {
        this.groupNumber = groupNumber;
    }

    public Boolean getServingCell()
    {
        return servingCell;
    }

    public void setServingCell(Boolean servingCell)
    {
        this.servingCell = servingCell;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public int getSid()
    {
        return sid;
    }

    public void setSid(int sid)
    {
        this.sid = sid;
    }

    public int getNid()
    {
        return nid;
    }

    public void setNid(int nid)
    {
        this.nid = nid;
    }

    public int getBsid()
    {
        return bsid;
    }

    public void setBsid(int bsid)
    {
        this.bsid = bsid;
    }

    public int getChannel()
    {
        return channel;
    }

    public void setChannel(int channel)
    {
        this.channel = channel;
    }

    public int getPnOffset()
    {
        return pnOffset;
    }

    public void setPnOffset(int pnOffset)
    {
        this.pnOffset = pnOffset;
    }

    public float getSignalStrength()
    {
        return signalStrength;
    }

    public void setSignalStrength(float signalStrength)
    {
        this.signalStrength = signalStrength;
    }

    public float getEcIo()
    {
        return ecIo;
    }

    public void setEcIo(float ecIo)
    {
        this.ecIo = ecIo;
    }

    public double getBaseLatitude()
    {
        return baseLatitude;
    }

    public void setBaseLatitude(double baseLatitude)
    {
        this.baseLatitude = baseLatitude;
    }

    public double getBaseLongitude()
    {
        return baseLongitude;
    }

    public void setBaseLongitude(double baseLongitude)
    {
        this.baseLongitude = baseLongitude;
    }

    @Override
    public String toString()
    {
        return "CdmaModel{" +
                "id=" + id +
                ", geom=" + geom +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", groupNumber=" + groupNumber +
                ", servingCell=" + servingCell +
                ", provider='" + provider + '\'' +
                ", sid=" + sid +
                ", nid=" + nid +
                ", bsid=" + bsid +
                ", channel=" + channel +
                ", pnOffset=" + pnOffset +
                ", signalStrength=" + signalStrength +
                ", ecIo=" + ecIo +
                ", baseLatitude=" + baseLatitude +
                ", baseLongitude=" + baseLongitude +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CdmaModel cdmaModel = (CdmaModel) o;
        return id == cdmaModel.id &&
                time == cdmaModel.time &&
                recordNumber == cdmaModel.recordNumber &&
                groupNumber == cdmaModel.groupNumber &&
                sid == cdmaModel.sid &&
                nid == cdmaModel.nid &&
                bsid == cdmaModel.bsid &&
                channel == cdmaModel.channel &&
                pnOffset == cdmaModel.pnOffset &&
                Float.compare(cdmaModel.signalStrength, signalStrength) == 0 &&
                Float.compare(cdmaModel.ecIo, ecIo) == 0 &&
                Double.compare(cdmaModel.baseLatitude, baseLatitude) == 0 &&
                Double.compare(cdmaModel.baseLongitude, baseLongitude) == 0 &&
                Objects.equals(geom, cdmaModel.geom) &&
                Objects.equals(servingCell, cdmaModel.servingCell) &&
                Objects.equals(provider, cdmaModel.provider);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, groupNumber, servingCell, provider, sid, nid, bsid, channel, pnOffset, signalStrength, ecIo, baseLatitude, baseLongitude);
    }

    public static class CdmaModelBuilder
    {

        private int id;
        private Point geom;
        private long time;
        private int recordNumber;
        private int groupNumber;
        private Boolean servingCell;
        private String provider;
        private int sid;
        private int nid;
        private int bsid;
        private int channel;
        private int pnOffset;
        private float signalStrength;
        private float ecIo;
        private double baseLatitude;
        private double baseLongitude;

        public CdmaModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public CdmaModelBuilder setGeom(Point geom)
        {
            this.geom = geom;
            return this;
        }

        public CdmaModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public CdmaModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public CdmaModelBuilder setGroupNumber(int groupNumber)
        {
            this.groupNumber = groupNumber;
            return this;
        }

        public CdmaModelBuilder setServingCell(Boolean servingCell)
        {
            this.servingCell = servingCell;
            return this;
        }

        public CdmaModelBuilder setProvider(String provider)
        {
            this.provider = provider;
            return this;
        }

        public CdmaModelBuilder setSid(int sid)
        {
            this.sid = sid;
            return this;
        }

        public CdmaModelBuilder setNid(int nid)
        {
            this.nid = nid;
            return this;
        }

        public CdmaModelBuilder setBsid(int bsid)
        {
            this.bsid = bsid;
            return this;
        }

        public CdmaModelBuilder setChannel(int channel)
        {
            this.channel = channel;
            return this;
        }

        public CdmaModelBuilder setPnOffset(int pnOffset)
        {
            this.pnOffset = pnOffset;
            return this;
        }

        public CdmaModelBuilder setSignalStrength(float signalStrength)
        {
            this.signalStrength = signalStrength;
            return this;
        }

        public CdmaModelBuilder setEcIo(float ecIo)
        {
            this.ecIo = ecIo;
            return this;
        }

        public CdmaModelBuilder setBaseLatitude(double baseLatitude)
        {
            this.baseLatitude = baseLatitude;
            return this;
        }

        public CdmaModelBuilder setBaseLongitude(double baseLongitude)
        {
            this.baseLongitude = baseLongitude;
            return this;
        }

        public CdmaModel createCdmaModel()
        {
            return new CdmaModel(id, geom, time, recordNumber, groupNumber, servingCell, provider, sid, nid, bsid, channel, pnOffset, signalStrength, ecIo, baseLatitude, baseLongitude);
        }
    }
}
