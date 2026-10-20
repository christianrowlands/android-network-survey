/*
 * Copyright (C) 2014-2018 University of South Florida, Sean J. Barbeau (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.craxiom.networksurvey.util;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

/**
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
public class LocationUtils
{
    /**
     * @return A location provider to use for getting locations.
     */
    public static String getLocationProvider(LocationManager locationManager)
    {
        final String provider;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER))
        {
            provider = LocationManager.FUSED_PROVIDER;
        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            provider = LocationManager.GPS_PROVIDER;
        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        {
            provider = LocationManager.NETWORK_PROVIDER;
        } else
        {
            provider = LocationManager.PASSIVE_PROVIDER;
        }

        return provider;
    }

    /**
     * Reports whether a fix came from a mock location provider.
     * <p>
     * {@link Location#isMock()} arrived in API 31 and replaced the deprecated
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
