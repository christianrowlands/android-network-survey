package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat

private const val PERCENT_PER_FRACTION = 100.0

/** [percent] (0 to 100) as the phone's language writes a percentage: "20%", "20 %" or "%20". */
@Composable
fun percentText(percent: Int): String =
    NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(percent / PERCENT_PER_FRACTION)
