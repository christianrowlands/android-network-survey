package com.craxiom.networksurvey.model;

import com.google.protobuf.GeneratedMessageV3;

/**
 * Wraps the various cellular records so that we can include a variable that specifies which record type it is.
 *
 * @since 1.6.0
 */
public class CellularRecordWrapper
{
    public final CellularProtocol cellularProtocol;
    public final GeneratedMessageV3 cellularRecord;

    public CellularRecordWrapper(CellularProtocol cellularProtocol, GeneratedMessageV3 cellularRecord)
    {
        this.cellularProtocol = cellularProtocol;
        this.cellularRecord = cellularRecord;
    }
}
