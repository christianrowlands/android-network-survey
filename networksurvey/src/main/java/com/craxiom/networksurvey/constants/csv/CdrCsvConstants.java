package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the CDR CSV file headers.
 *
 * @since 1.11
 */
public class CdrCsvConstants extends CsvConstants
{
    private CdrCsvConstants()
    {
    }

    public static final String START_TIME = "startTime";
    public static final String EVENT = "event";
    public static final String ORIGINATING_ADDRESS = "originatingAddress";
    public static final String DESTINATION_ADDRESS = "destinationAddress";

    /**
     * The Circuit Switched Radio Access Network Technology. E.g. LTE. See
     * {@link com.craxiom.messaging.phonestate.NetworkType} for the allowed values in this column.
     */
    public static final String CS_RANT = "csAccessNetworkTechnology";

    /**
     * The Circuit Switched Cell Identifier. This is different for each technology. I specifically did not call this
     * the CGI because I wanted to include the TAC.
     */
    public static final String CS_CELL_IDENTIFIER = "csCellIdentifier";

    /**
     * The Packet Switched Radio Access Network Technology. E.g. LTE. See
     * {@link com.craxiom.messaging.phonestate.NetworkType} for the allowed values in this column.
     */
    public static final String PS_RANT = "psAccessNetworkTechnology";

    /**
     * The Packet Switched Cell Identifier. This is different for each technology. I specifically
     * did not call this the CGI because I wanted to include the TAC.
     */
    public static final String PS_CELL_IDENTIFIER = "psCellIdentifier";

    /**
     * The subscription ID that the event occurred on. This allows for multiple SIMs to be tracked.
     * The value does not have to start at 0 ot 1, it can start at any value.
     */
    public static final String SLOT = "slot";
}
