package com.craxiom.networksurvey.models.message.cellular;

import mil.nga.sf.Point;

import java.util.Objects;

public class GsmModel
{

    private final int id;
    private final Point geom;
    private final long time;
    private final int recordNumber;
    private final int groupNumber;
    private final Boolean servingCell;
    private final String provider;
    private final int mcc;
    private final int mnc;
    private final int lac;
    private final int cid;
    private final int arfcn;
    private final int bsic;
    private final float signalStrength;
    private final int ta;

    private GsmModel(int id, Point geom, long time, int recordNumber, int groupNumber, Boolean servingCell, String provider, int mcc, int mnc, int lac, int cid, int arfcn, int bsic, float signalStrength, int ta)
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
        this.cid = cid;
        this.arfcn = arfcn;
        this.bsic = bsic;
        this.signalStrength = signalStrength;
        this.ta = ta;
    }

    @Override
    public String toString()
    {
        return "GsmModel{" +
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
                ", cid=" + cid +
                ", arfcn=" + arfcn +
                ", bsic=" + bsic +
                ", signalStrength=" + signalStrength +
                ", ta=" + ta +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GsmModel gsmModel = (GsmModel) o;
        return id == gsmModel.id &&
                time == gsmModel.time &&
                recordNumber == gsmModel.recordNumber &&
                groupNumber == gsmModel.groupNumber &&
                mcc == gsmModel.mcc &&
                mnc == gsmModel.mnc &&
                lac == gsmModel.lac &&
                cid == gsmModel.cid &&
                arfcn == gsmModel.arfcn &&
                bsic == gsmModel.bsic &&
                Float.compare(gsmModel.signalStrength, signalStrength) == 0 &&
                ta == gsmModel.ta &&
                Objects.equals(geom, gsmModel.geom) &&
                Objects.equals(servingCell, gsmModel.servingCell) &&
                Objects.equals(provider, gsmModel.provider);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, lac, cid, arfcn, bsic, signalStrength, ta);
    }

    public static class GsmModelBuilder
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
        private int cid;
        private int arfcn;
        private int bsic;
        private float signalStrength;
        private int ta;

        public GsmModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public GsmModelBuilder setGeom(Point geom)
        {
            this.geom = geom;
            return this;
        }

        public GsmModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public GsmModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public GsmModelBuilder setGroupNumber(int groupNumber)
        {
            this.groupNumber = groupNumber;
            return this;
        }

        public GsmModelBuilder setServingCell(Boolean servingCell)
        {
            this.servingCell = servingCell;
            return this;
        }

        public GsmModelBuilder setProvider(String provider)
        {
            this.provider = provider;
            return this;
        }

        public GsmModelBuilder setMcc(int mcc)
        {
            this.mcc = mcc;
            return this;
        }

        public GsmModelBuilder setMnc(int mnc)
        {
            this.mnc = mnc;
            return this;
        }

        public GsmModelBuilder setLac(int lac)
        {
            this.lac = lac;
            return this;
        }

        public GsmModelBuilder setCid(int cid)
        {
            this.cid = cid;
            return this;
        }

        public GsmModelBuilder setArfcn(int arfcn)
        {
            this.arfcn = arfcn;
            return this;
        }

        public GsmModelBuilder setBsic(int bsic)
        {
            this.bsic = bsic;
            return this;
        }

        public GsmModelBuilder setSignalStrength(float signalStrength)
        {
            this.signalStrength = signalStrength;
            return this;
        }

        public GsmModelBuilder setTa(int ta)
        {
            this.ta = ta;
            return this;
        }

        public GsmModel build()
        {
            return new GsmModel(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, lac, cid, arfcn, bsic, signalStrength, ta);
        }
    }
}
