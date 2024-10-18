package com.craxiom.networksurvey.constants.csv;

/**
 * The constants associated with the Phone State CSV file headers.
 * <p>
 * The constants in this class are intended to match the constants defined in the
 * <a href="https://messaging.networksurvey.app/">Network Survey Messaging API</a>.
 */
public class PhoneStateCsvConstants extends CellularCsvConstants
{
    private PhoneStateCsvConstants()
    {
    }

    public static final String SIM_STATE = "simState";
    public static final String SIM_OPERATOR = "simOperator";
    public static final String NETWORK_REGISTRATION = "networkRegistrationInfo";
    public static final String SLOT = "slot";
    public static final String NON_TERRESTRIAL_NETWORK = "nonTerrestrialNetwork";
}
