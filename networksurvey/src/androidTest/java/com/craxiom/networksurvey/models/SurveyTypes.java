package com.craxiom.networksurvey.models;

public enum SurveyTypes
{

    WIFI_SURVEY("wifi"),
    GNSS_SURVEY("gnss"),
    BLUETOOTH_SURVEY("bluetooth"),
    CELLULAR_SURVEY("cellular"),
    PHONE_STATE_SURVEY("phonestate");

    private final String value;

    SurveyTypes(String value)
    {
        this.value = value;
    }

    public String getValue()
    {
        return this.value;
    }
}
