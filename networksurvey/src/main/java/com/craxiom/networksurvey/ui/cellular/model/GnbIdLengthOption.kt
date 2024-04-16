package com.craxiom.networksurvey.ui.cellular.model

/**
 * A simple data class for combining the gNB ID length options along with the corresponding bit shifting count.
 */
data class GnbIdLengthOption(
    val label: String,
    val gnbBitCount: Int
)