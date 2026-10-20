package com.craxiom.networksurvey.gpstest.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.SatelliteStatus
import com.craxiom.networksurvey.gpstest.ui.status.StatusPage
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.gpstest.ui.sky.SkyPage
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.common.PermissionRequest
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.openAppSettings
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.common.rememberPermissionRequest
import com.craxiom.networksurvey.ui.signals.GnssPage
import com.craxiom.networksurvey.ui.signals.SignalsStateMessage
import com.craxiom.networksurvey.ui.signals.SignalsViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The GNSS sub-tab of Signals: Status or Sky, chosen by the toggle at its top and kept in
 * [SignalsViewModel.gnssPage]. GNSS runs only while this view is resumed and nothing is in the way
 * (precise location allowed, location on); leaving the sub-tab, the tab or the app stops it. Nothing
 * is asked until the user taps the one button that fixes what is missing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun GnssScreen() {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val environment: GnssEnvironmentViewModel = viewModel(viewModelStoreOwner = activity)
    val signals: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    // The activity's Hilt factory builds the GPSTest view model.
    val signalInfo: SignalInfoViewModel = viewModel(viewModelStoreOwner = activity)

    val scanState by environment.scanState.collectAsStateWithLifecycle()
    val page by signals.gnssPage.collectAsStateWithLifecycle()
    val all: List<SatelliteStatus> by signalInfo.allStatuses.observeAsState(emptyList())
    val filtered: List<SatelliteStatus> by signalInfo.filteredStatuses.observeAsState(emptyList())

    LifecycleResumeEffect(environment) {
        environment.refresh()
        onPauseOrDispose { }
    }
    // Not collectAsStateWithLifecycle: that stops collecting when the screen stops, and this must
    // see every state change.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = scanState == GnssScanState.READY && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(signalInfo, running) {
        if (running) signalInfo.setStarted(context, true, Application.getPrefs())
        onDispose { if (running) signalInfo.setStarted(context, false, Application.getPrefs()) }
    }

    val permission = rememberPermissionRequest(GnssEnvironment.PERMISSIONS, onAnswered = environment::refresh) {
        environment.refresh()
    }

    GnssContent(
        scanState = scanState,
        permissionAccess = permission.access,
        page = page,
        heard = all.isNotEmpty(),
        filterHidesAll = all.isNotEmpty() && filtered.isEmpty(),
        actions = remember(signals, permission) { ScreenActions(context, signals, permission) },
        statusPage = { StatusPage(viewModel = signalInfo) },
        skyPage = { SkyPage(viewModel = signalInfo) },
    )
}

/** What the user can do on the GNSS view; the stateful screen wires these up. */
interface GnssActions {
    fun onSelectPage(page: GnssPage)
    fun onAllow()
    fun onOpenAppSettings()
    fun onTurnOnLocation()
    fun onShowAll()
}

/**
 * The GNSS view without its state: what is in the way, or the Status/Sky toggle over the page, or
 * a message in place of the page before the first satellite and while the filter hides them all.
 *
 * @param heard True once any satellite has been reported, filtered or not.
 * @param filterHidesAll True when satellites are reported but the constellation filter hides each.
 */
@Composable
fun GnssContent(
    scanState: GnssScanState,
    permissionAccess: PermissionAccess,
    page: GnssPage,
    heard: Boolean,
    filterHidesAll: Boolean,
    actions: GnssActions,
    modifier: Modifier = Modifier,
    statusPage: @Composable () -> Unit = {},
    skyPage: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        when (scanState) {
            GnssScanState.PERMISSION_NEEDED -> {
                val blocked = permissionAccess == PermissionAccess.BLOCKED
                SignalsStateMessage(
                    icon = R.drawable.ic_info,
                    text = stringResource(R.string.gnss_state_permission_body),
                    action = stringResource(if (blocked) R.string.open_settings else R.string.survey_location_allow),
                    onAction = if (blocked) actions::onOpenAppSettings else actions::onAllow,
                )
            }

            GnssScanState.LOCATION_OFF -> SignalsStateMessage(
                icon = R.drawable.ic_location_off,
                text = stringResource(R.string.gnss_state_location_off_body),
                action = stringResource(R.string.survey_location_turn_on),
                onAction = actions::onTurnOnLocation,
            )

            GnssScanState.READY -> {
                PageToggle(page, actions::onSelectPage)
                Box(Modifier.weight(1f)) {
                    when {
                        !heard -> SignalsStateMessage(icon = R.drawable.ic_gnss, text = stringResource(R.string.signals_gnss_empty))
                        filterHidesAll -> SignalsStateMessage(
                            icon = R.drawable.ic_filter,
                            text = stringResource(R.string.gnss_filter_hides_all),
                            action = stringResource(R.string.gnss_filter_show_all),
                            onAction = actions::onShowAll,
                        )

                        page == GnssPage.STATUS -> statusPage()
                        else -> skyPage()
                    }
                }
            }
        }
    }
}

/** The compact Status | Sky switch, centered over the page. */
@Composable
private fun PageToggle(page: GnssPage, onSelect: (GnssPage) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        NsSegmentedToggle(
            options = GnssPage.entries.map { SegmentedOption(label = stringResource(it.label)) },
            selectedIndex = page.ordinal,
            onSelected = { onSelect(GnssPage.entries[it]) },
            fillWidth = false,
        )
    }
}

/** Wires [GnssActions] to the view models, the permission request and the system screens. */
private class ScreenActions(
    private val context: Context,
    private val signals: SignalsViewModel,
    private val permission: PermissionRequest,
) : GnssActions {

    override fun onSelectPage(page: GnssPage) = signals.selectGnssPage(page)

    override fun onAllow() = permission.request()

    override fun onOpenAppSettings() = openAppSettings(context)

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onShowAll() = clearGnssFilter()
}
