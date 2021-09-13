package com.craxiom.networksurvey.constants;

/**
 * The constants associated with the NR (New Record for 5G) table in the GeoPackage file. From
 * <a href="https://messaging.networksurvey.app/#operation-publish-nr_message">NR for MQTT publishing</a>
 *
 * @since 1.5.0
 */
public final class NrMessageConstants
{
    private NrMessageConstants()
    {
    }

    public static final String NR_RECORD_MESSAGE_TYPE = "NrRecord";
    public static final String NR_RECORDS_TABLE_NAME = "NR_MESSAGE";

    // may abstract these later to go with new schema where we match values with MQTT
    public static final String DEVICE_TIME_COLUMN = "deviceTime";
    public static final String LATITUDE_COLUMN = "latitude";
    public static final String LONGITUDE_COLUMN = "longitude";
    public static final String ALTITUDE_COLUMN = "altitude";
    public static final String MISSION_ID_COLUMN = "missionId";
    public static final String RECORD_NUMBER_COLUMN = "recordNumber";
    public static final String GROUP_NUMBER_COLUMN = "groupNumber";
    public static final String SERVING_CELL_COLUMN = "servingCell";
    public static final String PROVIDER_COLUMN = "provider";
    public static final String ACCURACY = "accuracy";

    // unique to new radio
    public static final String MCC_COLUMN = "mcc";
    public static final String MNC_COLUMN = "mnc";
    public static final String TAC_COLUMN = "tac";
    public static final String NCI_COLUMN = "nci";
    public static final String NARFCN_COLUMN = "narfcn";    // equivalent to nrarfcn
    public static final String PCI_COLUMN = "pci";
    public static final String SS_RSRP_COLUMN = "ssRsrp";
    public static final String SS_RSRQ_COLUMN = "ssRsrq";
    public static final String SS_SINR_COLUMN = "ssSinr";
    public static final String CSI_RSRP_COLUMN = "csiRsrp";
    public static final String CSI_RSRQ_COLUMN = "csiRsrq";
    public static final String CSI_SINR_COLUMN = "csiSinr";
}
