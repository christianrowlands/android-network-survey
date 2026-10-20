package com.craxiom.networksurvey.ui.cellular.details

import android.content.Context
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.ui.SignalChart
import com.craxiom.networksurvey.ui.cellular.bands.BandLookupDialog
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.annotatedStringResource
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.openAppSettings
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.common.openSystemSettings
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.signals.SignalsViewModel
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation
import kotlinx.coroutines.flow.filterNotNull

/**
 * The Cellular sub-tab of Signals: binds to the survey service while it shows, re-checks the
 * permissions, location and airplane mode on every resume, and asks for nothing until the user taps
 * the one button that fixes what is missing. It tells the Signals strip and the map which SIM it
 * shows, and hands its serving cell to the map for the map's first visit.
 */
@Composable
fun CellularDetailsScreen() {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val viewModel: CellularDetailsViewModel = viewModel(viewModelStoreOwner = activity)
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    val sharedViewModel: SharedViewModel = viewModel(viewModelStoreOwner = activity)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ServiceConnectionHandler(viewModel::onServiceChanged)
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        viewModel.setChartsPaused(false)
        onPauseOrDispose { viewModel.setChartsPaused(true) }
    }

    val viewedSim = if (state.pagesKnown) state.pages.getOrNull(state.selectedPage)?.sim else null
    LaunchedEffect(viewedSim) { signalsViewModel.setViewedSim(viewedSim) }
    LaunchedEffect(viewModel) {
        viewModel.latestServingCell.filterNotNull().collect(sharedViewModel::updateLatestServingCellInfo)
    }

    val permission = rememberPermissionRequest(CellularEnvironment.PERMISSIONS, onAnswered = viewModel::refresh) {
        viewModel.refresh()
    }
    var info by rememberSaveable { mutableStateOf<CellularInfo?>(null) }
    var band by remember { mutableStateOf<BandTapTarget?>(null) }

    CellularDetailsContent(
        state = state,
        permissionAccess = permission.access,
        actions = remember(viewModel, permission) {
            ScreenActions(context, viewModel, permission, onInfo = { info = it }, onBand = { band = it })
        },
        chart = { subscriptionId -> viewModel.chartFor(subscriptionId)?.let { SignalChart(it) } },
    )

    info?.let { CellularInfoDialog(it, state.sim.serving?.protocol) { info = null } }
    band?.let { BandLookupDialog(it) { band = null } }
}

/** The explanation behind an info button; the terms follow the serving cell's technology. */
@Composable
private fun CellularInfoDialog(info: CellularInfo, protocol: CellularProtocol?, onDismiss: () -> Unit) {
    val (title, message) = when (info) {
        CellularInfo.TECHNOLOGY -> R.string.network_technology_title to R.string.network_technology_explanation
        CellularInfo.NR_SECONDARY -> R.string.nr_secondary_info_description to R.string.nr_secondary_cell_explanation
        CellularInfo.TERMS -> when (protocol) {
            CellularProtocol.GSM -> R.string.gsm_info_description to R.string.gsm_cellular_terms_explanation
            CellularProtocol.UMTS -> R.string.umts_info_description to R.string.umts_cellular_terms_explanation
            CellularProtocol.NR -> R.string.nr_info_description to R.string.nr_cellular_terms_explanation
            else -> R.string.lte_info_description to R.string.lte_cellular_terms_explanation
        }
    }
    NsMessageDialog(
        title = stringResource(title),
        message = annotatedStringResource(message),
        onDismiss = onDismiss,
        icon = Icons.Outlined.Info,
    )
}

/** Wires [CellularDetailsActions] to the view model, the permission request and the system screens. */
private class ScreenActions(
    private val context: Context,
    private val viewModel: CellularDetailsViewModel,
    private val permission: PermissionRequest,
    private val onInfo: (CellularInfo) -> Unit,
    private val onBand: (BandTapTarget) -> Unit,
) : CellularDetailsActions {

    override fun onSelectPage(index: Int) = viewModel.selectPage(index)

    override fun onAllow() = permission.request()

    override fun onOpenAppSettings() = openAppSettings(context)

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onOpenAirplaneSettings() = openSystemSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    override fun onInfo(info: CellularInfo) = onInfo.invoke(info)

    override fun onBandClick(target: BandTapTarget) = onBand(target)

    override fun onCopy(label: String, value: String) =
        copyWithSystemConfirmation(context, label, value, context.getString(R.string.value_copied_to_clipboard))
}
