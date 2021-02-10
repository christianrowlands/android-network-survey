package com.craxiom.networksurvey.helpers.geopackage;

public enum SurveyTypes {

    WIFI_SURVEY("wifi"),
    GNSS_SURVEY("gnss"),
    CELLULAR_SURVEY("cellular");

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
