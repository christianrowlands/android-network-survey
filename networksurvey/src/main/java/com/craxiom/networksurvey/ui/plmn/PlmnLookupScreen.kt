package com.craxiom.networksurvey.ui.plmn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.plmn.PlmnResult
import com.craxiom.networksurvey.ui.plmn.components.PlmnGroupRow
import com.craxiom.networksurvey.ui.plmn.components.PlmnModeTabs
import com.craxiom.networksurvey.ui.plmn.components.PlmnResolveFields
import com.craxiom.networksurvey.ui.plmn.components.PlmnResultStrip
import com.craxiom.networksurvey.ui.plmn.components.PlmnRow
import com.craxiom.networksurvey.ui.plmn.components.PlmnSearchField
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Top-level Compose surface for the "PLMN lookup" drawer screen.
 *
 * Wraps three vertical regions:
 *  1. Mode tabs (Search / Resolve).
 *  2. Either a search field or two numeric MCC/MNC cards.
 *  3. A result strip + scrollable list, or nothing when there's no input.
 *
 * State + side effects live on [PlmnLookupViewModel]; this composable is mostly layout glue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlmnLookupScreen(
    modifier: Modifier = Modifier,
    viewModel: PlmnLookupViewModel = viewModel(),
) {
    val mode by viewModel.mode.collectAsState()
    val query by viewModel.query.collectAsState()
    val mcc by viewModel.mcc.collectAsState()
    val mnc by viewModel.mnc.collectAsState()
    val results by viewModel.results.collectAsState()
    val expandedPlmns by viewModel.expandedPlmns.collectAsState()
    val sortKey by viewModel.sortKey.collectAsState()
    val showSortSheet by viewModel.showSortSheet.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchFocusRequester = remember { FocusRequester() }
    val mccFocusRequester = remember { FocusRequester() }
    val mncFocusRequester = remember { FocusRequester() }

    LaunchedEffect(mode) {
        // Compose tears down the previous mode's subtree and recomposes the new one in the next
        // frame. The new FocusRequester is not yet attached when this effect runs, so wait one
        // frame before calling requestFocus(); otherwise the call silently no-ops.
        withFrameNanos { }
        when (mode) {
            PlmnMode.Search -> runCatching { searchFocusRequester.requestFocus() }
            PlmnMode.Resolve -> {
                val target = when {
                    mcc.isEmpty() -> mccFocusRequester
                    mnc.isEmpty() -> mncFocusRequester
                    else -> mccFocusRequester
                }
                runCatching { target.requestFocus() }
            }
        }
    }

    // Auto-expand the single group in Resolve mode when there's exactly one match.
    val (groups, datasetVersion, truncated) = remember(results, sortKey) {
        when (val r = results) {
            is PlmnResult.Loaded -> Triple(
                sortGroups(groupByPlmn(r.records), sortKey),
                r.datasetVersion,
                r.truncated
            )

            else -> Triple(emptyList(), null, false)
        }
    }
    LaunchedEffect(groups) {
        viewModel.autoExpandIfSingleGroup(groups.map { it.plmn })
    }

    val hasInput = when (mode) {
        PlmnMode.Search -> query.isNotBlank()
        PlmnMode.Resolve -> mcc.isNotEmpty() || mnc.isNotEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlmnModeTabs(mode = mode, onModeChanged = viewModel::setMode)
            when (mode) {
                PlmnMode.Search -> PlmnSearchField(
                    value = query,
                    onChange = viewModel::setQuery,
                    focusRequester = searchFocusRequester,
                )

                PlmnMode.Resolve -> PlmnResolveFields(
                    mcc = mcc,
                    mnc = mnc,
                    onMcc = viewModel::setMcc,
                    onMnc = viewModel::setMnc,
                    mccFocusRequester = mccFocusRequester,
                    mncFocusRequester = mncFocusRequester,
                )
            }
        }

        if (!hasInput) return@Column

        PlmnResultStrip(
            count = groups.sumOf { it.records.size },
            datasetVersion = datasetVersion,
            isLoading = results is PlmnResult.Loading,
            sortKey = sortKey,
            onSortClick = {
                keyboardController?.hide()
                viewModel.openSortSheet()
            }
        )

        ResultBody(
            results = results,
            groups = groups,
            expandedPlmns = expandedPlmns,
            truncated = truncated,
            onToggle = viewModel::toggleExpanded,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showSortSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSortSheet,
            sheetState = sheetState,
        ) {
            PlmnSortSheetContent(
                current = sortKey,
                onPick = {
                    viewModel.setSortKey(it)
                    viewModel.dismissSortSheet()
                }
            )
        }
    }
}

@Composable
private fun ResultBody(
    results: PlmnResult,
    groups: List<PlmnGroup>,
    expandedPlmns: Set<String>,
    truncated: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // While we are still in the debounce window (results=Idle even though hasInput=true)
        // OR while a request is genuinely in flight with no prior data, show the spinner instead
        // of the "No PLMNs match." copy. This collapses the otherwise-visible 200 ms blank flash
        // between a keystroke and the first emitted Loading state.
        (results is PlmnResult.Idle || results is PlmnResult.Loading) && groups.isEmpty() ->
            LoadingCenter(modifier)

        results is PlmnResult.Offline -> StatusCenter(
            stringResource(R.string.plmn_lookup_offline),
            modifier
        )

        results is PlmnResult.TransientFailure -> StatusCenter(
            stringResource(R.string.plmn_lookup_transient_failure),
            modifier
        )

        groups.isEmpty() -> EmptyState(modifier)
        else -> ResultList(
            groups = groups,
            expandedPlmns = expandedPlmns,
            truncated = truncated,
            onToggle = onToggle,
            modifier = modifier,
        )
    }
}

@Composable
private fun ResultList(
    groups: List<PlmnGroup>,
    expandedPlmns: Set<String>,
    truncated: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(groups, key = { it.plmn }) { group ->
            if (group.records.size == 1) {
                PlmnRow(record = group.records.first())
            } else {
                PlmnGroupRow(
                    group = group,
                    expanded = group.plmn in expandedPlmns,
                    onToggle = { onToggle(group.plmn) },
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
        }
        if (truncated) {
            item {
                Text(
                    text = stringResource(R.string.plmn_lookup_truncated_hint),
                    style = TextStyle(fontSize = 12.sp),
                    color = WifiTokens.InkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingCenter(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun StatusCenter(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = TextStyle(fontSize = 13.sp),
            color = WifiTokens.InkFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.plmn_lookup_empty_line1),
            style = TextStyle(fontSize = 13.sp),
            color = WifiTokens.InkFaint,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.plmn_lookup_empty_line2),
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            color = WifiTokens.InkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlmnSortSheetContent(
    current: PlmnSortKey,
    onPick: (PlmnSortKey) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)) {
        Text(
            text = stringResource(R.string.plmn_sort_sheet_title),
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        PlmnSortOption(R.string.plmn_sort_mcc_mnc, current == PlmnSortKey.MccMnc) {
            onPick(PlmnSortKey.MccMnc)
        }
        PlmnSortOption(R.string.plmn_sort_country, current == PlmnSortKey.Country) {
            onPick(PlmnSortKey.Country)
        }
        PlmnSortOption(R.string.plmn_sort_provider, current == PlmnSortKey.Provider) {
            onPick(PlmnSortKey.Provider)
        }
    }
}

@Composable
private fun PlmnSortOption(textRes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(textRes),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) WifiTokens.SsidAccent else MaterialTheme.colorScheme.onSurface,
        )
    }
}
