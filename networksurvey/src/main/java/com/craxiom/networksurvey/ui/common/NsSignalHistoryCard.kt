package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.craxiom.networksurvey.ui.ASignalChartViewModel
import com.craxiom.networksurvey.ui.SignalChart

/**
 * A details page's signal history: [title] over the [SignalChart] (two minutes of readings).
 * Without a chart (a test) only the title shows.
 */
@Composable
fun NsSignalHistoryCard(title: String, chart: ASignalChartViewModel?, modifier: Modifier = Modifier) {
    NsDetailsCardFrame(modifier = modifier) {
        NsCardHeader(text = title)
        chart?.let { SignalChart(it) }
    }
}
