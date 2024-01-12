package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.ui.SignalChart

/**
 * A Compose component that displays the cellular signals chart.
 */
@Composable
internal fun CellularChartComponent(
    viewModel: CellularChartViewModel
) {
    val chartTitle by viewModel.chartTitle.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier.padding(padding),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors()
    ) {
        Box(Modifier.padding(padding)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(padding),
            ) {
                Text(
                    text = "$chartTitle (Last 2 Minutes)",
                    style = MaterialTheme.typography.titleMedium
                )
                SignalChart(viewModel)
            }
        }
    }
}

private val padding = 6.dp
