package com.craxiom.networksurvey.models.message.cellular;

import java.util.Objects;

public class LteModel
{

    private final int id;
    private final String geom;
    private final long time;
    private final int recordNumber;
    private final int groupNumber;
    private final int servingCell;
    private final String provider;
    private final int mcc;
    private final int mnc;
    private final int tac;
    private final int eci;
    private final int dlEarfcn;
    private final int physCellId;
    private final float rsrp;
    private final int ta;
    private final String dlBandwidth;
    private final float rsrq;

    private LteModel(int id, String geom, long time, int recordNumber, int groupNumber, int servingCell, String provider, int mcc, int mnc, int tac, int eci, int dlEarfcn, int physCellId, float rsrp, int ta, String dlBandwidth, float rsrq)
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
        this.tac = tac;
        this.eci = eci;
        this.dlEarfcn = dlEarfcn;
        this.physCellId = physCellId;
        this.rsrp = rsrp;
        this.ta = ta;
        this.dlBandwidth = dlBandwidth;
        this.rsrq = rsrq;
    }

    @Override
    public String toString()
    {
        return "LteModel{" +
                "id=" + id +
                ", geom='" + geom + '\'' +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", groupNumber=" + groupNumber +
                ", servingCell=" + servingCell +
                ", provider='" + provider + '\'' +
                ", mcc=" + mcc +
                ", mnc=" + mnc +
                ", tac=" + tac +
                ", eci=" + eci +
                ", dlEarfcn=" + dlEarfcn +
                ", physCellId=" + physCellId +
                ", rsrp=" + rsrp +
                ", ta=" + ta +
                ", dlBandwidth='" + dlBandwidth + '\'' +
                ", rsrq=" + rsrq +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LteModel lteModel = (LteModel) o;
        return id == lteModel.id &&
                time == lteModel.time &&
                recordNumber == lteModel.recordNumber &&
                groupNumber == lteModel.groupNumber &&
                mcc == lteModel.mcc &&
                mnc == lteModel.mnc &&
                tac == lteModel.tac &&
                eci == lteModel.eci &&
                dlEarfcn == lteModel.dlEarfcn &&
                physCellId == lteModel.physCellId &&
                Float.compare(lteModel.rsrp, rsrp) == 0 &&
                ta == lteModel.ta &&
                Float.compare(lteModel.rsrq, rsrq) == 0 &&
                Objects.equals(geom, lteModel.geom) &&
                Objects.equals(servingCell, lteModel.servingCell) &&
                Objects.equals(provider, lteModel.provider) &&
                Objects.equals(dlBandwidth, lteModel.dlBandwidth);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, tac, eci, dlEarfcn, physCellId, rsrp, ta, dlBandwidth, rsrq);
    }

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

    public int getGroupNumber()
    {
        return groupNumber;
    }

    public int getServingCell()
    {
        return servingCell;
    }

    public String getProvider()
    {
        return provider;
    }

    public int getMcc()
    {
        return mcc;
    }

    public int getMnc()
    {
        return mnc;
    }

    public int getTac()
    {
        return tac;
    }

    public int getEci()
    {
        return eci;
    }

    public int getDlEarfcn()
    {
        return dlEarfcn;
    }

    public int getPhysCellId()
    {
        return physCellId;
    }

    public float getRsrp()
    {
        return rsrp;
    }

    public int getTa()
    {
        return ta;
    }

    public String getDlBandwidth()
    {
        return dlBandwidth;
    }

    public float getRsrq()
    {
        return rsrq;
    }

    public static class LteModelBuilder
    {

        private int id;
        private String geom;
        private long time;
        private int recordNumber;
        private int groupNumber;
        private int servingCell;
        private String provider;
        private int mcc;
        private int mnc;
        private int tac;
        private int eci;
        private int dlEarfcn;
        private int physCellId;
        private float rsrp;
        private int ta;
        private String dlBandwidth;
        private float rsrq;

        public LteModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public LteModelBuilder setGeom(String geom)
        {
            this.geom = geom;
            return this;
        }

        public LteModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public LteModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public LteModelBuilder setGroupNumber(int groupNumber)
        {
            this.groupNumber = groupNumber;
            return this;
        }

        public LteModelBuilder setServingCell(int servingCell)
        {
            this.servingCell = servingCell;
            return this;
        }

        public LteModelBuilder setProvider(String provider)
        {
            this.provider = provider;
            return this;
        }

        public LteModelBuilder setMcc(int mcc)
        {
            this.mcc = mcc;
            return this;
        }

        public LteModelBuilder setMnc(int mnc)
        {
            this.mnc = mnc;
            return this;
        }

        public LteModelBuilder setTac(int tac)
        {
            this.tac = tac;
            return this;
        }

        public LteModelBuilder setEci(int eci)
        {
            this.eci = eci;
            return this;
        }

        public LteModelBuilder setDlEarfcn(int dlEarfcn)
        {
            this.dlEarfcn = dlEarfcn;
            return this;
        }

        public LteModelBuilder setPhysCellId(int physCellId)
        {
            this.physCellId = physCellId;
            return this;
        }

        public LteModelBuilder setRsrp(float rsrp)
        {
            this.rsrp = rsrp;
            return this;
        }

        public LteModelBuilder setTa(int ta)
        {
            this.ta = ta;
            return this;
        }

        public LteModelBuilder setDlBandwidth(String dlBandwidth)
        {
            this.dlBandwidth = dlBandwidth;
            return this;
        }

        public LteModelBuilder setRsrq(float rsrq)
        {
            this.rsrq = rsrq;
            return this;
        }

        public LteModel build()
        {
            return new LteModel(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, tac, eci, dlEarfcn, physCellId, rsrp, ta, dlBandwidth, rsrq);
        }
    }
}
