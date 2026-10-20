package com.craxiom.networksurvey.ui.nsanalytics

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The full-color NS Analytics logo, centered at the top of the NS Analytics page. It is drawn
 * with [Image] so its four brand colors are never tinted, and it carries no content description
 * because the page title already names NS Analytics.
 */
@Composable
fun NsAnalyticsHeader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = NsTheme.spacing.s), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_ns_analytics_color),
            contentDescription = null,
            modifier = Modifier.size(LOGO_SIZE),
        )
    }
}

private val LOGO_SIZE = 64.dp
