@file:OptIn(ExperimentalMaterial3Api::class)

package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.calculator.ChannelToBandCard
import com.craxiom.networksurvey.ui.cellular.calculator.LteCalculators
import com.craxiom.networksurvey.ui.cellular.calculator.UmtsCalculators
import com.craxiom.networksurvey.ui.cellular.model.CalculatorNetworkType
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel
import com.craxiom.networksurvey.ui.cellular.model.GnbIdLengthOption
import com.craxiom.networksurvey.ui.cellular.model.MAX_NARFCN
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsSegmentedToggle
import com.craxiom.networksurvey.ui.common.NsValueCell
import com.craxiom.networksurvey.ui.common.NsValueGrid
import com.craxiom.networksurvey.ui.common.SegmentedOption
import com.craxiom.networksurvey.ui.theme.NsTheme
import java.text.NumberFormat

/**
 * The Calculators pane of Cellular tools: the network type as a segmented toggle, then one panel per
 * calculator (a title, its input and its results in the value grid), as on the other pages.
 */
@Composable
fun CalculatorScreen(
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = viewModel(),
    onBrowseBands: (String) -> Unit = {},
) {
    val networkType by viewModel.networkType.collectAsState()
    val types = CalculatorNetworkType.entries

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NsTheme.spacing.screenGutter)
            .padding(bottom = NsTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap),
    ) {
        NsSegmentedToggle(
            options = types.map { SegmentedOption(label = it.name) },
            selectedIndex = types.indexOf(networkType),
            onSelected = { viewModel.setNetworkType(types[it]) },
        )
        when (networkType) {
            CalculatorNetworkType.NR -> NrCalculators(viewModel = viewModel, onBrowseBands = onBrowseBands)
            CalculatorNetworkType.LTE -> LteCalculators(viewModel = viewModel, onBrowseBands = onBrowseBands)
            CalculatorNetworkType.UMTS -> UmtsCalculators(viewModel = viewModel)
        }
    }
}

@Composable
fun NrCalculators(viewModel: CalculatorViewModel, onBrowseBands: (String) -> Unit = {}) {
    val selectedGnbIdLength by viewModel.selectedGnbIdLength.collectAsState()
    val cellIdInput by viewModel.nciInput.collectAsState()
    val gnbIdOutput by viewModel.gnbIdOutput.collectAsState()
    val sectorIdOutput by viewModel.nrSectorIdOutput.collectAsState()
    val nciError by viewModel.nciError.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap)) {
        CalculatorPanel(title = stringResource(R.string.calculator_nci_title)) {
            GnbIdLengthDropdown(selectedGnbIdLength, viewModel.gnbIdLengthOptions, viewModel::setSelectedGnbIdLength)
            CalculatorField(label = stringResource(R.string.nci_label), value = cellIdInput, isError = nciError != null) {
                viewModel.setCellIdInput(it)
                viewModel.calculate5GNrGnbIdAndSectorId()
            }
            CalculatorResults(
                stringResource(R.string.gnb_id_label) to gnbIdOutput,
                stringResource(R.string.sector_id_label) to sectorIdOutput,
            )
            CalculatorError(stringResource(R.string.nci_label), nciError)
        }
        CalculatorPanel(title = stringResource(R.string.calculator_channel_to_band)) {
            ChannelToBandCard(
                label = stringResource(R.string.calculator_channel_narfcn),
                value = viewModel.narfcnInput.collectAsState().value,
                lookup = viewModel.narfcnLookup.collectAsState().value,
                maxChannel = MAX_NARFCN,
                bandPrefix = "n",
                onValueChange = viewModel::setNarfcnInput,
                onBrowseBands = onBrowseBands,
            )
        }
    }
}

@Composable
fun GnbIdLengthDropdown(
    selectedGnbIdLength: GnbIdLengthOption,
    options: List<GnbIdLengthOption>,
    onSelectionChanged: (GnbIdLengthOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            value = gnbIdLengthLabel(selectedGnbIdLength),
            onValueChange = { },
            label = { Text(stringResource(R.string.calculator_gnb_id_length)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(gnbIdLengthLabel(option)) },
                    onClick = {
                        onSelectionChanged(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** One calculator: a panel with its title as a heading and its parts spaced like a form. */
@Composable
fun CalculatorPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    NsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/** A single-line numeric input. */
@Composable
fun CalculatorField(label: String, value: String, isError: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
    )
}

/** Results as value-over-label cells; "--" until there is something to show. */
@Composable
fun CalculatorResults(vararg results: Pair<String, String>) {
    val none = stringResource(R.string.signal_none)
    NsValueGrid(results.toList(), maxColumns = 2) { (label, value), cellModifier ->
        NsValueCell(value = value.ifBlank { none }, label = label, modifier = cellModifier)
    }
}

/** "22 bits + 14 bits": the gNB ID's bits, then the sector ID's. */
@Composable
private fun gnbIdLengthLabel(option: GnbIdLengthOption): String = stringResource(
    R.string.calculator_bit_split,
    pluralStringResource(R.plurals.calculator_bits, option.gnbBitCount, option.gnbBitCount),
    pluralStringResource(R.plurals.calculator_bits, option.sectorBitCount, option.sectorBitCount),
)

/**
 * The input's problem, in the error color, when there is one: the [field] only accepts values from
 * 0 to [maxValue], and [maxValue] is null while the input is fine.
 */
@Composable
fun CalculatorError(field: String, maxValue: Long?) {
    if (maxValue != null) {
        Text(
            text = stringResource(
                R.string.calculator_out_of_range,
                field,
                NumberFormat.getIntegerInstance().format(maxValue),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
