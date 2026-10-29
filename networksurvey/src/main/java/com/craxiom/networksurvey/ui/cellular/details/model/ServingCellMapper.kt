package com.craxiom.networksurvey.ui.cellular.details.model

import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.GsmRecordData
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.LteRecordData
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.NrRecordData
import com.craxiom.messaging.UmtsRecord
import com.craxiom.messaging.UmtsRecordData
import com.craxiom.networksurvey.constants.LteMessageConstants
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.NrRecordWrapper
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.NsUtils
import com.craxiom.networksurvey.util.ParserUtils
import java.util.Locale

/**
 * Turns a serving cell record into the serving cell card: every value the old details screen
 * showed, per technology, without labels or units (the screen adds those).
 */
object ServingCellMapper {

    /**
     * Some devices report these for an unset UMTS RSCP instead of the API's unset value (Pixels
     * -120, Samsung -24). Both are valid readings in theory, but showing them is worse.
     */
    private const val RSCP_UNSET_VALUE_120 = -120
    private const val RSCP_UNSET_VALUE_24 = -24

    // The chart ranges the old details screen used, per technology.
    private const val GSM_CHART_MIN = -110
    private const val GSM_CHART_MAX = -46
    private const val UMTS_CHART_MIN = -110
    private const val UMTS_CHART_MAX = -62
    private const val LTE_CHART_MIN = -125
    private const val LTE_CHART_MAX = -65
    private const val NR_CHART_MIN = -125
    private const val NR_CHART_MAX = -73

    private const val FREQUENCY_FORMAT = "%.3f"

    /**
     * @return The card for [record], or null for a technology the card does not show (CDMA, none).
     */
    fun map(record: CellularRecordWrapper): ServingCellDetails? = when (record.cellularProtocol) {
        CellularProtocol.GSM -> gsm((record.cellularRecord as GsmRecord).data)
        CellularProtocol.UMTS -> umts((record.cellularRecord as UmtsRecord).data)
        CellularProtocol.LTE -> lte((record.cellularRecord as LteRecord).data)
        CellularProtocol.NR -> nr(record as NrRecordWrapper)
        else -> null
    }

    private fun gsm(data: GsmRecordData): ServingCellDetails {
        val rssi = if (data.hasSignalStrength()) data.signalStrength.value.toInt() else null
        val protocol = CellularProtocol.GSM
        return ServingCellDetails(
            protocol = protocol,
            carrier = data.provider,
            signals = listOf(signal(CellFieldKind.RSSI, rssi, ValueUnit.DBM)),
            identity = listOf(
                CellField(CellFieldKind.PLMN, plmn(data.hasPlmn(), data.plmn.value, data.hasMcc(), data.mcc.value, data.hasMnc(), data.mnc.value)),
                CellField(CellFieldKind.LAC, if (data.hasLac()) data.lac.value.toString() else null),
                CellField(CellFieldKind.CID, if (data.hasCi()) data.ci.value.toString() else null),
                CellField(CellFieldKind.ARFCN, if (data.hasArfcn()) data.arfcn.value.toString() else null),
                CellField(CellFieldKind.BSIC, if (data.hasBsic()) ParserUtils.bsicToString(data.bsic.value).ifEmpty { null } else null),
            ),
            band = null,
            chart = ChartSample(protocol, CellFieldKind.RSSI, GSM_CHART_MIN, GSM_CHART_MAX, rssi, if (data.hasCi()) data.ci.value else null),
        )
    }

    private fun umts(data: UmtsRecordData): ServingCellDetails {
        val rssi = if (data.hasSignalStrength()) data.signalStrength.value.toInt() else null
        val rscp = (if (data.hasRscp()) data.rscp.value.toInt() else null)
            ?.takeUnless { it == RSCP_UNSET_VALUE_120 || it == RSCP_UNSET_VALUE_24 }
        val cid = if (data.hasCid()) data.cid.value else null
        val protocol = CellularProtocol.UMTS
        return ServingCellDetails(
            protocol = protocol,
            carrier = data.provider,
            signals = listOf(
                signal(CellFieldKind.RSSI, rssi, ValueUnit.DBM),
                signal(CellFieldKind.RSCP, rscp, ValueUnit.DBM),
            ),
            identity = listOf(
                CellField(CellFieldKind.PLMN, plmn(data.hasPlmn(), data.plmn.value, data.hasMcc(), data.mcc.value, data.hasMnc(), data.mnc.value)),
                CellField(CellFieldKind.LAC, if (data.hasLac()) data.lac.value.toString() else null),
                CellField(CellFieldKind.CID, cid?.toString()),
                CellField(CellFieldKind.RNC, cid?.let { CalculationUtils.getUmtsRncFromCid(it).toString() }),
                CellField(CellFieldKind.SHORT_CID, cid?.let { CalculationUtils.getUmtsShortCellIdFromCid(it).toString() }),
                CellField(CellFieldKind.UARFCN, if (data.hasUarfcn()) data.uarfcn.value.toString() else null),
                CellField(CellFieldKind.PSC, if (data.hasPsc()) data.psc.value.toString() else null),
            ),
            band = null,
            // The chart has always plotted the UMTS RSSI, so it is labeled as such.
            chart = ChartSample(protocol, CellFieldKind.RSSI, UMTS_CHART_MIN, UMTS_CHART_MAX, rssi, cid),
        )
    }

    private fun lte(data: LteRecordData): ServingCellDetails {
        val rsrp = if (data.hasRsrp()) data.rsrp.value.toInt() else null
        val eci = if (data.hasEci()) data.eci.value else null
        val bandNumber = if (data.hasEarfcn()) CellularUtils.downlinkEarfcnToBand(data.earfcn.value) else -1
        val band = bandNumber.takeIf { it != -1 }
        val protocol = CellularProtocol.LTE
        return ServingCellDetails(
            protocol = protocol,
            carrier = data.provider,
            signals = listOf(
                signal(CellFieldKind.RSRP, rsrp, ValueUnit.DBM),
                signal(CellFieldKind.RSRQ, if (data.hasRsrq()) data.rsrq.value.toInt() else null, ValueUnit.DB),
                signal(CellFieldKind.SNR, if (data.hasSnr()) data.snr.value.toInt() else null, ValueUnit.DB),
            ),
            identity = listOf(
                CellField(CellFieldKind.PLMN, plmn(data.hasPlmn(), data.plmn.value, data.hasMcc(), data.mcc.value, data.hasMnc(), data.mnc.value)),
                CellField(CellFieldKind.TAC, if (data.hasTac()) data.tac.value.toString() else null),
                CellField(CellFieldKind.CID, eci?.toString()),
                CellField(CellFieldKind.ENB, eci?.let { CalculationUtils.getEnodebIdFromCellId(it).toString() }),
                CellField(CellFieldKind.SECTOR, eci?.let { CalculationUtils.getSectorIdFromCellId(it).toString() }),
                CellField(CellFieldKind.PCI, if (data.hasPci()) formatPci(data.pci.value) else null),
                CellField(CellFieldKind.EARFCN, if (data.hasEarfcn()) data.earfcn.value.toString() else null),
                CellField(
                    CellFieldKind.BAND,
                    band?.let { lteBandDisplay(it) },
                    bandTarget = band?.let { BandTapTarget.of(BandTechnology.LTE, it) },
                ),
                CellField(CellFieldKind.BANDWIDTH, LteMessageConstants.getLteBandwidth(data.lteBandwidth).ifEmpty { null }, ValueUnit.MHZ),
                CellField(CellFieldKind.TA, if (data.hasTa()) data.ta.value.toString() else null),
                // CQI has no scale to draw a bar on, so it sits with the other plain values.
                CellField(CellFieldKind.CQI, if (data.hasCqi()) data.cqi.value.toString() else null),
            ),
            band = band?.toString(),
            chart = ChartSample(protocol, CellFieldKind.RSRP, LTE_CHART_MIN, LTE_CHART_MAX, rsrp, eci),
        )
    }

    private fun nr(wrapper: NrRecordWrapper): ServingCellDetails {
        val data = (wrapper.cellularRecord as NrRecord).data
        val ssRsrp = if (data.hasSsRsrp()) data.ssRsrp.value.toInt() else null
        val narfcn = if (data.hasNarfcn()) data.narfcn.value else -1
        val bands = CellularUtils.resolveNrBands(wrapper.bands, narfcn)
        val protocol = CellularProtocol.NR
        return ServingCellDetails(
            protocol = protocol,
            carrier = data.provider,
            signals = nrSignals(data),
            identity = listOf(
                CellField(CellFieldKind.PLMN, plmn(data.hasPlmn(), data.plmn.value, data.hasMcc(), data.mcc.value, data.hasMnc(), data.mnc.value)),
                CellField(CellFieldKind.TAC, if (data.hasTac()) data.tac.value.toString() else null),
                CellField(CellFieldKind.CID, if (data.hasNci()) data.nci.value.toString() else null),
                CellField(CellFieldKind.PCI, if (data.hasPci()) formatPci(data.pci.value) else null),
                CellField(CellFieldKind.NARFCN, if (data.hasNarfcn()) narfcn.toString() else null),
                CellField(
                    CellFieldKind.BAND,
                    CellularUtils.formatNrBands(wrapper.bands, narfcn).ifEmpty { null },
                    bandTarget = nrBandTarget(bands),
                ),
                CellField(CellFieldKind.FREQUENCY, if (data.hasNarfcn()) formatNrFrequency(narfcn) else null, ValueUnit.MHZ),
                CellField(CellFieldKind.TA, if (data.hasTa()) data.ta.value.toString() else null),
            ),
            band = bands?.takeIf { it.isNotEmpty() }?.joinToString("/") { "n$it" },
            chart = ChartSample(
                protocol, CellFieldKind.SS_RSRP, NR_CHART_MIN, NR_CHART_MAX, ssRsrp,
                if (data.hasNci()) data.nci.value.toInt() else null,
            ),
        )
    }

    /** @return The SS-RSRP, SS-RSRQ and SS-SINR of an NR cell, with their buckets. */
    fun nrSignals(data: NrRecordData): List<CellField> {
        return listOf(
            signal(CellFieldKind.SS_RSRP, if (data.hasSsRsrp()) data.ssRsrp.value.toInt() else null, ValueUnit.DBM),
            signal(CellFieldKind.SS_RSRQ, if (data.hasSsRsrq()) data.ssRsrq.value.toInt() else null, ValueUnit.DB),
            signal(CellFieldKind.SS_SINR, if (data.hasSsSinr()) data.ssSinr.value.toInt() else null, ValueUnit.DB),
        )
    }

    /** @return A signal value with its bucket and its bar fraction, on one scale. */
    fun signal(kind: CellFieldKind, value: Int?, unit: ValueUnit): CellField = CellField(
        kind = kind,
        value = value?.toString(),
        unit = unit,
        bucket = CellularSignalBuckets.bucket(kind, value),
        fraction = CellularSignalBuckets.fraction(kind, value),
    )

    /** @return The PCI with its primary and secondary sync sequences, e.g. "146 (2/48)". */
    fun formatPci(pci: Int): String =
        "$pci (${CalculationUtils.getPrimarySyncSequence(pci)}/${CalculationUtils.getSecondarySyncSequence(pci)})"

    /** @return The NR downlink frequency in MHz ("3709.920"), or null outside the valid range. */
    fun formatNrFrequency(narfcn: Int): String? {
        val mhz = CellularUtils.narfcnToFrequencyMhz(narfcn)
        return if (mhz <= 0) null else String.format(Locale.US, FREQUENCY_FORMAT, mhz)
    }

    /** @return The NR band numbers as a tap target, or null when there are none. */
    fun nrBandTarget(bands: IntArray?): BandTapTarget? =
        bands?.takeIf { it.isNotEmpty() }?.let { BandTapTarget(BandTechnology.NR, it.toList()) }

    /** @return "66 (AWS-3)", or the number alone when the band has no name. */
    private fun lteBandDisplay(band: Int): String =
        CellularUtils.getLteBandName(band)?.let { "$band ($it)" } ?: band.toString()

    /**
     * @return "MCC-MNC", preferring the PLMN string (it keeps the MNC's leading zeros), or null when
     * neither is known.
     */
    private fun plmn(hasPlmn: Boolean, plmn: String, hasMcc: Boolean, mcc: Int, hasMnc: Boolean, mnc: Int): String? {
        val mccText = if (hasPlmn) NsUtils.extractMccFromPlmn(plmn) else if (hasMcc) mcc.toString() else ""
        val mncText = if (hasPlmn) NsUtils.extractMncFromPlmn(plmn) else if (hasMnc) mnc.toString() else ""
        return if (mccText.isEmpty() && mncText.isEmpty()) null else "$mccText-$mncText"
    }
}
