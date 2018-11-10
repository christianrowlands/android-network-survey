package com.craxiom.networksurvey;

import android.location.Location;

public class LteSurveyRecordBuilder
{
    private Location location;
    private long time;
    private int recordNumber = Integer.MAX_VALUE;
    private int groupNumber = Integer.MAX_VALUE;
    private int mcc = Integer.MAX_VALUE;
    private int mnc = Integer.MAX_VALUE;
    private int tac = Integer.MAX_VALUE;
    private int ci = Integer.MAX_VALUE;
    private int earfcn = Integer.MAX_VALUE;
    private int pci = Integer.MAX_VALUE;
    private int rsrp = Integer.MAX_VALUE;
    private int rsrq = Integer.MAX_VALUE;
    private int ta = Integer.MAX_VALUE;

    public LteSurveyRecordBuilder setLocation(Location location)
    {
        this.location = location;
        return this;
    }

    public LteSurveyRecordBuilder setTime(long time)
    {
        this.time = time;
        return this;
    }

    public LteSurveyRecordBuilder setRecordNumber(int recordNumber)
    {
        this.recordNumber = recordNumber;
        return this;
    }

    public LteSurveyRecordBuilder setGroupNumber(int groupNumber)
    {
        this.groupNumber = groupNumber;
        return this;
    }

    public LteSurveyRecordBuilder setMcc(int mcc)
    {
        this.mcc = mcc;
        return this;
    }

    public LteSurveyRecordBuilder setMnc(int mnc)
    {
        this.mnc = mnc;
        return this;
    }

    public LteSurveyRecordBuilder setTac(int tac)
    {
        this.tac = tac;
        return this;
    }

    public LteSurveyRecordBuilder setCi(int ci)
    {
        this.ci = ci;
        return this;
    }

    public LteSurveyRecordBuilder setEarfcn(int earfcn)
    {
        this.earfcn = earfcn;
        return this;
    }

    public LteSurveyRecordBuilder setPci(int pci)
    {
        this.pci = pci;
        return this;
    }

    public LteSurveyRecordBuilder setRsrp(int rsrp)
    {
        this.rsrp = rsrp;
        return this;
    }

    public LteSurveyRecordBuilder setRsrq(int rsrq)
    {
        this.rsrq = rsrq;
        return this;
    }

    public LteSurveyRecordBuilder setTa(int ta)
    {
        this.ta = ta;
        return this;
    }

    public LteSurveyRecord createLteSurveyRecord()
    {
        return new LteSurveyRecord(location, time, recordNumber, groupNumber, mcc, mnc, tac, ci, earfcn, pci, rsrp, rsrq, ta);
    }
}