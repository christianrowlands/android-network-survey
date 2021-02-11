package com.craxiom.networksurvey.models.message.cellular;

import mil.nga.sf.Point;

import java.util.Objects;

public class UmtsModel
{
    private int id;
    private Point geom;
    private long time;
    private int recordNumber;
    private int groupNumber;
    private Boolean servingCell;
    private String provider;
    private int mcc;
    private int mnc;
    private int lac;
    private int cellId;
    private int uarfcn;
    private int psc;
    private float signalStrength;
    private float rscp;

    private UmtsModel(int id, Point geom, long time, int recordNumber, int groupNumber, Boolean servingCell, String provider, int mcc, int mnc, int lac, int cellId, int uarfcn, int psc, float signalStrength, float rscp)
    {
        this.id = id;
        this.geom = geom;
        this.time = time;
        this.recordNumber = recordNumber;
        this.groupNumber = groupNumber;
        this.servingCell = servingCell;
        this.provider = provider;
        this.mcc = mcc;
        this.mnc = mnc;
        this.lac = lac;
        this.cellId = cellId;
        this.uarfcn = uarfcn;
        this.psc = psc;
        this.signalStrength = signalStrength;
        this.rscp = rscp;
    }

    @Override
    public String toString()
    {
        return "UmtsModel{" +
                "id=" + id +
                ", geom=" + geom +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", groupNumber=" + groupNumber +
                ", servingCell=" + servingCell +
                ", provider='" + provider + '\'' +
                ", mcc=" + mcc +
                ", mnc=" + mnc +
                ", lac=" + lac +
                ", cellId=" + cellId +
                ", uarfcn=" + uarfcn +
                ", psc=" + psc +
                ", signalStrength=" + signalStrength +
                ", rscp=" + rscp +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UmtsModel umtsModel = (UmtsModel) o;
        return id == umtsModel.id &&
                time == umtsModel.time &&
                recordNumber == umtsModel.recordNumber &&
                groupNumber == umtsModel.groupNumber &&
                mcc == umtsModel.mcc &&
                mnc == umtsModel.mnc &&
                lac == umtsModel.lac &&
                cellId == umtsModel.cellId &&
                uarfcn == umtsModel.uarfcn &&
                psc == umtsModel.psc &&
                Float.compare(umtsModel.signalStrength, signalStrength) == 0 &&
                Float.compare(umtsModel.rscp, rscp) == 0 &&
                Objects.equals(geom, umtsModel.geom) &&
                Objects.equals(servingCell, umtsModel.servingCell) &&
                Objects.equals(provider, umtsModel.provider);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, lac, cellId, uarfcn, psc, signalStrength, rscp);
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
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

    public int getMcc()
    {
        return mcc;
    }

    public void setMcc(int mcc)
    {
        this.mcc = mcc;
    }

    public int getMnc()
    {
        return mnc;
    }

    public void setMnc(int mnc)
    {
        this.mnc = mnc;
    }

    public int getLac()
    {
        return lac;
    }

    public void setLac(int lac)
    {
        this.lac = lac;
    }

    public int getCellId()
    {
        return cellId;
    }

    public void setCellId(int cellId)
    {
        this.cellId = cellId;
    }

    public int getUarfcn()
    {
        return uarfcn;
    }

    public void setUarfcn(int uarfcn)
    {
        this.uarfcn = uarfcn;
    }

    public int getPsc()
    {
        return psc;
    }

    public void setPsc(int psc)
    {
        this.psc = psc;
    }

    public float getSignalStrength()
    {
        return signalStrength;
    }

    public void setSignalStrength(float signalStrength)
    {
        this.signalStrength = signalStrength;
    }

    public float getRscp()
    {
        return rscp;
    }

    public void setRscp(float rscp)
    {
        this.rscp = rscp;
    }

    public static class UmtsModelBuilder
    {

        private int id;
        private Point geom;
        private long time;
        private int recordNumber;
        private int groupNumber;
        private Boolean servingCell;
        private String provider;
        private int mcc;
        private int mnc;
        private int lac;
        private int cellId;
        private int uarfcn;
        private int psc;
        private float signalStrength;
        private float rscp;

        public UmtsModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public UmtsModelBuilder setGeom(Point geom)
        {
            this.geom = geom;
            return this;
        }

        public UmtsModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public UmtsModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public UmtsModelBuilder setGroupNumber(int groupNumber)
        {
            this.groupNumber = groupNumber;
            return this;
        }

        public UmtsModelBuilder setServingCell(Boolean servingCell)
        {
            this.servingCell = servingCell;
            return this;
        }

        public UmtsModelBuilder setProvider(String provider)
        {
            this.provider = provider;
            return this;
        }

        public UmtsModelBuilder setMcc(int mcc)
        {
            this.mcc = mcc;
            return this;
        }

        public UmtsModelBuilder setMnc(int mnc)
        {
            this.mnc = mnc;
            return this;
        }

        public UmtsModelBuilder setLac(int lac)
        {
            this.lac = lac;
            return this;
        }

        public UmtsModelBuilder setCellId(int cellId)
        {
            this.cellId = cellId;
            return this;
        }

        public UmtsModelBuilder setUarfcn(int uarfcn)
        {
            this.uarfcn = uarfcn;
            return this;
        }

        public UmtsModelBuilder setPsc(int psc)
        {
            this.psc = psc;
            return this;
        }

        public UmtsModelBuilder setSignalStrength(float signalStrength)
        {
            this.signalStrength = signalStrength;
            return this;
        }

        public UmtsModelBuilder setRscp(float rscp)
        {
            this.rscp = rscp;
            return this;
        }

        public UmtsModel createUmtsModel()
        {
            return new UmtsModel(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, lac, cellId, uarfcn, psc, signalStrength, rscp);
        }
    }
}
