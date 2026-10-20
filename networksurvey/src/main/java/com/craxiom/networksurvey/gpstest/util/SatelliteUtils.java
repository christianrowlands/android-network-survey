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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.GnssMeasurement;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.craxiom.networksurvey.gpstest.model.GnssType;
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus;

/**
 * Utilities to manage GNSS signal and satellite information.
 * <p>
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
public class SatelliteUtils
{

    /**
     * Returns true if this device supports the Sensor.TYPE_ROTATION_VECTOR sensor, false if it
     * doesn't
     *
     * @return true if this device supports the Sensor.TYPE_ROTATION_VECTOR sensor, false if it
     * doesn't
     */
    public static boolean isRotationVectorSensorSupported(Context context)
    {
        SensorManager sensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        return sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null;
    }

    /**
     * Returns true if this device supports the Sensor.TYPE_ORIENTATION sensor, false if it
     * doesn't
     *
     * @return true if this device supports the Sensor.TYPE_ORIENTATION sensor, false if it
     * doesn't
     */
    public static boolean isOrientationSensorSupported(Context context)
    {
        SensorManager sensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        return sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION) != null;
    }

    /**
     * Returns true if the platform supports providing carrier frequencies for each satellite, false if it does not
     *
     * @return true if the platform supports providing carrier frequencies for each satellite, false if it does not
     */
    public static boolean isCfSupported()
    {
        return true;
    }

    /**
     * Returns true if automatic gain control is supported for this GNSS measurement, false if it is not
     *
     * @param gnssMeasurement
     * @return true if automatic gain control is supported for this GNSS measurement, false if it is not
     */
    public static boolean isAutomaticGainControlSupported(GnssMeasurement gnssMeasurement)
    {
        return gnssMeasurement.hasAutomaticGainControlLevelDb();
    }

    /**
     * Returns true if carrier phase is supported for this GNSS measurement, false if it is not
     *
     * @param gnssMeasurement
     * @return true if carrier phase is supported for this GNSS measurement, false if it is not
     */
    public static boolean isCarrierPhaseSupported(GnssMeasurement gnssMeasurement)
    {
        return isAccumulatedDeltaRangeStateValid(gnssMeasurement.getAccumulatedDeltaRangeState())
                && gnssMeasurement.getAccumulatedDeltaRangeMeters() != 0.0d;
    }

    /**
     * Returns the result of the GnssMeasurement.ADR_STATE_VALID bitmask being applied to the
     * AccumulatedDeltaRangeState from a GnssMeasurement - true if the ADR state is valid,
     * false if it is not
     *
     * @param accumulatedDeltaRangeState accumulatedDeltaRangeState from GnssMeasurement
     * @return the result of the GnssMeasurement.ADR_STATE_VALID bitmask being applied to the
     * * AccumulatedDeltaRangeState of the given GnssMeasurement - true if the ADR state is valid,
     * * false if it is not
     */
    public static boolean isAccumulatedDeltaRangeStateValid(int accumulatedDeltaRangeState)
    {
        return (GnssMeasurement.ADR_STATE_VALID & accumulatedDeltaRangeState) == GnssMeasurement.ADR_STATE_VALID;
    }

    /**
     * Returns true if "force full GNSS measurements" can be programmatically invoked, and false if not
     *
     * @return true if "force full GNSS measurements" can be programmatically invoked, and false if not
     */
    public static boolean isForceFullGnssMeasurementsSupported()
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /**
     * Returns true if the platform supports GNSS measurements, false if it does not.
     *
     * @return true if the platform supports GNSS measurements, false if it does not
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    public static boolean isMeasurementsSupported(LocationManager manager)
    {
        return manager != null && manager.getGnssCapabilities().hasMeasurements();
    }

    /**
     * Creates a unique key to identify this satellite using a combination of both the svid and
     * constellation type
     *
     * @return a unique key to identify this satellite using a combination of both the svid and
     * constellation type
     */
    public static String createGnssSatelliteKey(SatelliteStatus status)
    {
        if (status.getGnssType() == GnssType.SBAS)
        {
            return status.getSvid() + " " + status.getGnssType() + " " + status.getSbasType();
        } else
        {
            // GNSS
            return status.getSvid() + " " + status.getGnssType();
        }
    }

    /**
     * Creates a unique key to identify a particular signal, or GnssStatus, from a satellite using a
     * combination of both the svid and constellation type and carrier frequency
     *
     * @return a unique key to identify a particular signal, or GnssStatus, from a satellite using a
     * combination of both the svid and constellation type and carrier frequency
     */
    public static String createGnssStatusKey(SatelliteStatus status)
    {
        String carrierLabel = CarrierFreqUtils.getCarrierFrequencyLabel(status);
        if (status.getGnssType() == GnssType.SBAS)
        {
            return status.getSvid() + " " + status.getGnssType() + " " + status.getSbasType() + " " + carrierLabel;
        } else
        {
            // GNSS
            return status.getSvid() + " " + status.getGnssType() + " " + carrierLabel;
        }
    }
}
