package com.craxiom.networksurvey.model

/**
 * Represents the result of a toggle upload scanning operation.
 *
 * @property success Whether the operation was successful
 * @property isEnabled Whether upload scanning is now enabled (only valid if success is true)
 * @property message A user-friendly message describing the result
 * @property surveysStarted A set of survey types that were started (only valid if success is true and isEnabled is true)
 */
data class UploadScanningResult @JvmOverloads constructor(
    val success: Boolean,
    val isEnabled: Boolean = false,
    val message: String,
    val surveysStarted: Set<SurveyTypes> = emptySet()
)
