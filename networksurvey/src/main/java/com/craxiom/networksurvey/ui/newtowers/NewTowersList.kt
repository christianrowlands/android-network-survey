package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsHeroStatusCard
import com.craxiom.networksurvey.ui.common.NsInfoCard
import com.craxiom.networksurvey.ui.common.NsSectionLabel
import com.craxiom.networksurvey.ui.common.friendlyTimeText
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The LazyColumn keys of the items above the finds; the finds use their row id. */
private const val KEY_HERO = "hero"
private const val KEY_ALERTS_OFF = "alerts_off"
private const val KEY_EMPTY = "empty"
private const val KEY_SESSION_PREFIX = "session_"

/**
 * The List view: the count and what is checked, a card when the alerts are off (with a button
 * that turns them on) or when nothing has been found, then the finds grouped by the survey session
 * that found them, newest first.
 */
@Composable
internal fun NewTowersList(
    state: NewTowersUiState,
    nowMs: Long,
    onEnableAlerts: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nsContentWidth(),
        contentPadding = PaddingValues(bottom = NsTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
    ) {
        item(key = KEY_HERO) {
            NsHeroStatusCard(
                title = if (state.count > 0) {
                    pluralStringResource(R.plurals.new_towers_found_count, state.count, state.count)
                } else {
                    stringResource(R.string.new_towers_none_found)
                },
                subtitle = stringResource(R.string.new_towers_what_is_checked),
                icon = painterResource(R.drawable.ic_cell_tower),
            )
        }
        // Until the finds are read, say nothing rather than flash an empty state
        if (!state.loaded) return@LazyColumn
        if (!state.alertsEnabled) {
            // Nothing new is found while the alerts are off, so the card offers to turn them on
            item(key = KEY_ALERTS_OFF) {
                NsInfoCard(stringResource(R.string.new_towers_alerts_off)) {
                    Button(onClick = onEnableAlerts) { Text(stringResource(R.string.new_towers_turn_on_alerts)) }
                }
            }
        } else if (state.count == 0) {
            item(key = KEY_EMPTY) { NsInfoCard(stringResource(R.string.new_towers_empty)) }
        }
        state.sessions.forEach { session ->
            item(key = KEY_SESSION_PREFIX + session.startedAt) {
                NsSectionLabel(
                    text = friendlyTimeText(session.startedAt, nowMs, relative = false),
                    modifier = Modifier
                        .padding(horizontal = NsTheme.spacing.screenGutter)
                        .padding(top = NsTheme.spacing.s)
                        .semantics { heading() },
                )
            }
            items(items = session.finds, key = { it.id }) { find ->
                NewTowerRow(
                    find = find,
                    nowMs = nowMs,
                    onClick = { onOpenDetail(find.id) },
                    modifier = Modifier.padding(horizontal = NsTheme.spacing.screenGutter),
                )
            }
        }
    }
}
