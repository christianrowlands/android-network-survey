package com.craxiom.networksurvey.logging.db.model

/**
 * One grid cell of surveyed points, produced by the coarse grouping query when the map is zoomed
 * out too far to draw individual points. [latitude] and [longitude] are the mean position of the
 * rows in the cell, [minUploadedMask] is zero when any row in the cell is still waiting to upload,
 * [bestBucket] is the strongest signal bucket seen in the cell, and [lastTime] the newest visit.
 */
data class SurveyedPointCell(
    val latKey: Long,
    val lonKey: Long,
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    val minUploadedMask: Int,
    val bestBucket: Int,
    val lastTime: Long,
)

/**
 * A grid cell reduced to its most common category value (technology rank, PLMN, cell id, or
 * area key as text, null when unknown). [count], [minUploadedMask], [bestBucket], and [lastTime]
 * describe the rows of that dominant category only; [cellCount] is every row in the cell.
 */
data class SurveyedPointCategoryCell(
    val latKey: Long,
    val lonKey: Long,
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    val category: String?,
    val minUploadedMask: Int,
    val bestBucket: Int,
    val lastTime: Long,
    val cellCount: Int,
)

/** Number of surveyed points per observed kind. */
data class SurveyedPointKindCount(val kind: Int, val count: Int)
