package com.craxiom.networksurvey.ui.map

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The widest the card stack over the map grows; map notices line up with it. */
internal val MAP_TOP_STACK_MAX_WIDTH = 480.dp

/** The opacity of the action row's bar, so the map shows faintly through it. */
private const val ACTION_BAR_ALPHA = 0.85f

/** What the Map tab's chrome shows. */
data class MapChromeState(
    /** The serving-cell card, or null to hide it (no serving cell). */
    val card: ServingCellCardState? = null,
    /** True while the phone's location setting is off. */
    val locationOff: Boolean = false,
    /** False while a survey point sheet is open; the action row and survey points chrome hide. */
    val controlsVisible: Boolean = true,
    /** Search only makes sense while the towers layer shows. */
    val searchAvailable: Boolean = true,
    /** Filters only make sense while a filterable layer shows. */
    val filtersAvailable: Boolean = true,
    /** The zoom buttons, which Map settings turns on. */
    val showZoom: Boolean = false,
    /** True while the camera follows the user's location. */
    val following: Boolean = false,
)

/** What the Map tab's buttons do. */
interface MapTabActions {
    fun onSearch()
    fun onFilters()
    fun onLayers()
    fun onSettings()
    fun onAbout()
    fun onZoomIn()
    fun onZoomOut()
    fun onToggleFollow()
    fun onTurnOnLocation()
    fun onSelectSim(subscriptionId: Int)
}

/**
 * The Map tab around the map. There is no title bar: the map runs up under the status bar, which
 * gets a translucent band so its icons stay readable. Over the map sit the serving-cell card, the
 * location banner and the survey points pill, and at the bottom the zoom and follow buttons over
 * the action row (About, Map settings, Filters, Search, Layers).
 *
 * @param surveyPoints The survey points pill and filter chips, under the card.
 * @param map The map, told how much of it the chrome (and the status bar) covers at the top and
 * bottom.
 */
@Composable
fun MapTabChrome(
    state: MapChromeState,
    actions: MapTabActions,
    modifier: Modifier = Modifier,
    surveyPoints: @Composable () -> Unit = {},
    map: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val mapHeight = maxHeight
        var topHeight by remember { mutableIntStateOf(0) }
        var bottomHeight by remember { mutableIntStateOf(0) }
        val coveredBottom = if (state.controlsVisible) bottomHeight else 0
        map(with(density) { PaddingValues(top = topHeight.toDp(), bottom = coveredBottom.toDp()) })

        TopAppBarOverlay(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .widthIn(max = MAP_TOP_STACK_MAX_WIDTH)
                .fillMaxWidth()
                // Measured with the status bar, so the map's compass and notices clear it too
                .onSizeChanged { topHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = NsTheme.spacing.s, vertical = NsTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            state.card?.let { card ->
                // Half the map at most, so the card never reaches the controls in landscape
                ServingCellCard(card, actions::onSelectSim, maxExpandedHeight = mapHeight / 2)
            }
            if (state.locationOff) LocationOffBanner(actions::onTurnOnLocation)
            if (state.controlsVisible) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { surveyPoints() }
            }
        }

        if (state.controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(NsTheme.spacing.m)
                    .onSizeChanged { bottomHeight = it.height },
            ) {
                CameraControls(state, actions)
                Spacer(modifier = Modifier.height(NsTheme.spacing.m))
                ActionRow(state, actions)
            }
        }
    }
}

/** Location is off: the map cannot show where the user is. */
@Composable
private fun LocationOffBanner(onTurnOn: () -> Unit) {
    Surface(
        shape = NsTheme.shapes.panel,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = NsTheme.spacing.xs,
        modifier = Modifier.fillMaxWidth(),
    ) {
        InlineNotice(
            text = stringResource(R.string.location_services_disabled_message),
            icon = R.drawable.ic_location_off,
            iconTint = NsTheme.colors.status.warning.content,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = NsTheme.spacing.m, end = NsTheme.spacing.xs),
        ) {
            NsTextAction(stringResource(R.string.survey_location_turn_on), onTurnOn)
        }
    }
}

/** Zoom (when turned on in Map settings) and follow my location, on the right. */
@Composable
private fun CameraControls(state: MapChromeState, actions: MapTabActions) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.showZoom) ZoomControls(onZoomIn = actions::onZoomIn, onZoomOut = actions::onZoomOut)
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = ACTION_BAR_ALPHA),
                shape = CircleShape,
                shadowElevation = 6.dp,
            ) {
                Box(modifier = Modifier.padding(NsTheme.spacing.xs)) {
                    LocationButton(isFollowing = state.following, toggleFollowMe = actions::onToggleFollow)
                }
            }
        }
    }
}

/**
 * About, Map settings, Filters, Search and Layers: one row of round buttons floating on the map,
 * in the same surface fill as the follow button. Filters, the one most used while surveying, is
 * the larger accent button in the middle, as on the tower map before the redesign.
 */
@Composable
private fun ActionRow(state: MapChromeState, actions: MapTabActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NsTheme.spacing.l, vertical = NsTheme.spacing.s),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(R.drawable.ic_info, R.string.map_about_title, actions::onAbout)
        ActionButton(R.drawable.ic_settings, R.string.map_settings_title, actions::onSettings)
        if (state.filtersAvailable) {
            ActionButton(R.drawable.ic_filter, R.string.map_action_filters, actions::onFilters, isPrimary = true)
        }
        if (state.searchAvailable) ActionButton(R.drawable.ic_search_24, R.string.map_action_search, actions::onSearch)
        ActionButton(R.drawable.ic_layers, R.string.map_action_layers, actions::onLayers)
    }
}

/** @param isPrimary The larger button in the accent fill, for the row's main action. */
@Composable
private fun ActionButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    MapButton(
        contentDescription = stringResource(label),
        onClick = onClick,
        iconRes = icon,
        isLarge = isPrimary,
        containerColor = if (isPrimary) colors.primaryContainer else colors.surface,
        contentColor = if (isPrimary) colors.onPrimaryContainer else colors.onSurface,
    )
}
