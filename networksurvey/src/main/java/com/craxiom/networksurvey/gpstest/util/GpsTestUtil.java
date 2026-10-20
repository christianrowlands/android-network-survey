/*
 * Copyright (C) 2013 Sean J. Barbeau
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

package com.craxiom.networksurvey.gpstest.util;

import android.location.GnssStatus;

import com.craxiom.networksurvey.gpstest.model.GnssType;

import static com.craxiom.networksurvey.gpstest.model.GnssType.BEIDOU;
import static com.craxiom.networksurvey.gpstest.model.GnssType.GALILEO;
import static com.craxiom.networksurvey.gpstest.model.GnssType.GLONASS;
import static com.craxiom.networksurvey.gpstest.model.GnssType.IRNSS;
import static com.craxiom.networksurvey.gpstest.model.GnssType.NAVSTAR;
import static com.craxiom.networksurvey.gpstest.model.GnssType.QZSS;
import static com.craxiom.networksurvey.gpstest.model.GnssType.SBAS;
import static com.craxiom.networksurvey.gpstest.model.GnssType.UNKNOWN;

/**
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
public class GpsTestUtil
{
    private static final int CONSTELLATION_IRNSS_TEMP = 7;

    /**
     * Returns the Global Navigation Satellite System (GNSS) for a satellite given the GnssStatus
     * constellation type.  For Android 7.0 and higher.  This is basically a translation to our
     * own GnssType enumeration that we use for Android 6.0.1 and lower.  Note that
     * SatelliteUtil.toSbasType() should be used to get the particular SBAS constellation type
     *
     * @param gnssConstellationType constellation type provided by the GnssStatus.getConstellationType()
     *                              method
     * @return GnssType for the given GnssStatus constellation type
     */
    public static GnssType getGnssConstellationType(int gnssConstellationType)
    {
        switch (gnssConstellationType)
        {
            case GnssStatus.CONSTELLATION_GPS:
                return NAVSTAR;
            case GnssStatus.CONSTELLATION_GLONASS:
                return GLONASS;
            case GnssStatus.CONSTELLATION_BEIDOU:
                return BEIDOU;
            case GnssStatus.CONSTELLATION_QZSS:
                return QZSS;
            case GnssStatus.CONSTELLATION_GALILEO:
                return GALILEO;
            case CONSTELLATION_IRNSS_TEMP:
                // FIX ME - We can't use the GnssStatus.CONSTELLATION_IRNSS Android SDK constant in
                // this switch statement until this Android bug is fixed - https://issuetracker.google.com/issues/134611316
                // For now, we define CONSTELLATION_IRNSS_TEMP to be the same value of 7 so we can
                // still support IRNSS.
                return IRNSS;
            case GnssStatus.CONSTELLATION_SBAS:
                return SBAS;
            case GnssStatus.CONSTELLATION_UNKNOWN:
                return UNKNOWN;
            default:
                return UNKNOWN;
        }
    }

    /**
     * @return The time duration, in milliseconds, during which we expect to receive at least one GNSS measurement
     *
     * @since 1.8.0
     */
    public static long getGnssTimeoutIntervalMs(final long gnssScanRateMs)
    {
        return gnssScanRateMs * 2L;
    }

}
