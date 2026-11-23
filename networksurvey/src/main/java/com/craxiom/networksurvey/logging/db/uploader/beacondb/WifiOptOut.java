package com.craxiom.networksurvey.logging.db.uploader.beacondb;

import java.util.Locale;

/**
 * Decides which Wi-Fi access points must never be sent to BeaconDB. The Ichnaea geosubmit v2 spec
 * says hidden networks and networks whose SSID ends in {@value #NOMAP_MARKER} "must not be
 * collected", and leaves that filtering to the client: the Ichnaea server never checks it. The
 * BeaconDB server does drop them, but only as a backstop, and it also drops
 * {@value #OPTOUT_MARKER}, so both are skipped here.
 * <p>
 * Only community uploads use this. Local files, MQTT, gRPC and NS Analytics still receive these
 * access points, and randomized (locally administered) BSSIDs are not filtered here.
 */
public final class WifiOptOut
{
    /**
     * The Google and Ichnaea opt-out marker.
     */
    public static final String NOMAP_MARKER = "_nomap";

    /**
     * A second opt-out marker the BeaconDB server honors (commonly attributed to Microsoft's
     * location service; not verified against a Microsoft source).
     */
    public static final String OPTOUT_MARKER = "_optout";

    private static final char NUL = '\0';

    private WifiOptOut()
    {
    }

    /**
     * The spec names a suffix, but the BeaconDB server drops an SSID that contains either marker
     * anywhere (case sensitive), so this matches anywhere too, to never be looser than the server.
     * It also ignores case: an owner who typed "_NoMap" clearly meant to opt out, and leaving a few
     * extra access points out of a community upload costs nothing.
     *
     * @param ssid The SSID as captured, may be null.
     * @return True if the access point is hidden or its owner opted out, so it must not be
     * uploaded to BeaconDB.
     */
    public static boolean isExcludedFromBeaconDb(String ssid)
    {
        if (isHidden(ssid)) return true;

        final String lower = ssid.toLowerCase(Locale.ROOT);
        return lower.contains(NOMAP_MARKER) || lower.contains(OPTOUT_MARKER);
    }

    /**
     * @return True if the SSID is null, empty, or only NUL bytes (how some access points hide
     * their name while still sending a length). BeaconDB reads hidden the same way.
     */
    static boolean isHidden(String ssid)
    {
        if (ssid == null) return true;
        for (int i = 0; i < ssid.length(); i++)
        {
            if (ssid.charAt(i) != NUL) return false;
        }
        return true;
    }
}
