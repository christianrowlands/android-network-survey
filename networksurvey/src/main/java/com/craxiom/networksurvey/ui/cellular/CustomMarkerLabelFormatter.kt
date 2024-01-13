package com.craxiom.networksurvey.ui.cellular


import android.text.Spannable
import android.text.style.ForegroundColorSpan
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.extension.appendCompat
import com.patrykandpatrick.vico.core.extension.transformToSpannable
import com.patrykandpatrick.vico.core.marker.Marker
import com.patrykandpatrick.vico.core.marker.MarkerLabelFormatter

/**
 * A [MarkerLabelFormatter] that displays the the provided text.
 */
class CustomMarkerLabelFormatter(private val colorCode: Boolean = true, private val text: String) :
    MarkerLabelFormatter {
    override fun getLabel(
        markedEntries: List<Marker.EntryModel>,
        chartValues: ChartValues,
    ): CharSequence =
        markedEntries.transformToSpannable { model ->
            if (colorCode) {
                appendCompat(
                    text,
                    ForegroundColorSpan(model.color),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else {
                append(text)
            }
        }
}
