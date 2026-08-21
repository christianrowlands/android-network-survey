package com.craxiom.networksurvey.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.util.copyMissionIdToClipboard
import kotlinx.coroutines.delay

/**
 * Dashboard card that surfaces the current Mission ID so the user can see and copy it. The Mission
 * ID identifies one survey session; the help dialog teaches that it is effectively the survey's ID.
 * The card appears once a real mission has started and is retained as the most recent value after
 * the survey stops, so the user can still copy it to look up their data.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MissionIdCard(
    state: MissionIdUiState,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Show a brief "NEW" badge when the Mission ID rolls while the card is already visible (for
    // example when adding a survey to an upload only session). A fresh appearance of the card is
    // signaled by its animated entry, so no badge is shown the first time.
    var showNewBadge by remember { mutableStateOf(false) }
    var previousMissionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.missionId) {
        val previous = previousMissionId
        previousMissionId = state.missionId
        if (state.missionId.isNotEmpty() && previous != null && previous != state.missionId) {
            showNewBadge = true
            delay(NEW_BADGE_DURATION_MS)
            showNewBadge = false
        }
    }

    val copyMissionId = { copyMissionIdToClipboard(context, state.missionId) }

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
                icon = R.drawable.ic_survey_monitor,
                title = stringResource(R.string.mission_id_card_title),
                onHelpClick = onHelpClick,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { copyMissionId() },
                        onLongClick = { copyMissionId() })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.missionId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = DashboardCardDefaults.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (state.active) {
                                stringResource(R.string.mission_id_caption_active)
                            } else {
                                stringResource(R.string.mission_id_caption_recent)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DashboardCardDefaults.TextSecondary,
                        )
                        if (showNewBadge) {
                            Spacer(modifier = Modifier.width(8.dp))
                            NewBadge()
                        }
                    }
                }

                IconButton(onClick = { copyMissionId() }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_content_copy),
                        contentDescription = stringResource(R.string.mission_id_copy_content_description),
                        modifier = Modifier.size(20.dp),
                        tint = DashboardCardDefaults.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewBadge() {
    Text(
        text = stringResource(R.string.mission_id_new_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private const val NEW_BADGE_DURATION_MS = 3000L
