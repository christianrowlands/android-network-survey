@file:JvmName("ComposeFunctions")

package com.craxiom.networksurvey.ui.cellular

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.craxiom.networksurvey.util.CellularTheme

fun setContent(composeView: ComposeView, viewModel: CellularChartViewModel) {
    composeView.apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            CellularTheme {
                CellularChartComponent(viewModel = viewModel)
            }
        }
    }
}