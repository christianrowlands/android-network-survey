package com.craxiom.networksurvey.ui.cellular.model

/**
 * One gNB ID length choice: [gnbBitCount] bits of the 36-bit NCI are the gNB ID and the rest
 * ([sectorBitCount]) the sector ID.
 */
data class GnbIdLengthOption(val gnbBitCount: Int) {
    val sectorBitCount: Int
        get() = NCI_BITS - gnbBitCount

    private companion object {
        const val NCI_BITS = 36
    }
}
