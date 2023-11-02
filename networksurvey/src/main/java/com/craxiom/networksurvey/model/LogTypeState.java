package com.craxiom.networksurvey.model;

/**
 * A simple wrapper class to store which logging types are enabled.
 */
public class LogTypeState
{
    public final boolean csv;
    public final boolean geoPackage;

    public LogTypeState(boolean csv, boolean geoPackage)
    {
        this.csv = csv;
        this.geoPackage = geoPackage;
    }
}
