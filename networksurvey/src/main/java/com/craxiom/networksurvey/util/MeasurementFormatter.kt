package com.craxiom.networksurvey.util

import android.content.Context
import com.craxiom.networksurvey.R
import kotlin.math.roundToInt

/**
 * Centralized utility for formatting measurements with unit-aware display.
 * Supports both metric and imperial units based on user preferences.
 *
 * This formatter handles:
 * - Distance (meters/feet, kilometers/miles)
 * - Altitude (meters/feet)
 * - Speed (km/h / mph)
 * - Accuracy (meters/feet)
 *
 * Note: This only affects UI display. All exported data (CSV, GeoPackage, MQTT, gRPC)
 * remains in metric/SI units.
 */
object MeasurementFormatter {
    // Conversion constants
    private const val METERS_TO_FEET = 3.28084
    private const val METERS_TO_MILES = 0.000621371
    private const val KMH_TO_MPH = 0.621371

    // Thresholds for unit switching
    private const val METERS_THRESHOLD = 1000.0  // Switch to km above this
    private const val FEET_THRESHOLD = 1000.0    // Switch to miles above this

    /**
     * Formats a distance value for display on the map or in dialogs.
     * Uses meters/kilometers for metric, feet/miles for imperial.
     *
     * @param context The context for accessing preferences
     * @param meters The distance in meters
     * @return Formatted distance string with units (e.g., "500m", "2.5km", "1500ft", "1.2mi")
     */
    @JvmStatic
    fun formatDistance(context: Context, meters: Double): String {
        return if (PreferenceUtils.useImperialUnits(context)) {
            formatDistanceImperial(meters)
        } else {
            formatDistanceMetric(meters)
        }
    }

    /**
     * Formats altitude for display.
     *
     * @param context The context for accessing preferences
     * @param meters The altitude in meters
     * @return Formatted altitude string with units (e.g., "150.5 m" or "493.8 ft")
     */
    @JvmStatic
    fun formatAltitude(context: Context, meters: Double): String {
        return if (PreferenceUtils.useImperialUnits(context)) {
            val feet = meters * METERS_TO_FEET
            context.getString(R.string.gps_altitude_value_feet, feet)
        } else {
            context.getString(R.string.gps_altitude_value_meters, meters)
        }
    }

    /**
     * Formats speed for display, converting from meters per second.
     *
     * @param context The context for accessing preferences
     * @param metersPerSecond The speed in meters per second
     * @return Formatted speed string with units (e.g., "45.5 km/h" or "28.3 mph")
     */
    @JvmStatic
    fun formatSpeed(context: Context, metersPerSecond: Float): String {
        return if (PreferenceUtils.useImperialUnits(context)) {
            val mph = toMilesPerHour(metersPerSecond)
            context.getString(R.string.gps_speed_value_mph, mph)
        } else {
            val kmh = toKilometersPerHour(metersPerSecond)
            context.getString(R.string.gps_speed_value_kilometers_hour, kmh)
        }
    }

    /**
     * Formats speed accuracy for display, converting from meters per second.
     *
     * @param context The context for accessing preferences
     * @param metersPerSecond The speed accuracy in meters per second
     * @return Formatted speed accuracy string with units
     */
    @JvmStatic
    fun formatSpeedAccuracy(context: Context, metersPerSecond: Float): String {
        return if (PreferenceUtils.useImperialUnits(context)) {
            val mph = toMilesPerHour(metersPerSecond)
            context.getString(R.string.gps_speed_acc_value_mph, mph)
        } else {
            val kmh = toKilometersPerHour(metersPerSecond)
            context.getString(R.string.gps_speed_acc_value_km_hour, kmh)
        }
    }

    /**
     * Formats horizontal accuracy for display.
     *
     * @param context The context for accessing preferences
     * @param meters The accuracy in meters
     * @return Formatted accuracy string with units (e.g., "5.2 m" or "17.1 ft")
     */
    @JvmStatic
    fun formatAccuracy(context: Context, meters: Float): String {
        return if (PreferenceUtils.useImperialUnits(context)) {
            val feet = meters * METERS_TO_FEET
            context.getString(R.string.gps_accuracy_value_feet, feet.toFloat())
        } else {
            context.getString(R.string.gps_accuracy_value_meters, meters)
        }
    }

    /**
     * Formats horizontal and vertical accuracy for display.
     *
     * @param context The context for accessing preferences
     * @param horizontalMeters The horizontal accuracy in meters
     * @param verticalMeters The vertical accuracy in meters
     * @return Formatted accuracy string with units (e.g., "5.2/3.1 m" or "17.1/10.2 ft")
     */
    @JvmStatic
    fun formatAccuracyPair(
        context: Context,
        horizontalMeters: Float,
        verticalMeters: Float
    ): String {
        return if (PreferenceUtils.useImperialUnits(context)) {
            val horizontalFeet = horizontalMeters * METERS_TO_FEET
            val verticalFeet = verticalMeters * METERS_TO_FEET
            context.getString(
                R.string.gps_hor_and_vert_accuracy_value_feet,
                horizontalFeet.toFloat(),
                verticalFeet.toFloat()
            )
        } else {
            context.getString(
                R.string.gps_hor_and_vert_accuracy_value_meters,
                horizontalMeters,
                verticalMeters
            )
        }
    }

    /**
     * Formats a range value for tower info display.
     *
     * @param context The context for accessing preferences
     * @param meters The range in meters (as integer from tower data)
     * @return Formatted range string with units
     */
    @JvmStatic
    fun formatRange(context: Context, meters: Int): String {
        return formatDistance(context, meters.toDouble())
    }

    // Private helper methods

    private fun formatDistanceMetric(meters: Double): String {
        return when {
            meters < METERS_THRESHOLD -> "${meters.roundToInt()}m"
            else -> {
                val km = meters / 1000.0
                if (km < 10) {
                    String.format("%.1fkm", km)
                } else {
                    "${km.roundToInt()}km"
                }
            }
        }
    }

    private fun formatDistanceImperial(meters: Double): String {
        val feet = meters * METERS_TO_FEET
        return when {
            feet < FEET_THRESHOLD -> "${feet.roundToInt()}ft"
            else -> {
                val miles = meters * METERS_TO_MILES
                if (miles < 10) {
                    String.format("%.1fmi", miles)
                } else {
                    "${miles.roundToInt()}mi"
                }
            }
        }
    }

    /**
     * Converts meters per second to kilometers per hour.
     */
    private fun toKilometersPerHour(metersPerSecond: Float): Float {
        return metersPerSecond * 3600f / 1000f
    }

    /**
     * Converts meters per second to miles per hour.
     */
    private fun toMilesPerHour(metersPerSecond: Float): Float {
        return toKilometersPerHour(metersPerSecond) * KMH_TO_MPH.toFloat()
    }
}
