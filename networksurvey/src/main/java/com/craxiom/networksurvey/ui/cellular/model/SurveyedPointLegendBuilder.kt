package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.util.CalculationUtils

/** Accumulates the identities in view for the provider, cell, and area legends. */
internal class SurveyedPointLegendBuilder(private val mode: SurveyedPointColorMode) {
    private val counts = LinkedHashMap<String, Int>()
    private val labels = HashMap<String, String>()
    val plmns = HashSet<String>()
    val size: Int get() = counts.size

    fun add(point: SurveyedPointEntity) {
        point.plmn?.let { plmns.add(it) }
        val key = when (mode) {
            SurveyedPointColorMode.PROVIDER -> point.plmn
            SurveyedPointColorMode.CELL -> point.cellId
            SurveyedPointColorMode.AREA -> SurveyedPointFeatures.areaKey(point.plmn, point.area)
            else -> null
        } ?: return
        counts[key] = (counts[key] ?: 0) + 1
        if (key !in labels) labelFor(point)?.let { labels[key] = it }
    }

    fun add(key: String?, count: Int) {
        if (key == null) return
        if (mode == SurveyedPointColorMode.PROVIDER) plmns.add(key)
        counts[key] = (counts[key] ?: 0) + count
    }

    fun top(limit: Int): List<SurveyedPointLegendKey> =
        counts.entries.sortedByDescending { it.value }.take(limit)
            .map { SurveyedPointLegendKey(it.key, labels[it.key], it.value) }

    private fun labelFor(point: SurveyedPointEntity): String? = when (mode) {
        SurveyedPointColorMode.PROVIDER, SurveyedPointColorMode.AREA -> point.provider
        SurveyedPointColorMode.CELL -> if (point.protocol == SurveyedPointEntity.PROTOCOL_LTE && point.cid in 0..Int.MAX_VALUE) {
            "eNB ${CalculationUtils.getEnodebIdFromCellId(point.cid.toInt())} " +
                    "Sector ${CalculationUtils.getSectorIdFromCellId(point.cid.toInt())}"
        } else null

        else -> null
    }
}

