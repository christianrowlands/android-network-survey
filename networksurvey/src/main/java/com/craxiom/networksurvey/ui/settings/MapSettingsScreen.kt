package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.logging.db.SurveyedPointStore
import com.craxiom.networksurvey.ui.common.NsGroupLabel
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.common.percentText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

private const val OPACITY_DEFAULT = 30
private const val OPACITY_MAX = 100f
private val SWATCH_SIZE = 20.dp

/** The coverage circle colors, by stored value; a data palette kept as color resources. */
private val COVERAGE_COLORS = mapOf(
    NetworkSurveyConstants.DEFAULT_COVERAGE_CIRCLE_COLOR to R.color.serving_cell_dark,
    "red" to R.color.coverage_circle_red,
    "green" to R.color.coverage_circle_green,
    "orange" to R.color.coverage_circle_orange,
    "purple" to R.color.coverage_circle_purple,
    "yellow" to R.color.coverage_circle_yellow,
    "cyan" to R.color.coverage_circle_cyan,
    "white" to R.color.coverage_circle_white,
)

/**
 * Map settings: the screen and zoom buttons, the serving cell coverage circle (its color and fill
 * opacity apply only while it shows), clearing the survey points, and the provider color overrides.
 */
@Composable
fun MapSettingsScreen(onBack: () -> Unit, onOpenProviderColors: () -> Unit) {
    val preferences = rememberAppPreferences()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var choosingColor by rememberSaveable { mutableStateOf(false) }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }

    val keepScreenOn by preferences.observe { it.getBoolean(NetworkSurveyConstants.PROPERTY_MAP_KEEP_SCREEN_ON, true) }
    val zoomControls by preferences.observe { it.getBoolean(NetworkSurveyConstants.PROPERTY_MAP_SHOW_ZOOM_CONTROLS, false) }
    val coverage by preferences.observe { it.getBoolean(NetworkSurveyConstants.PROPERTY_MAP_DISPLAY_SERVING_CELL_COVERAGE, true) }
    val color by preferences.observe {
        it.stringOr(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_COLOR, NetworkSurveyConstants.DEFAULT_COVERAGE_CIRCLE_COLOR)
    }
    val opacity by preferences.observe { it.intOr(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_OPACITY, OPACITY_DEFAULT) }
    val colorValues = stringArrayResource(R.array.coverage_circle_color_values).toList()
    val colorLabels = stringArrayResource(R.array.coverage_circle_color_labels).toList()

    NsPageScaffold(title = stringResource(R.string.map_settings_title), onBack = onBack, snackbarHostState = snackbarHostState) {
        NsGroupLabel(stringResource(R.string.settings))
        NsRowGroup {
            NsSwitchRow(
                title = stringResource(R.string.keep_screen_on_title),
                subtitle = stringResource(if (keepScreenOn) R.string.keep_screen_on_summary_on else R.string.keep_screen_on_summary_off),
                checked = keepScreenOn,
                onCheckedChange = { on -> preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MAP_KEEP_SCREEN_ON, on) } },
            )
            NsRowDivider()
            NsSwitchRow(
                title = stringResource(R.string.show_zoom_controls_title),
                subtitle = stringResource(if (zoomControls) R.string.show_zoom_controls_summary_on else R.string.show_zoom_controls_summary_off),
                checked = zoomControls,
                onCheckedChange = { on -> preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MAP_SHOW_ZOOM_CONTROLS, on) } },
            )
        }

        NsGroupLabel(stringResource(R.string.coverage_area_category_title))
        NsRowGroup {
            NsSwitchRow(
                title = stringResource(R.string.display_coverage_area_title),
                subtitle = stringResource(if (coverage) R.string.display_coverage_area_summary_on else R.string.display_coverage_area_summary_off),
                checked = coverage,
                onCheckedChange = { on -> preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MAP_DISPLAY_SERVING_CELL_COVERAGE, on) } },
            )
            if (coverage) {
                NsRowDivider()
                val colorIndex = colorValues.indexOf(color).coerceAtLeast(0)
                NsRow(
                    title = stringResource(R.string.coverage_circle_color_title),
                    subtitle = colorLabels.getOrNull(colorIndex),
                    onClick = { choosingColor = true },
                ) { Swatch(colorResource(COVERAGE_COLORS[color] ?: R.color.serving_cell_dark)) }
                NsRowDivider()
                OpacityRow(opacity) { value -> preferences.edit { putInt(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_OPACITY, value) } }
            }
        }

        NsGroupLabel(stringResource(R.string.survey_points_category_title))
        NsRowGroup {
            NsRow(
                title = stringResource(R.string.clear_survey_points_title),
                subtitle = stringResource(R.string.clear_survey_points_summary),
                onClick = { confirmingClear = true },
            )
        }

        NsGroupLabel(stringResource(R.string.provider_colors_category_title))
        NsRowGroup {
            NsRow(
                title = stringResource(R.string.provider_color_overrides_title),
                subtitle = stringResource(R.string.provider_color_overrides_summary),
                onClick = onOpenProviderColors,
            )
        }
    }

    if (choosingColor) {
        ColorChoiceDialog(
            title = stringResource(R.string.coverage_circle_color_title),
            names = colorLabels,
            colors = colorValues.map { colorResource(COVERAGE_COLORS[it] ?: R.color.serving_cell_dark) },
            selectedIndex = colorValues.indexOf(color),
            onSelected = { index -> preferences.edit { putString(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_COLOR, colorValues[index]) } },
            onDismiss = { choosingColor = false },
        )
    }
    if (confirmingClear) {
        ClearSurveyPointsDialog(
            onClear = {
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) {
                        runCatching { SurveyedPointStore.clearAll(SurveyDatabase.getInstance(context).surveyedPointDao()) }
                            .onFailure { Timber.e(it, "Could not clear the survey points") }
                            .isSuccess
                    }
                    snackbarHostState.showSnackbar(
                        resources.getString(if (cleared) R.string.clear_survey_points_success else R.string.clear_survey_points_failed)
                    )
                }
            },
            onDismiss = { confirmingClear = false },
        )
    }
}

/** The fill opacity as a slider under its title, stored when the finger lifts. */
@Composable
private fun OpacityRow(opacity: Int, onChange: (Int) -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity.toFloat()) }
    val title = stringResource(R.string.coverage_circle_opacity_title)
    Column(modifier = Modifier.padding(vertical = NsTheme.spacing.s)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.coverage_circle_opacity_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = percentText(value.roundToInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 0f..OPACITY_MAX,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color)
    )
}
