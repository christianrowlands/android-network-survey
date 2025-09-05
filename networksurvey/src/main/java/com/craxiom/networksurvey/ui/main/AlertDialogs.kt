package com.craxiom.networksurvey.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.model.GnssType
import com.craxiom.networksurvey.util.PreferenceUtils

@Composable
fun GnssFilterDialog(
    initialItems: Array<String>,
    initialChecks: BooleanArray,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val selectedChecks = remember { mutableStateListOf(*initialChecks.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = context.getString(R.string.filter_dialog_title)) },
        text = {
            Column {
                initialItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Toggle the checkbox state when the row is clicked
                                selectedChecks[index] = !selectedChecks[index]
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedChecks[index],
                            onCheckedChange = { isChecked ->
                                selectedChecks[index] = isChecked
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Save selections to SharedPreferences using PreferenceUtils
                    val selectedGnssTypes = GnssType.entries.toTypedArray()
                        .filterIndexed { index, _ -> selectedChecks[index] }
                        .toSet()
                    PreferenceUtils.saveGnssFilter(
                        Application.get(),
                        selectedGnssTypes,
                        Application.getPrefs()
                    )
                    onSave()
                }
            ) {
                Text(text = context.getString(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = context.getString(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun GnssSortByDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current

    val prefs = Application.getPrefs()
    val currentSatOrder = PreferenceUtils.getSatSortOrderFromPreferences(context, prefs)
    var selectedOptionIndex by remember { mutableStateOf(currentSatOrder) }
    val sortOptions = stringArrayResource(id = R.array.sort_sats)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(id = R.string.menu_option_sort_by)) },
        text = {
            Column {
                sortOptions.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (index == selectedOptionIndex),
                                onClick = { selectedOptionIndex = index }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (index == selectedOptionIndex),
                            onClick = { selectedOptionIndex = index }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Save selected sort option to preferences
                    setSortByClause(context, selectedOptionIndex, prefs)
                    onSave()
                    onDismissRequest()
                }
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
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
