package com.craxiom.networksurvey.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
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
 */
@Composable
fun GnssSortByDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val prefs = Application.getPrefs()
    val currentSatOrder = PreferenceUtils.getSatSortOrderFromPreferences(context, prefs)
    val sortOptions = stringArrayResource(id = R.array.sort_sats)

    NsSingleChoiceDialog(
        title = stringResource(id = R.string.menu_option_sort_by),
        options = sortOptions.toList(),
        selectedIndex = currentSatOrder,
        onConfirm = { index ->
            setSortByClause(context, index, prefs)
            onSave()
        },
        onDismiss = onDismissRequest,
    )
}

/**
 * Saves the "sort by" order to preferences.
 */
private fun setSortByClause(context: Context, index: Int, prefs: SharedPreferences) {
    val sortOptions = context.resources.getStringArray(R.array.sort_sats)
    PreferenceUtils.saveString(
        prefs,
        context.resources.getString(R.string.pref_key_default_sat_sort),
        sortOptions[index]
    )
}
