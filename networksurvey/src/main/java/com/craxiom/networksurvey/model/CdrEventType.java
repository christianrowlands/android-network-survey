package com.craxiom.networksurvey.model;

/**
 * The different CDR event types that we can log to the CDR file.
 */
public enum CdrEventType
{
    OUTGOING_CALL,
    INCOMING_CALL,
    OUTGOING_SMS,
    INCOMING_SMS,
    LOCATION_UPDATE,
    OUTGOING_MMS,
    INCOMING_MMS
}
