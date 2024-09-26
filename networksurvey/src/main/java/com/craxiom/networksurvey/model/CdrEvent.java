package com.craxiom.networksurvey.model;

import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.CS_CELL_IDENTIFIER;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.CS_RANT;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.DESTINATION_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.EVENT;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.ORIGINATING_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.PS_CELL_IDENTIFIER;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.PS_RANT;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.SLOT;
import static com.craxiom.networksurvey.constants.csv.CdrCsvConstants.START_TIME;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LONGITUDE;

import android.location.Location;

import com.craxiom.messaging.phonestate.NetworkType;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.craxiom.networksurvey.util.NsUtils;

import java.time.ZonedDateTime;

/**
 * Represents a Call Detail Record Event.
 *
 * @since 1.11
 */
public class CdrEvent
{
    private final ZonedDateTime timestamp;
    private Location location;
    private final CdrEventType eventType;
    private final String callingNumber;
    private final String calledNumber;
    private NetworkType csRant;
    private NetworkType psRant;
    private String csCellIdentifier = "";
    private String psCellIdentifier = "";
    private final int slot;

    public CdrEvent(CdrEventType eventType, String callingNumber, String calledNumber, int subscriptionId)
    {
        timestamp = ZonedDateTime.now();
        this.eventType = eventType;
        this.callingNumber = callingNumber;
        this.calledNumber = calledNumber;
        slot = subscriptionId;
    }

    /**
     * @return A String array that represents the CSV column headers for this event. These are the
     * headers that should be written to the CSV file.
     */
    public static String[] getHeaders()
    {
        return new String[]{START_TIME, LATITUDE, LONGITUDE, ALTITUDE, ACCURACY, EVENT,
                ORIGINATING_ADDRESS, DESTINATION_ADDRESS, CS_RANT, CS_CELL_IDENTIFIER, PS_RANT,
                PS_CELL_IDENTIFIER, SLOT};
    }

    public void setLocation(Location location)
    {
        this.location = location;
    }

    public void setCircuitSwitchedInformation(NetworkType rant, String cgi)
    {
        csRant = rant;
        csCellIdentifier = cgi;
    }

    public void setPacketSwitchedInformation(NetworkType rant, String cgi)
    {
        psRant = rant;
        psCellIdentifier = cgi;
    }

    /**
     * @return A String array that contains the CDR event values that can be written out as a CSV
     * row.
     */
    public String[] getCsvRowArray()
    {
        // The order of these values in the array matters. It MUST be kept in sync with the
        // getHeaders method, and new columns should not be inserted in the middle since consuming
        // applications need to trust that the order will not change.
        return new String[]{
                NsUtils.getRfc3339String(timestamp),
                String.valueOf(location == null ? "" : location.getLatitude()),
                String.valueOf(location == null ? "" : location.getLongitude()),
                String.valueOf(location == null ? "" : location.getAltitude()),
                String.valueOf(location == null ? "" : location.getAccuracy()),
                eventType.toString(),
                callingNumber,
                calledNumber,
                csRant.toString(),
                csCellIdentifier,
                psRant.toString(),
                psCellIdentifier,
                slot != CellularController.DEFAULT_SUBSCRIPTION_ID ? String.valueOf(slot) : ""
        };
    }

    /**
     * Checks to see if the location area in the cell identifier from the provided CDR event has
     * changed since the values were placed in this CDR event. In other words, has a location update
     * occurred.
     *
     * @param cdrEvent The new CDR event with the possible new location area.
     * @return True if the location area has changed, false otherwise.
     */
    public boolean locationAreaChanged(CdrEvent cdrEvent)
    {
        if (cdrEvent == null) return false;

        if (cdrEvent.csCellIdentifier.isEmpty() && cdrEvent.psCellIdentifier.isEmpty())
        {
            return false;
        }

        String thisCsLocationArea = extractLocationArea(csCellIdentifier);
        String newCsLocationArea = extractLocationArea(cdrEvent.csCellIdentifier);
        if (!thisCsLocationArea.equals(newCsLocationArea)) return true;

        String thisPsLocationArea = extractLocationArea(psCellIdentifier);
        String newPsLocationArea = extractLocationArea(cdrEvent.psCellIdentifier);
        return !thisPsLocationArea.equals(newPsLocationArea);
    }

    /**
     * Pulls the location area from the provide cell identifier. An empty string is returned if the
     * location area could not be extracted.
     */
    private String extractLocationArea(String csCellIdentifier)
    {
        if (!csCellIdentifier.isEmpty())
        {
            int index = csCellIdentifier.lastIndexOf('-');
            if (index > 0)
            {
                return csCellIdentifier.substring(0, index);
            }
        }

        return "";
    }
}
