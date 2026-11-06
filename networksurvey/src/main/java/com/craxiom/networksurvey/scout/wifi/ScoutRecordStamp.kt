package com.craxiom.networksurvey.scout.wifi

import com.craxiom.networksurvey.gpstest.util.FormatUtils
import com.craxiom.networksurvey.gpstest.util.MathUtils
import com.craxiom.networksurvey.util.NsUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * The time and place fields every record built from a Scout capture carries, worked out once so
 * the beacon, client and probe builders write them the same way: `deviceTime` is when the Scout
 * heard it, and the location is the phone fix nearest that time with `locationAge` the gap.
 *
 * @property location Null when no phone fix was close enough, so the record has no location.
 */
class ScoutRecordStamp private constructor(val deviceTime: String, val location: Location?) {

    /** The location fields of a record that has one. */
    class Location(
        val latitude: Double,
        val longitude: Double,
        val altitude: Float,
        val accuracy: Int,
        val locationAge: Int,
        /** Zero when the phone was standing still or did not report a speed; leave the field unset then. */
        val speed: Float,
    )

    companion object {
        /** @return The stamp for a capture heard at [captureTimeMs], located at [fix] when there is one. */
        @JvmStatic
        fun of(captureTimeMs: Long, fix: PhoneFix?): ScoutRecordStamp {
            val time = NsUtils.getRfc3339String(ZonedDateTime.ofInstant(Instant.ofEpochMilli(captureTimeMs), ZoneId.systemDefault()))
            val location = fix?.let {
                Location(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = (it.altitude ?: 0.0).toFloat(),
                    accuracy = MathUtils.roundAccuracy(it.accuracy ?: 0f),
                    locationAge = abs(captureTimeMs - it.timeMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    speed = it.speed?.let(FormatUtils::formatSpeed) ?: 0f,
                )
            }
            return ScoutRecordStamp(time, location)
        }
    }
}
