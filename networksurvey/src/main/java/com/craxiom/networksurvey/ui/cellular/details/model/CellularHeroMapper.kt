package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.model.NetworkTechnologyInfo
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.util.CellularBandwidthUtils
import com.craxiom.networksurvey.util.TelephonyStateUtils

/**
 * The hero line, the pills and the carrier aggregation chips, from the technology snapshot the
 * cellular controller sends with every scan. Every decision is [TelephonyStateUtils]'s; this only
 * shapes its answers for the screen.
 */
object CellularHeroMapper {

    /** @return The hero line for [info]. */
    fun hero(info: NetworkTechnologyInfo): CellularHero {
        val hero = TelephonyStateUtils.deriveHero(info.nrMode(), info.baseDataRat(), info.registrationRows())
        return when (hero.state()) {
            TelephonyStateUtils.HeroState.NR_STANDALONE -> CellularHero(HeroKind.NR_STANDALONE)
            TelephonyStateUtils.HeroState.NR_NON_STANDALONE -> CellularHero(HeroKind.NR_NON_STANDALONE)
            TelephonyStateUtils.HeroState.DATA_RAT -> CellularHero(HeroKind.DATA_RAT, CalculationUtils.getNetworkType(hero.rat()))
            TelephonyStateUtils.HeroState.NO_SERVICE -> CellularHero(HeroKind.NO_SERVICE)
            TelephonyStateUtils.HeroState.UNKNOWN, null -> CellularHero(HeroKind.UNKNOWN)
        }
    }

    /**
     * The pills support a live hero. On No service and Unknown they usually carry only noise the
     * hero already says, so they hide unless one still has a real value (on Android 8 to 11 during
     * Wi-Fi calling the hero is Unknown while the data type is IWLAN). On every live state all
     * three show, so nothing appears or disappears during normal use.
     *
     * @param voiceNone The localized "None" the controller uses for no voice bearer.
     * @return The pills, or null to hide the row.
     */
    fun pills(info: NetworkTechnologyInfo, voiceNone: String): CellularPills? {
        val hero = TelephonyStateUtils.deriveHero(info.nrMode(), info.baseDataRat(), info.registrationRows())
        val noise = setOf(NetworkSurveyConstants.UNKNOWN, voiceNone)
        if (!TelephonyStateUtils.shouldShowPillRow(hero.state(), info.voiceDisplay(), info.dataDisplay(), noise)) {
            return null
        }
        // Branding gates on the raw override type, so it reads the same on every API level and scan
        // path; overrideDisplay itself is "N/A" or "Unknown" on some paths.
        val branding = info.overrideDisplay().takeIf { TelephonyStateUtils.isBrandingOverride(info.overrideNetworkType()) }
        // A missing voice value hides the row and a missing data value hides its pill, as before.
        val voice = info.voiceDisplay() ?: return null
        return CellularPills(voice, info.dataDisplay(), branding)
    }

    /**
     * @return One chip per valid component carrier with the total, or null with fewer than two
     * (no aggregation to show).
     */
    fun carrierAggregation(cellBandwidthsKhz: IntArray?): CarrierAggregation? {
        val valid = CellularBandwidthUtils.validBandwidthsKhz(cellBandwidthsKhz)
        if (valid.size <= 1) return null
        return CarrierAggregation(
            carriersMhz = valid.map { CellularBandwidthUtils.formatBandwidthMhz(it) },
            totalMhz = CellularBandwidthUtils.formatBandwidthMhz(CellularBandwidthUtils.aggregateBandwidthKhz(valid)),
        )
    }
}
