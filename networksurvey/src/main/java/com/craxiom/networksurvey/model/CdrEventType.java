package com.craxiom.networksurvey.model;

/**
 * The different CDR event types that we can log to the CDR file.
 *
 * @since 1.11
 */
public enum CdrEventType
{
    OUTGOING_CALL,
    INCOMING_CALL,
    SMS,
    LOCATION_UPDATE
}
