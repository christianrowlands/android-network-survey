package com.craxiom.networksurvey.models.message.cellular;

import java.util.Objects;

import mil.nga.sf.Point;

public class LteModel {

    private int id;
    private Point geom;
    private int time;
    private int recordNumber;
    private int groupNumber;
    private Boolean servingCell;
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
    private LteModel(int id, Point geom, int time, int recordNumber, int groupNumber, Boolean servingCell, String provider, int mcc, int mnc, int tac, int eci, int dlEarfcn, int physCellId, float rsrp, int ta, String dlBandwidth) {
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
    }

    @Override
    public String toString() {
        return "LteModel{" +
                "id=" + id +
                ", geom=" + geom +
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
                '}';
    }

    @Override
    public boolean equals(Object o) {
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
                Objects.equals(geom, lteModel.geom) &&
                Objects.equals(servingCell, lteModel.servingCell) &&
                Objects.equals(provider, lteModel.provider) &&
                Objects.equals(dlBandwidth, lteModel.dlBandwidth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, tac, eci, dlEarfcn, physCellId, rsrp, ta, dlBandwidth);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Point getGeom() {
        return geom;
    }

    public void setGeom(Point geom) {
        this.geom = geom;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public void setRecordNumber(int recordNumber) {
        this.recordNumber = recordNumber;
    }

    public int getGroupNumber() {
        return groupNumber;
    }

    public void setGroupNumber(int groupNumber) {
        this.groupNumber = groupNumber;
    }

    public Boolean getServingCell() {
        return servingCell;
    }

    public void setServingCell(Boolean servingCell) {
        this.servingCell = servingCell;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getMcc() {
        return mcc;
    }

    public void setMcc(int mcc) {
        this.mcc = mcc;
    }

    public int getMnc() {
        return mnc;
    }

    public void setMnc(int mnc) {
        this.mnc = mnc;
    }

    public int getTac() {
        return tac;
    }

    public void setTac(int tac) {
        this.tac = tac;
    }

    public int getEci() {
        return eci;
    }

    public void setEci(int eci) {
        this.eci = eci;
    }

    public int getDlEarfcn() {
        return dlEarfcn;
    }

    public void setDlEarfcn(int dlEarfcn) {
        this.dlEarfcn = dlEarfcn;
    }

    public int getPhysCellId() {
        return physCellId;
    }

    public void setPhysCellId(int physCellId) {
        this.physCellId = physCellId;
    }

    public float getRsrp() {
        return rsrp;
    }

    public void setRsrp(float rsrp) {
        this.rsrp = rsrp;
    }

    public int getTa() {
        return ta;
    }

    public void setTa(int ta) {
        this.ta = ta;
    }

    public String getDlBandwidth() {
        return dlBandwidth;
    }

    public void setDlBandwidth(String dlBandwidth) {
        this.dlBandwidth = dlBandwidth;
    }

    public static class LteModelBuilder {

        private int id;
        private Point geom;
        private int time;
        private int recordNumber;
        private int groupNumber;
        private Boolean servingCell;
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

        public LteModelBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public LteModelBuilder setGeom(Point geom) {
            this.geom = geom;
            return this;
        }

        public LteModelBuilder setTime(int time) {
            this.time = time;
            return this;
        }

        public LteModelBuilder setRecordNumber(int recordNumber) {
            this.recordNumber = recordNumber;
            return this;
        }

        public LteModelBuilder setGroupNumber(int groupNumber) {
            this.groupNumber = groupNumber;
            return this;
        }

        public LteModelBuilder setServingCell(Boolean servingCell) {
            this.servingCell = servingCell;
            return this;
        }

        public LteModelBuilder setProvider(String provider) {
            this.provider = provider;
            return this;
        }

        public LteModelBuilder setMcc(int mcc) {
            this.mcc = mcc;
            return this;
        }

        public LteModelBuilder setMnc(int mnc) {
            this.mnc = mnc;
            return this;
        }

        public LteModelBuilder setTac(int tac) {
            this.tac = tac;
            return this;
        }

        public LteModelBuilder setEci(int eci) {
            this.eci = eci;
            return this;
        }

        public LteModelBuilder setDlEarfcn(int dlEarfcn) {
            this.dlEarfcn = dlEarfcn;
            return this;
        }

        public LteModelBuilder setPhysCellId(int physCellId) {
            this.physCellId = physCellId;
            return this;
        }

        public LteModelBuilder setRsrp(float rsrp) {
            this.rsrp = rsrp;
            return this;
        }

        public LteModelBuilder setTa(int ta) {
            this.ta = ta;
            return this;
        }

        public LteModelBuilder setDlBandwidth(String dlBandwidth) {
            this.dlBandwidth = dlBandwidth;
            return this;
        }

        public LteModel build() {
            return new LteModel(id, geom, time, recordNumber, groupNumber, servingCell, provider, mcc, mnc, tac, eci, dlEarfcn, physCellId, rsrp, ta, dlBandwidth);
        }
    }
}
