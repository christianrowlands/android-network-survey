/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.craxiom.networksurvey.model

/**
 * Mirrors the GnssStatus class (https://developer.android.com/reference/android/location/GnssStatus),
 * but uses internal GnssType and SbasType values for GNSS and SBAS constellations, respectively
 *
 * Originally from the GPS Test open source Android app.
 * https://github.com/barbeau/gpstest
 */
data class SatelliteStatus(
        val svid: Int,
        val gnssType: GnssType,
        var cn0DbHz: Float,
        val hasAlmanac: Boolean,
        val hasEphemeris: Boolean,
        val usedInFix: Boolean,
        var elevationDegrees: Float,
        var azimuthDegrees: Float) {
    var sbasType: SbasType = SbasType.UNKNOWN
    var hasCarrierFrequency: Boolean = false
    var carrierFrequencyHz: Float = NO_DATA
    var agc: Double = NO_DATA_DOUBLE

    companion object {
        const val NO_DATA = 0.0f
        const val NO_DATA_DOUBLE = 0.0
    }
}
