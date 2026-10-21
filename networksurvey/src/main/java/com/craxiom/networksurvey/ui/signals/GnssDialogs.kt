package com.craxiom.networksurvey.ui.signals

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.gpstest.util.LibUIUtils
import com.craxiom.networksurvey.ui.common.dialogs.NsMultiChoiceDialog
import com.craxiom.networksurvey.ui.common.dialogs.NsSingleChoiceDialog
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * Multi-choice dialog for filtering which GNSS constellations are shown. Backed by the shared
 * [NsMultiChoiceDialog] so it stays consistent with every other dialog in the app.
 */
@Composable
fun GnssFilterDialog(
    initialItems: Array<String>,
    initialChecks: BooleanArray,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    NsMultiChoiceDialog(
        title = stringResource(id = R.string.filter_dialog_title),
        options = initialItems.toList(),
        initialChecked = initialChecks.toList(),
        onConfirm = { checked ->
            // Save selections to SharedPreferences using PreferenceUtils
            val selectedGnssTypes = GnssType.entries.toTypedArray()
                .filterIndexed { index, _ -> checked.getOrElse(index) { false } }
                .toSet()
            PreferenceUtils.saveGnssFilter(
                Application.get(),
                selectedGnssTypes,
                Application.getPrefs()
            )
            onSave()
        },
        onDismiss = onDismissRequest,
        confirmText = stringResource(id = R.string.save),
    )
}

/**
 * Single-choice dialog for selecting the satellite sort order. Backed by the shared
 * [NsSingleChoiceDialog].
 *
 * @param onSave Receives the chosen index into the sort_sats array; the caller saves and applies it.
 */
@Composable
fun GnssSortByDialog(
    onDismissRequest: () -> Unit,
    onSave: (index: Int) -> Unit
) {
    val currentSatOrder = PreferenceUtils.getSatSortIndex(Application.getPrefs())
    val sortOptions = stringArrayResource(id = R.array.sort_sats)

    NsSingleChoiceDialog(
        title = stringResource(id = R.string.menu_option_sort_by),
        options = sortOptions.toList(),
        selectedIndex = currentSatOrder,
        onConfirm = onSave,
        onDismiss = onDismissRequest,
    )
}

/** The GNSS constellation filter, opened from the GNSS sub-tab's Status | Sky row. */
@Composable
fun SatsFilterDialog(onDismissRequest: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val gnssTypes = GnssType.entries
    val filter = PreferenceUtils.gnssFilter(context, Application.getPrefs())
    GnssFilterDialog(
        initialItems = Array(gnssTypes.size) { LibUIUtils.getGnssDisplayName(context, gnssTypes[it]) },
        initialChecks = BooleanArray(gnssTypes.size) { filter.contains(gnssTypes[it]) },
        onDismissRequest = onDismissRequest,
        onSave = onSave,
    )
}
