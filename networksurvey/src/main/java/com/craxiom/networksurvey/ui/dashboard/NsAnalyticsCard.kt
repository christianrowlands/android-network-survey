package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.nsanalytics.NsAnalyticsStatusCard

/**
 * Dashboard wrapper for the NS Analytics status card. Includes the standard
 * card header with help icon and wraps the reusable [NsAnalyticsStatusCard].
 */
@Composable
fun NsAnalyticsCard(
    state: NsAnalyticsUiState,
    onToggleSurvey: () -> Unit,
    onOpenDetails: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = DashboardCardDefaults.margin, vertical = 4.dp),
            shape = DashboardCardDefaults.shape,
            elevation = DashboardCardDefaults.elevation(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            DashboardCardHeader(
                icon = R.drawable.ic_ns_analytics,
                title = stringResource(R.string.ns_analytics),
                onHelpClick = onHelpClick,
            )

            NsAnalyticsStatusCard(
                isSurveyActive = state.isSurveyActive,
                surveyStartTime = state.surveyStartTime,
                cellularCount = state.cellularCount,
                wifiCount = state.wifiCount,
                bluetoothCount = state.bluetoothCount,
                gnssCount = state.gnssCount,
                phoneStateCount = state.phoneStateCount,
                onToggleSurvey = onToggleSurvey,
                onOpenDetails = onOpenDetails,
                modifier = Modifier.padding(0.dp),
            )
        }
    }
}
