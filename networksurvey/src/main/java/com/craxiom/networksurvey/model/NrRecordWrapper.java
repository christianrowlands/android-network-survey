package com.craxiom.networksurvey.model;

import com.craxiom.messaging.NrRecord;

/**
 * Wraps the {@link NrRecord} so that we can include the bands array. This allows us to display the
 * bands in the UI. The bands array is in-memory only and is never serialized with the record.
 */
public class NrRecordWrapper extends CellularRecordWrapper
{
    public final int[] bands;

    public NrRecordWrapper(NrRecord nrRecord, int[] bands)
    {
        super(CellularProtocol.NR, nrRecord);

        this.bands = bands;
    }
}
