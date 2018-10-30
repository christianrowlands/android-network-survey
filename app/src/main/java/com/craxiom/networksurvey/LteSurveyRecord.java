package com.craxiom.networksurvey;

import android.location.Location;

/**
 * Represents a snapshot of an LTE tower parameters and signal strength values at the specified
 * time and location.  This record can be for a serving cell, or a neighbor cell.
 *
 * @since 0.0.1
 */
public class LteSurveyRecord
{
    private Location location;
    private long time;
    private int mcc;
    private int mnc;
    private int tac;
    private int ci;
    private int earfcn;
    private int pci;
    private int rsrp;
    private int rsrq;
    private int ta;

    public LteSurveyRecord(Location location, long time, int mcc, int mnc, int tac, int ci, int earfcn, int pci, int rsrp, int rsrq, int ta)
    {
        this.location = location;
        this.time = time;
        this.mcc = mcc;
        this.mnc = mnc;
        this.tac = tac;
        this.ci = ci;
        this.earfcn = earfcn;
        this.pci = pci;
        this.rsrp = rsrp;
        this.rsrq = rsrq;
        this.ta = ta;
    }

    public Location getLocation()
    {
        return location;
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

    public int getCi()
    {
        return ci;
    }

    public int getEarfcn()
    {
        return earfcn;
    }

    public int getPci()
    {
        return pci;
    }

    public int getRsrp()
    {
        return rsrp;
    }

    public int getRsrq()
    {
        return rsrq;
    }

    public int getTa()
    {
        return ta;
    }
}
