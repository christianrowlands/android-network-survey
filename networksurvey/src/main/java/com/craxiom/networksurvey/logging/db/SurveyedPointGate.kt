package com.craxiom.networksurvey.logging.db

/**
 * Decides when a survey batch is far enough from the last recorded point of the same kind to
 * become a new survey point. The threshold adapts to speed so a walked warehouse produces a
 * usable trail while a drive does not flood the table.
 *
 * Each observed kind (cellular, Wi-Fi, Bluetooth) keeps its own anchor, so one step can yield at
 * most one point per kind regardless of how many records or SIMs reported it. A rejected
 * observation never moves the anchor, so slow drift still accumulates into a new point.
 *
 * Not thread safe; callers invoke it from a single executor thread.
 */
class SurveyedPointGate(
    private val walkingThresholdMeters: Int = WALKING_THRESHOLD_METERS,
    private val drivingThresholdMeters: Int = DbUploadStore.DISTANCE_MOVED_THRESHOLD_METERS,
    private val walkingSpeedLimitMps: Float = WALKING_SPEED_LIMIT_MPS,
    private val maxAccuracyMeters: Int = DbUploadStore.ACCURACY_THRESHOLD_METERS,
) {
    private val anchors = HashMap<Int, Pair<Double, Double>>()

    /**
     * @return true when the observation should be written as a new surveyed point. The anchor
     * for [kind] is updated only when this returns true.
     */
    fun shouldRecord(
        kind: Int,
        latitude: Double,
        longitude: Double,
        accuracy: Int,
        speedMps: Float
    ): Boolean {
        if (accuracy <= 0 || accuracy > maxAccuracyMeters) return false
        if (latitude == 0.0 && longitude == 0.0) return false

        val threshold =
            if (speedMps < walkingSpeedLimitMps) walkingThresholdMeters else drivingThresholdMeters
        if (!DbUploadStore.hasMovedEnough(
                latitude,
                longitude,
                anchors[kind],
                threshold
            )
        ) return false

        anchors[kind] = Pair(latitude, longitude)
        return true
    }

    /** Forgets every anchor so the next observation of each kind is recorded. */
    fun reset() = anchors.clear()

    companion object {
        /** Spacing between points while on foot, so an indoor walk still draws a trail. */
        const val WALKING_THRESHOLD_METERS = 10

        /** Speeds under this are treated as walking; roughly 11 km/h. */
        const val WALKING_SPEED_LIMIT_MPS = 3f
    }
}
