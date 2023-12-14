package com.craxiom.networksurvey.constants.csv;

/**
 * The common constants associated with survey CSV records.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public abstract class CellularCsvConstants extends SurveyCsvConstants
{
    public static final String GROUP_NUMBER = "groupNumber";
    public static final String SERVING_CELL = "servingCell";
    public static final String PROVIDER = "provider";
    public static final String SLOT = "slot";
}
