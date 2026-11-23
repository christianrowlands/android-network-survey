package com.craxiom.networksurvey.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.privacy.PrivacyZoneRules
import com.craxiom.networksurvey.ui.common.CountFormat
import com.craxiom.networksurvey.ui.common.NsInfoCard
import com.craxiom.networksurvey.ui.common.NsSectionLabel
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.util.MeasurementFormatter

/** Where the Privacy zones page leads. */
interface PrivacyZonesActions {
    fun onBack()
    fun onAdd()
    fun onEdit(zoneId: String)
    fun onDelete(zoneId: String)
    fun onOpenSsidExclusions()
}

/** The Privacy zones page on its view model, with the delete confirmation. */
@Composable
fun PrivacyZonesScreen(
    viewModel: PrivacyZonesViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenSsidExclusions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The organization's policy can change while the page is open or away
    val allowed = rememberPrivacyZonesAllowed()
    LaunchedEffect(viewModel, allowed) { viewModel.refresh() }

    PrivacyZonesContent(
        state = state,
        actions = object : PrivacyZonesActions {
            override fun onBack() = onBack()
            override fun onAdd() = onAdd()
            override fun onEdit(zoneId: String) = onEdit(zoneId)
            override fun onDelete(zoneId: String) = viewModel.remove(zoneId)
            override fun onOpenSsidExclusions() = onOpenSsidExclusions()
        },
    )
}

/**
 * The Privacy zones page, stateless: the status card, what a zone does and does not do, the zones
 * (or an empty state with Add), then how zones work. It uses the shared list screen pieces, like
 * the Watchlist and the SSID exclusion list. Deleting a zone asks first. While the organization
 * turns zones off the list is read-only: nothing can be added, opened or deleted.
 */
@Composable
fun PrivacyZonesContent(state: PrivacyZonesUiState, actions: PrivacyZonesActions, modifier: Modifier = Modifier) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TitleBar(
                title = stringResource(R.string.privacy_zones_title),
                appBarActions = if (state.canAdd) {
                    listOf(AppBarAction(icon = R.drawable.ic_add, description = R.string.privacy_zones_add, onClick = actions::onAdd))
                } else {
                    null
                },
                onBackClick = actions::onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nsContentWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = KEY_HERO) { PrivacyZonesHero(state) }
            item(key = KEY_INFO) { PrivacyZonesInfo(actions::onOpenSsidExclusions) }
            if (state.zones.isEmpty()) {
                item(key = KEY_EMPTY) { PrivacyZonesEmpty(canAdd = state.canAdd, onAdd = actions::onAdd) }
            } else {
                item(key = KEY_HEADER) {
                    NsSectionLabel(
                        text = joinedWithDot(stringResource(R.string.privacy_zones_section), CountFormat.format(state.zones.size)),
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                    )
                }
                items(items = state.zones, key = { it.id }) { zone ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        PrivacyZoneRow(
                            zone = zone,
                            inside = zone.id == state.insideZoneId,
                            editable = state.allowed,
                            onEdit = { actions.onEdit(zone.id) },
                            onDelete = { pendingDelete = zone.id },
                        )
                    }
                }
            }
            item(key = KEY_HOW) { PrivacyZonesHowTheyWork() }
        }
    }

    val deleting = pendingDelete?.let { id -> state.zones.firstOrNull { it.id == id } }?.takeIf { state.allowed }
    if (deleting != null) {
        PrivacyZoneDeleteDialog(
            name = deleting.name,
            onConfirm = { actions.onDelete(deleting.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * What a zone does: nothing saved or sent except watchlist alerts and device status without a
 * location, live screens unchanged, and the SSID exclusion list for the user's own network. The
 * rules behind it are in [PrivacyZonesHowTheyWork], so this card stays short.
 */
@Composable
private fun PrivacyZonesInfo(onOpenSsidExclusions: () -> Unit) {
    val text = listOf(
        stringResource(R.string.privacy_zones_info),
        stringResource(R.string.privacy_zones_info_ssid),
    ).joinToString(PARAGRAPH)
    NsInfoCard(text = text) {
        TextButton(onClick = onOpenSsidExclusions) { Text(stringResource(R.string.ssid_exclusion_list_title)) }
    }
}

/**
 * How zones work, under the list: the location rule (and staying paused when the phone leaves
 * without a usable location), the accuracy that widens the area checked, going forward only, and
 * which upload queues are checked again.
 */
@Composable
private fun PrivacyZonesHowTheyWork() {
    val maxAccuracy = MeasurementFormatter.formatDistance(LocalContext.current, PrivacyZoneRules.MAX_FIX_ACCURACY_METERS.toDouble())
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NsSectionLabel(text = stringResource(R.string.privacy_zones_how_title), modifier = Modifier.semantics { heading() })
        listOf(
            stringResource(R.string.privacy_zones_info_rules, maxAccuracy),
            stringResource(R.string.privacy_zones_info_queues),
        ).forEach { paragraph ->
            Text(text = paragraph, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** No zones yet: what a zone is for, and Add (unless the organization turned zones off). */
@Composable
private fun PrivacyZonesEmpty(canAdd: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = stringResource(R.string.privacy_zones_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.privacy_zones_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (canAdd) {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.privacy_zones_add)) }
        }
    }
}

private const val PARAGRAPH = "\n\n"
private const val KEY_HERO = "hero"
private const val KEY_INFO = "info"
private const val KEY_EMPTY = "empty"
private const val KEY_HEADER = "header"
private const val KEY_HOW = "how"
