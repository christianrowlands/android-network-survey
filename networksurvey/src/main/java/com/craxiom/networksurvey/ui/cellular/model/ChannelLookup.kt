package com.craxiom.networksurvey.ui.cellular.model

/**
 * The result of looking up a channel number, for the Channel to Band calculator.
 *
 * Kept as data rather than as a formatted string so the composable owns every user visible word
 * and the view model stays free of resources it cannot localise.
 *
 * @property frequencyMhz the frequency the channel works out to, which is known whenever the
 *   channel is in range even if no band contains it. This is the reason the type exists: a
 *   channel number always yields a frequency, and only sometimes yields a band.
 * @property bandNumbers every band containing the channel, ascending. Empty when the channel is
 *   valid but falls in no known band, which is a real answer rather than an error.
 */
data class ChannelLookup(
    val channel: Int,
    val frequencyMhz: Double?,
    val bandNumbers: List<Int>,
    val outOfRange: Boolean = false,
) {
    val hasBand: Boolean get() = bandNumbers.isNotEmpty()
    val isAmbiguous: Boolean get() = bandNumbers.size > 1
}
