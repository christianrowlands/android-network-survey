package com.craxiom.networksurvey.services.controller;

import android.telephony.TelephonyManager;

/**
 * A wrapper class for the TelephonyManager that also stores the subscription ID because the
 * TelephonyManager does not have a way to get the subscription ID prior to API 30.
 */
public class TelephonyManagerWrapper
{
    private final TelephonyManager telephonyManager;
    private final int subscriptionId;
    private final String phoneNumber;

    /**
     * Creates a new TelephonyManagerWrapper instance.
     *
     * @param telephonyManager The TelephonyManager to store in this wrapper.
     * @param subscriptionId   The subscription ID to use when getting the TelephonyManager.
     */
    public TelephonyManagerWrapper(TelephonyManager telephonyManager, int subscriptionId, String phoneNumber)
    {
        this.telephonyManager = telephonyManager;
        this.subscriptionId = subscriptionId;
        this.phoneNumber = phoneNumber;
    }

    public TelephonyManager getTelephonyManager()
    {
        return telephonyManager;
    }

    public int getSubscriptionId()
    {
        return subscriptionId;
    }

    public String getPhoneNumber()
    {
        return phoneNumber;
    }
}
