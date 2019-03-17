package com.craxiom.networksurvey;

import android.location.Location;
import android.util.Log;

/**
 * Builder class for an {@link LteSurveyRecord}.  The required fields are
 */
public class LteSurveyRecordBuilder
{
    private final String LOG_TAG = LteSurveyRecordBuilder.class.getSimpleName();

    private Location location;
    private long time = -1;
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

    /**
     * @return An {@link LteSurveyRecord} populated will all the values from this builder.
     * @throws IllegalArgumentException If all the required values are not set.
     */
    public LteSurveyRecord createLteSurveyRecord()
    {
        final String errorMessage = validate();
        if (!errorMessage.isEmpty())
        {
            Log.w(LOG_TAG, "Skipping an LTE Survey Record because it was not valid.  " + errorMessage);
            return null;
        }

        return new LteSurveyRecord(location, time, recordNumber, groupNumber, mcc, mnc, tac, ci, earfcn, pci, rsrp, rsrq, ta);
    }

    /**
     * Validates the required fields, and throws an exception if any of them are not set.
     */
    private String validate()
    {
        String errorMessage = "";

        if (time == -1)
        {
            errorMessage += "The time is required to build an LTE Survey Record.  ";
        }

        if (recordNumber == Integer.MAX_VALUE || groupNumber == Integer.MAX_VALUE)
        {
            errorMessage += "The record number and group number are required to" +
                    "build an LTE Survey Record.  ";
        }

        if (earfcn == Integer.MAX_VALUE)
        {
            errorMessage += "The EARFCN is required to build an LTE Survey Record.  ";
        }

        if (pci == Integer.MAX_VALUE)
        {
            errorMessage += "The PCI is required to build an LTE Survey Record.  ";
        }

        if (rsrp == Integer.MAX_VALUE)
        {
            errorMessage += "The RSRP is required to build an LTE Survey Record.  ";
        }

        return errorMessage;
    }
}