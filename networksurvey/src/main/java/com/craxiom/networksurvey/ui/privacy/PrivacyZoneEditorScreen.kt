package com.craxiom.networksurvey.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.common.CountFormat
import com.craxiom.networksurvey.ui.common.MapPoint
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsFormField
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.main.appbar.AppBarAction
import com.craxiom.networksurvey.ui.main.appbar.TitleBar
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.launch

/** Below this width the form sits under the map; at or above it (and wider than tall), beside it. */
private val SIDE_BY_SIDE_MIN_WIDTH = 600.dp
private val SIDE_FORM_WIDTH = 360.dp

/** The form never takes more than this share of a tall window, so the map stays usable. */
private const val FORM_MAX_HEIGHT_FRACTION = 0.5f

/**
 * The full-screen privacy zone editor: the map with a fixed center pin (pan the map to place the
 * zone), "Use my location", then the name, the radius slider (in the user's units), the live count
 * of waiting community records the zone would remove, and the tip about not centering it on the
 * front door. Save is the pinned action; an existing zone can be deleted from the top bar, after a
 * confirmation. The page closes when the organization turns zones off, so nothing can be changed then.
 *
 * @param fallbackCenter Where a new zone starts when the phone's location is unknown (the place the
 *                       Map tab last showed), or null.
 * @param onDone         Called once the zone is saved or deleted, or when it cannot be edited.
 */
@Composable
fun PrivacyZoneEditorScreen(
    viewModel: PrivacyZoneEditorViewModel,
    fallbackCenter: MapPoint?,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val waiting by viewModel.waitingCount.collectAsStateWithLifecycle()
    val otherZones by viewModel.otherZones.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val control = remember { ZoneEditorMapControl() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Seeded before the map is composed, so its camera opens there
    remember(viewModel) { viewModel.seedCenter(fallbackCenter) }
    val allowed = rememberPrivacyZonesAllowed()
    LaunchedEffect(viewModel, allowed) {
        if (viewModel.unavailable || !allowed) onDone()
    }
    LaunchedEffect(result) {
        when (result) {
            PrivacyZoneEditorResult.SAVED, PrivacyZoneEditorResult.DELETED -> onDone()
            PrivacyZoneEditorResult.FAILED -> {
                viewModel.consumeFailure()
                snackbarHostState.showSnackbar(resources.getString(R.string.privacy_zone_save_failed))
            }

            null -> Unit
        }
    }

    val defaultName = viewModel.defaultName { number -> resources.getString(R.string.privacy_zone_default_name, number) }
    Scaffold(
        topBar = {
            TitleBar(
                title = stringResource(if (form.isNew) R.string.privacy_zone_new_title else R.string.privacy_zone_edit_title),
                appBarActions = if (form.isNew) {
                    null
                } else {
                    listOf(AppBarAction(icon = R.drawable.ic_delete, description = R.string.privacy_zone_delete, onClick = { confirmDelete = true }))
                },
                onBackClick = onBack,
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                NsCtaBar {
                    NsPrimaryCta(
                        text = stringResource(R.string.privacy_zone_save),
                        onClick = { viewModel.save(defaultName) },
                        icon = R.drawable.ic_check,
                        enabled = form.center != null,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val findingLocation = stringResource(R.string.privacy_zone_finding_location)
        val map: @Composable (Modifier) -> Unit = { modifier ->
            PrivacyZoneEditorMap(
                form = form,
                otherZones = otherZones,
                initialZoom = viewModel.cameraZoom,
                control = control,
                onCameraMoved = { center, zoom, byUser ->
                    viewModel.cameraZoom = zoom
                    viewModel.onCenterChanged(center, byUser)
                },
                onLocation = { point -> viewModel.takeFirstFix(point)?.let(control::moveTo) },
                onLocationUnknown = { scope.launch { snackbarHostState.showSnackbar(findingLocation) } },
                modifier = modifier,
            )
        }
        val fields: @Composable (Modifier) -> Unit = { modifier ->
            ZoneEditorFields(
                form = form,
                waitingCount = if (allowed) waiting else 0,
                defaultName = defaultName,
                onNameChange = viewModel::setName,
                onRadiusChange = viewModel::setRadius,
                onRadiusChosen = control::frameCircle,
                modifier = modifier,
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= SIDE_BY_SIDE_MIN_WIDTH && maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    map(Modifier.weight(1f).fillMaxHeight())
                    fields(Modifier.width(SIDE_FORM_WIDTH).fillMaxHeight())
                }
            } else {
                val formMaxHeight = maxHeight * FORM_MAX_HEIGHT_FRACTION
                Column(Modifier.fillMaxSize()) {
                    map(Modifier.weight(1f).fillMaxWidth())
                    fields(Modifier.fillMaxWidth().heightIn(max = formMaxHeight))
                }
            }
        }
    }

    val savedName = viewModel.savedName
    if (confirmDelete && savedName != null) {
        PrivacyZoneDeleteDialog(name = savedName, onConfirm = viewModel::delete, onDismiss = { confirmDelete = false })
    }
}

/**
 * The editor's form, stateless: the name, the radius slider with its value in the user's units,
 * the waiting community records the zone would remove (hidden at 0, announced politely as it
 * changes), and the center tip. TalkBack's slider adjustments always move to another snapped
 * radius ([PrivacyZoneGeometry.radiusForAdjustment]), and the slider also offers increase and
 * decrease actions.
 *
 * @param onRadiusChosen Called when the user lets go of the slider, to frame the new circle.
 */
@Composable
internal fun ZoneEditorFields(
    form: PrivacyZoneEditorForm,
    waitingCount: Int,
    defaultName: String,
    onNameChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onRadiusChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radiusText = formatRadius(form.radiusMeters)
    val radiusLabel = stringResource(R.string.privacy_zone_radius)
    val increase = stringResource(R.string.privacy_zone_radius_increase)
    val decrease = stringResource(R.string.privacy_zone_radius_decrease)
    val adjustTo: (Int) -> Boolean = { radius ->
        if (radius == form.radiusMeters) {
            false
        } else {
            onRadiusChange(radius)
            onRadiusChosen()
            true
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NsTheme.spacing.screenGutter, vertical = NsTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        NsFormField(
            label = R.string.privacy_zone_name,
            value = form.name,
            placeholder = stringResource(R.string.privacy_zone_name_hint).takeIf { form.isNew } ?: defaultName,
            imeAction = ImeAction.Done,
            onChange = onNameChange,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.privacy_zone_radius),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(text = radiusText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = PrivacyZoneGeometry.sliderForRadius(form.radiusMeters),
            onValueChange = { onRadiusChange(PrivacyZoneGeometry.radiusForSlider(it)) },
            onValueChangeFinished = onRadiusChosen,
            // Outermost, so these replace the slider's own percentage and progress action
            modifier = Modifier.semantics {
                contentDescription = radiusLabel
                stateDescription = radiusText
                setProgress { position -> adjustTo(PrivacyZoneGeometry.radiusForAdjustment(form.radiusMeters, position)) }
                customActions = listOf(
                    CustomAccessibilityAction(increase) { adjustTo(PrivacyZoneGeometry.steppedRadius(form.radiusMeters, up = true)) },
                    CustomAccessibilityAction(decrease) { adjustTo(PrivacyZoneGeometry.steppedRadius(form.radiusMeters, up = false)) },
                )
            },
        )
        Row {
            RangeLabel(formatRadius(PrivacyZone.MIN_RADIUS_METERS), Modifier.weight(1f))
            RangeLabel(formatRadius(PrivacyZone.MAX_RADIUS_METERS))
        }
        if (waitingCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.privacy_zone_scrubs_waiting, waitingCount, CountFormat.format(waitingCount)),
                style = MaterialTheme.typography.bodyMedium,
                color = NsTheme.colors.status.warningText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            text = stringResource(R.string.privacy_zone_center_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RangeLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
