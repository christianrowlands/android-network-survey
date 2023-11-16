package com.craxiom.networksurvey.model;

import com.craxiom.messaging.NrRecord;

/**
 * Wraps the {@link NrRecord} so that we can include the bands array. This allow us to display the
 * bands in the UI.
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
