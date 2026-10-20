package com.craxiom.networksurvey.logging.db.uploader;

import androidx.annotation.VisibleForTesting;

/**
 * The base URLs the community database upload clients talk to. Production always uses the
 * {@link UploadConstants} values; the instrumented destination tests point them at a local
 * MockWebServer instead, because the clients are interfaces built by static factories and cannot
 * take a URL any other way.
 */
public final class UploadEndpoints
{
    private static volatile String openCelliDBaseUrl = UploadConstants.OPENCELLID_URL;
    private static volatile String beaconDbBaseUrl = UploadConstants.BEACONDB_URL;

    private UploadEndpoints()
    {
    }

    public static String getOpenCelliDBaseUrl()
    {
        return openCelliDBaseUrl;
    }

    public static String getBeaconDbBaseUrl()
    {
        return beaconDbBaseUrl;
    }

    /**
     * Points both upload clients at {@code baseUrl}, or back at production when it is null.
     */
    @VisibleForTesting
    public static void overrideForTest(String baseUrl)
    {
        openCelliDBaseUrl = baseUrl == null ? UploadConstants.OPENCELLID_URL : baseUrl;
        beaconDbBaseUrl = baseUrl == null ? UploadConstants.BEACONDB_URL : baseUrl;
    }
}
