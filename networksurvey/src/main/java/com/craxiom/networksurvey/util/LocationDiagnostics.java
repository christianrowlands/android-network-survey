package com.craxiom.networksurvey.util;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import com.craxiom.networksurvey.BuildConfig;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

/**
 * Debug only diagnostics for figuring out what produced a location fix, and in particular whether
 * a {@link LocationManager#NETWORK_PROVIDER} fix was derived from Wi-Fi access points or from cell
 * towers.
 * <p>
 * Android has no public API for this. The network provider is documented as determining location
 * "based on nearby of cell tower and WiFi access points" without ever saying which one was used for
 * a given fix. {@link Location} itself carries no source field, the only named extras constant in
 * the platform ({@code Location.EXTRA_NO_GPS_LOCATION}) is hidden and deprecated, and
 * {@code ProviderProperties.hasCellRequirement()} describes a provider's static capabilities rather
 * than an individual fix.
 * <p>
 * The one known lead is undocumented. Google Play services has historically attached the answer to
 * the fix through {@link Location#getExtras()}, as {@link #KEY_NETWORK_LOCATION_TYPE} set to either
 * {@link #VALUE_CELL} or {@link #VALUE_WIFI}, alongside a serialized Wi-Fi scan under
 * {@link #KEY_WIFI_SCAN} whenever Wi-Fi contributed. Since that behavior is unsupported, is absent
 * on de-Googled devices, and has never been checked on a current Play services build, this class
 * exists to find out what today's devices actually hand us before anything is built on top of it.
 * <p>
 * The logging here is a no-op outside of a debug build. {@link #isMock(Location)} is the one
 * exception: it is a plain helper that runs in every build, and it lives here so that the survey
 * records and these diagnostics agree on what "mocked" means.
 */
public final class LocationDiagnostics
{
    private static final String LOG_PREFIX = "NS-LOCSRC";

    /**
     * The undocumented Google Play services extra that names the technology behind a network fix.
     * Promote this to {@code NetworkSurveyConstants} if it ever feeds a survey record.
     */
    private static final String KEY_NETWORK_LOCATION_TYPE = "networkLocationType";
    private static final String VALUE_WIFI = "wifi";
    private static final String VALUE_CELL = "cell";

    /**
     * A serialized Wi-Fi scan proto. Its mere presence is evidence that Wi-Fi contributed to the
     * fix, which makes it a weaker fallback if {@link #KEY_NETWORK_LOCATION_TYPE} has gone away.
     */
    private static final String KEY_WIFI_SCAN = "wifiScan";
    private static final String KEY_NEW_WIFI_SCAN = "newWifiScan";

    private static final String KEY_LOCATION_SUBTYPE = "locationSubtype";
    private static final String KEY_NLP_VERSION = "nlpVersion";
    private static final String KEY_LEVEL_ID = "levelId";
    private static final String KEY_LEVEL_NUMBER_E3 = "levelNumberE3";
    private static final String KEY_FLOOR_LABEL = "floorLabel";
    private static final String KEY_VERTICAL_ACCURACY = "verticalAccuracy";
    private static final String KEY_SATELLITES = "satellites";

    private static final String UNKNOWN_PROVIDER = "unknown";
    private static final String NO_EXTRAS_SIGNATURE = "<no extras>";

    /**
     * How long to stay quiet about a fix that looks just like the last one from the same provider.
     * Without this a multi hour survey buries the interesting lines under thousands of duplicates.
     */
    private static final long REPEAT_LOG_INTERVAL_MS = 60_000L;

    private static final Map<String, String> LAST_SIGNATURE_BY_PROVIDER = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_LOG_TIME_BY_PROVIDER = new ConcurrentHashMap<>();

    private LocationDiagnostics()
    {
    }

    /**
     * Logs everything the platform is willing to tell us about where a fix came from, including a
     * full dump of the extras bundle.
     * <p>
     * Call this from a location listener as each fix arrives. Repeat fixes that carry the same
     * information are suppressed for {@link #REPEAT_LOG_INTERVAL_MS}, but a change in the extras,
     * and in particular a Wi-Fi to cell transition, is always logged immediately.
     *
     * @param location The fix that just arrived, which may be null.
     */
    public static void logLocationExtras(Location location)
    {
        if (!BuildConfig.DEBUG) return;
        if (location == null) return;

        try
        {
            logSnapshot(location);
        } catch (Throwable t)
        {
            // Never let a diagnostic take down a location update
            Timber.e(t, "%s Could not log the location snapshot", LOG_PREFIX);
        }
    }

    // Bundle.get(String) is deprecated in API 31, but it is the only way to dump a bundle
    // whose keys and value types are not known ahead of time, which is the entire point here.
    @SuppressWarnings("deprecation")
    private static void logSnapshot(Location location)
    {
        final String provider = location.getProvider() == null ? UNKNOWN_PROVIDER : location.getProvider();
        final Bundle extras = location.getExtras();

        if (!shouldLog(provider, extras)) return;

        Timber.i("%s ===== provider=%s accuracy=%.1f hasVerticalAccuracy=%b mock=%b", LOG_PREFIX,
                provider, location.getAccuracy(), location.hasVerticalAccuracy(), isMock(location));

        if (extras == null || extras.isEmpty())
        {
            Timber.i("%s No extras on this fix, so the platform is telling us nothing about the source", LOG_PREFIX);
            return;
        }

        // The headline answer, on one greppable line
        final String networkLocationType = getStringExtra(extras, KEY_NETWORK_LOCATION_TYPE);
        final boolean wifiScanPresent = extras.containsKey(KEY_WIFI_SCAN) || extras.containsKey(KEY_NEW_WIFI_SCAN);
        Timber.i("%s ANSWER provider=%s networkLocationType=%s wifiScanPresent=%b satellites=%s", LOG_PREFIX,
                provider, networkLocationType == null ? "<absent>" : networkLocationType,
                wifiScanPresent, describeValue(extras.get(KEY_SATELLITES)));

        if (VALUE_WIFI.equals(networkLocationType))
        {
            Timber.i("%s This fix was derived from Wi-Fi access points", LOG_PREFIX);
        } else if (VALUE_CELL.equals(networkLocationType))
        {
            Timber.i("%s This fix was derived from cell towers", LOG_PREFIX);
        }

        for (String key : new TreeSet<>(extras.keySet()))
        {
            Timber.i("%s   extra %s=%s", LOG_PREFIX, key, describeValue(extras.get(key)));
        }
    }

    /**
     * Rate limits the dump so that a long survey stays readable without hiding a change in source.
     * <p>
     * The signature deliberately includes the value of {@link #KEY_NETWORK_LOCATION_TYPE} and not
     * just the key set, because a Wi-Fi to cell transition leaves the key set alone and is the
     * single most interesting thing this class can catch.
     *
     * @return True if this fix should be logged, false if it repeats a recent one.
     */
    private static boolean shouldLog(String provider, Bundle extras)
    {
        final String signature = buildSignature(extras);
        final long now = SystemClock.elapsedRealtime();

        final String lastSignature = LAST_SIGNATURE_BY_PROVIDER.get(provider);
        final Long lastLogTime = LAST_LOG_TIME_BY_PROVIDER.get(provider);

        final boolean unchanged = signature.equals(lastSignature);
        final boolean recent = lastLogTime != null && now - lastLogTime < REPEAT_LOG_INTERVAL_MS;
        if (unchanged && recent) return false;

        LAST_SIGNATURE_BY_PROVIDER.put(provider, signature);
        LAST_LOG_TIME_BY_PROVIDER.put(provider, now);
        return true;
    }

    private static String buildSignature(Bundle extras)
    {
        if (extras == null || extras.isEmpty()) return NO_EXTRAS_SIGNATURE;

        final Set<String> sortedKeys = new TreeSet<>(extras.keySet());
        return sortedKeys + "|" + getStringExtra(extras, KEY_NETWORK_LOCATION_TYPE);
    }

    /**
     * Reads an extra that is expected to hold a String without assuming that it does, since these
     * keys are undocumented and nothing stops a provider from putting another type there.
     */
    @SuppressWarnings("deprecation")
    private static String getStringExtra(Bundle extras, String key)
    {
        final Object value = extras.get(key);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Renders an extras value for the log.
     * <p>
     * Byte arrays are reported by length only. {@link #KEY_WIFI_SCAN} is a serialized scan
     * containing BSSIDs, and knowing that it is present is the whole signal we need. Writing access
     * point MAC addresses into logcat is a bad trade in a survey app.
     */
    private static String describeValue(Object value)
    {
        if (value == null) return "null";
        if (value instanceof byte[]) return "byte[" + ((byte[]) value).length + "]";
        if (value instanceof Bundle) return "Bundle(" + ((Bundle) value).size() + " keys)";
        // Guards against a nested Location, which would otherwise recurse through its own extras
        if (value instanceof Location) return "Location(" + ((Location) value).getProvider() + ")";
        return String.valueOf(value);
    }

    /**
     * Reports whether a fix came from a mock location provider.
     * <p>
     * Unlike the logging in this class, this method is not debug only. It is the single place the
     * app decides what "mocked" means, so that the survey records and the diagnostic logs cannot
     * drift apart. {@link Location#isMock()} arrived in API 31 and replaced the deprecated
     * {@link Location#isFromMockProvider()}, which is what is used below that.
     *
     * @param location The fix to inspect, which must not be null.
     * @return True if the platform reported the fix as coming from a mock provider.
     */
    @SuppressWarnings("deprecation")
    public static boolean isMock(Location location)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            return location.isMock();
        } else
        {
            return location.isFromMockProvider();
        }
    }
}
