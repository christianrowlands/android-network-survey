package com.craxiom.networksurvey.constants;

/**
 * The constants associated with the NR (New Record for 5G) table in the GeoPackage file.
 *
 * @since 1.5.0-SNAPSHOT
 */
public final class NrMessageConstants extends CellularMessageConstants
{
    private NrMessageConstants(){}

    public static final String NR_RECORD_MESSAGE_TYOE = "NrRecord";
    public static final String NR_RECORDS_TABLE_NAME = "NR_MESSAGE";

    // TODO: 8/20/2021 Include MCC, MNC, LAT, LONG, ALT?
    public static final String NRARFCN_COLUMN = "NRARFCN";
    public static final String PCI_COLUMN = "Phys_cell_ID";
    public static final String TAC_COLUMN = "TAC";
    public static final String NCI_COLUMN = "NCI";
    public static final String CSI_RSRP_COLUMN = "CSI_RSRP";
    public static final String CSI_RSRQ_COLUMN = "CSI_RSRQ";
    public static final String CSI_SINR_COLUMN = "CSI_SINR";
    public static final String SS_RSRP_COLUMN = "SS_RSRP";
    public static final String SS_RSRQ_COLUMN = "SS_RSRQ";
    public static final String SS_SINR_COLUMN = "SS_SINR";
}
