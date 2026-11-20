package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.main.appbar.nsTopBarHeight

/** Everything the New towers page can ask for. */
interface NewTowersActions {
    fun onBack() {}
    fun onModeChange(mode: NewTowersViewMode) {}
    fun onOpenDetail(findId: Long) {}
    fun onCloseDetail() {}

    /** The detail sheet's "Show on map": the Map view, centered on the find. */
    fun onShowOnMap(findId: Long) {}
    fun onClear() {}
    fun onEnableAlerts() {}
}

/** The New towers page with its view model. */
@Composable
fun NewTowersScreen(viewModel: NewTowersViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : NewTowersActions {
            override fun onBack() = onNavigateUp()
            override fun onModeChange(mode: NewTowersViewMode) = viewModel.setMode(mode)
            override fun onOpenDetail(findId: Long) = viewModel.openDetail(findId)
            override fun onCloseDetail() = viewModel.closeDetail()
            override fun onShowOnMap(findId: Long) = viewModel.showOnMap(findId)
            override fun onClear() = viewModel.clearAll()
            override fun onEnableAlerts() = viewModel.enableAlerts()
        }
    }
    NewTowersContent(state, actions)
}

/**
 * The New towers page, stateless: the towers the new tower alerts found, as a List grouped by
 * survey session or a Map, with a detail sheet per tower and a Clear action.
 *
 * @param nowMs The time the relative ages ("Found 5 minutes ago") count from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTowersContent(
    state: NewTowersUiState,
    actions: NewTowersActions,
    nowMs: Long = System.currentTimeMillis(),
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val hasFinds = state.count > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_towers_title), modifier = Modifier.semantics { heading() }) },
                expandedHeight = nsTopBarHeight(),
                navigationIcon = {
                    IconButton(onClick = actions::onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (hasFinds) {
                        ViewModeToggle(state.mode, actions::onModeChange)
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.new_towers_clear))
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        // An empty list has nothing to map, so it always shows the list and its empty state
        if (state.mode == NewTowersViewMode.MAP && hasFinds) {
            // The map runs under the navigation bar; its legend keeps clear of it
            val layoutDirection = LocalLayoutDirection.current
            NewTowersMapView(
                finds = state.finds,
                focusFindId = state.mapFocusFindId,
                onFindClick = actions::onOpenDetail,
                selectedFindId = state.detailFindId ?: state.mapFocusFindId,
                focusRequest = state.mapFocusRequest,
                modifier = Modifier.padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection),
                ),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                NewTowersList(state, nowMs, onEnableAlerts = actions::onEnableAlerts, onOpenDetail = actions::onOpenDetail)
            }
        }
    }

    state.detailFind?.let { find ->
        NewTowerDetailSheet(find = find, onShowOnMap = { actions.onShowOnMap(find.id) }, onDismiss = actions::onCloseDetail)
    }

    if (showClearDialog) {
        NsConfirmationDialog(
            title = stringResource(R.string.new_towers_clear_title),
            message = stringResource(R.string.new_towers_clear_message),
            confirmText = stringResource(R.string.new_towers_clear),
            onConfirm = actions::onClear,
            onDismiss = { showClearDialog = false },
            destructive = true,
        )
    }
}

/** The List / Map switch in the top bar, as on the Watchlist history. */
@Composable
private fun ViewModeToggle(mode: NewTowersViewMode, onModeChange: (NewTowersViewMode) -> Unit) {
    NsSegmentedToggle(
        options = listOf(
            SegmentedOption(
                stringResource(R.string.watchlist_history_view_list),
                Icons.AutoMirrored.Filled.List,
                stringResource(R.string.watchlist_map_view_list),
            ),
            SegmentedOption(
                stringResource(R.string.watchlist_history_view_map),
                Icons.Default.Place,
                stringResource(R.string.watchlist_map_view_map),
            ),
        ),
        selectedIndex = if (mode == NewTowersViewMode.MAP) 1 else 0,
        onSelected = { onModeChange(if (it == 1) NewTowersViewMode.MAP else NewTowersViewMode.LIST) },
        fillWidth = false,
        modifier = Modifier.padding(end = 4.dp),
    )
}
