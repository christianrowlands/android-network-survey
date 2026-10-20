package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSourceFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointTimeFilter
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointUploadFilter
import com.craxiom.networksurvey.ui.cellular.model.TowerSource

/**
 * Preset options, in months, for the "Last updated" age filter in [CombinedFiltersBottomSheet]; 0
 * means no filter. 60 months represents 5 years; older thresholds are unlikely to be useful since
 * the filter exists to hide stale tower data.
 */
private val TOWER_AGE_OPTIONS = listOf(0, 6, 12, 24, 60)

private const val MONTHS_PER_YEAR = 12

/** "Any age", "Within 6 months" or "Within 2 years" for an age option of [months]. */
@Composable
private fun towerAgeLabel(months: Int): String = when {
    months == 0 -> stringResource(R.string.tower_filter_age_any)
    months % MONTHS_PER_YEAR == 0 -> (months / MONTHS_PER_YEAR).let {
        pluralStringResource(R.plurals.tower_filter_age_years, it, it)
    }

    else -> pluralStringResource(R.plurals.tower_filter_age_months, months, months)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CombinedFiltersBottomSheet(
    showTowerFilters: Boolean,
    showSurveyedFilters: Boolean,
    currentSurveyedFilters: SurveyedPointFilterSelection,
    latestSurveyAvailable: Boolean,
    latestSurveyStart: Long?,
    showSourceFilter: Boolean,
    onSetSurveyedFilters: (SurveyedPointFilterSelection) -> Unit,
    currentPlmn: Plmn,
    currentRadio: String,
    currentSource: TowerSource,
    currentMaxAgeMonths: Int,
    onSetPlmnFilter: (Int, Int, String?) -> Unit,
    onSetRadioType: (String) -> Unit,
    onSetTowerSource: (TowerSource) -> Unit,
    onSetMaxAgeMonths: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val dismissKeyboard = rememberKeyboardDismisser()
    val focusManager = LocalFocusManager.current

    var mccInput by remember { mutableStateOf(currentPlmn.mcc.toString()) }
    var mncInput by remember { mutableStateOf(currentPlmn.mncString ?: currentPlmn.mnc.toString()) }
    var selectedRadio by remember { mutableStateOf(currentRadio) }
    var selectedSource by remember { mutableStateOf(currentSource) }
    var selectedMaxAgeMonths by remember { mutableIntStateOf(currentMaxAgeMonths) }
    var selectedSurveyed by remember { mutableStateOf(currentSurveyedFilters) }

    val radioOptions = listOf(
        CellularProtocol.GSM.name,
        CellularProtocol.CDMA.name,
        CellularProtocol.UMTS.name,
        CellularProtocol.LTE.name,
        CellularProtocol.NR.name
    )

    ModalBottomSheet(
        onDismissRequest = {
            dismissKeyboard()
            val mcc = mccInput.toIntOrNull() ?: 0
            val mnc = mncInput.toIntOrNull() ?: 0
            val mncString = if (mncInput.isNotBlank() && mnc != 0) mncInput else null
            onSetPlmnFilter(mcc, mnc, mncString)
            onSetRadioType(selectedRadio)
            onSetTowerSource(selectedSource)
            onSetMaxAgeMonths(selectedMaxAgeMonths)
            onSetSurveyedFilters(selectedSurveyed)
            onDismiss()
        },
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.map_action_filters),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (showTowerFilters) {
                // PLMN Filter Section
                Text(
                    text = stringResource(R.string.tower_filter_provider),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.tower_filter_provider_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = if (mccInput == "0") "" else mccInput,
                        onValueChange = { mccInput = it },
                        label = { Text(stringResource(R.string.plmn_lookup_mcc_label)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Right) }
                        ),
                        trailingIcon = {
                            if (mccInput.isNotEmpty() && mccInput != "0") {
                                IconButton(onClick = { mccInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(
                                            R.string.tower_filter_clear_field,
                                            stringResource(R.string.plmn_lookup_mcc_label),
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = if (mncInput == "0") "" else mncInput,
                        onValueChange = { mncInput = it },
                        label = { Text(stringResource(R.string.plmn_lookup_mnc_label)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { dismissKeyboard() }
                        ),
                        trailingIcon = {
                            if (mncInput.isNotEmpty() && mncInput != "0") {
                                IconButton(onClick = { mncInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(
                                            R.string.tower_filter_clear_field,
                                            stringResource(R.string.plmn_lookup_mnc_label),
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Protocol Selection Section
                Text(
                    text = stringResource(R.string.tower_filter_technology),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.tower_filter_technology_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                radioOptions.forEach { protocol ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (protocol == selectedRadio),
                                onClick = { selectedRadio = protocol }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (protocol == selectedRadio),
                            onClick = { selectedRadio = protocol }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = protocol)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tower Source Section
                Text(
                    text = stringResource(R.string.tower_filter_source),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.tower_filter_source_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TowerSource.entries.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (source == selectedSource),
                                onClick = { selectedSource = source }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (source == selectedSource),
                            onClick = { selectedSource = source }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = stringResource(source.displayName))
                            Text(
                                text = stringResource(
                                    when (source) {
                                        TowerSource.OpenCelliD -> R.string.tower_filter_source_opencellid
                                        TowerSource.BTSearch -> R.string.tower_filter_source_btsearch
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Last Updated age filter
                Text(
                    text = stringResource(R.string.tower_filter_age),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.tower_filter_age_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var ageMenuExpanded by remember { mutableStateOf(false) }
                val currentLabel = towerAgeLabel(selectedMaxAgeMonths.takeIf { it in TOWER_AGE_OPTIONS } ?: 0)

                ExposedDropdownMenuBox(
                    expanded = ageMenuExpanded,
                    onExpandedChange = { ageMenuExpanded = !ageMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tower_filter_age_field)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = ageMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = ageMenuExpanded,
                        onDismissRequest = { ageMenuExpanded = false }
                    ) {
                        TOWER_AGE_OPTIONS.forEach { months ->
                            DropdownMenuItem(
                                text = { Text(towerAgeLabel(months)) },
                                onClick = {
                                    selectedMaxAgeMonths = months
                                    ageMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedSource == TowerSource.BTSearch && selectedMaxAgeMonths > 0) {
                    Text(
                        text = stringResource(R.string.tower_filter_age_not_btsearch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

            }

            if (showSurveyedFilters) {
                SurveyedPointFilterSection(
                    selection = selectedSurveyed,
                    latestSurveyAvailable = latestSurveyAvailable,
                    latestSurveyStart = latestSurveyStart,
                    showSourceFilter = showSourceFilter,
                    showLeadingSpacer = showTowerFilters,
                    onChange = { selectedSurveyed = it }
                )
            }
        }
    }
}
