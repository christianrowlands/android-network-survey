@file:OptIn(ExperimentalMaterial3Api::class)

package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.calculator.ChannelToBandCard
import com.craxiom.networksurvey.ui.cellular.calculator.LteCalculators
import com.craxiom.networksurvey.ui.cellular.calculator.UmtsCalculators
import com.craxiom.networksurvey.ui.cellular.model.CalculatorNetworkType
import com.craxiom.networksurvey.ui.cellular.model.MAX_NARFCN
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel
import com.craxiom.networksurvey.ui.cellular.model.GnbIdLengthOption

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onBrowseBands: (String) -> Unit = {},
) {
    // This will hold the state for which calculator section is being displayed
    val networkType by viewModel.networkType.collectAsState()

    Column(
        modifier = modifier
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.padding(4.dp)) {
            NetworkTypeDropdown(networkType, viewModel::setNetworkType)
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (networkType) {
            CalculatorNetworkType.NR -> {
                NrCalculators(viewModel = viewModel, onBrowseBands = onBrowseBands)
            }

            CalculatorNetworkType.LTE -> {
                LteCalculators(viewModel = viewModel, onBrowseBands = onBrowseBands)
            }

            CalculatorNetworkType.UMTS -> {
                UmtsCalculators(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NetworkTypeDropdown(
    selectedNetworkType: CalculatorNetworkType,
    onSelectionChanged: (CalculatorNetworkType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val networkTypes = CalculatorNetworkType.entries.toTypedArray()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            value = selectedNetworkType.name,
            onValueChange = { },
            label = { Text("Network Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            networkTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name) },
                    onClick = {
                        onSelectionChanged(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NrCalculators(viewModel: CalculatorViewModel, onBrowseBands: (String) -> Unit = {}) {
    val selectedGnbIdLength by viewModel.selectedGnbIdLength.collectAsState()
    val gnbIdLengthOptions = viewModel.gnbIdLengthOptions
    val cellIdInput by viewModel.nciInput.collectAsState()
    val gnbIdOutput by viewModel.gnbIdOutput.collectAsState()
    val sectorIdOutput by viewModel.nrSectorIdOutput.collectAsState()

    Column {
        CardItem {
            Column {
                TitleText(text = "NCI to gNB ID and Sector ID")

                GnbIdLengthDropdown(
                    selectedGnbIdLength,
                    gnbIdLengthOptions,
                    viewModel::setSelectedGnbIdLength
                )
                Spacer(modifier = Modifier.height(16.dp))
                NciInputField(cellIdInput, viewModel) {
                    viewModel.setCellIdInput(it)
                    viewModel.calculate5GNrGnbIdAndSectorId()
                }
                Spacer(modifier = Modifier.height(16.dp))
                ResultsDisplay(gnbIdOutput, sectorIdOutput, viewModel)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CardItem {
            Column {
                TitleText(text = stringResource(R.string.calculator_channel_to_band))
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
            value = selectedGnbIdLength.label,
            onValueChange = { },
            label = { Text("gNB ID Length") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelectionChanged(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NciInputField(
    cellIdInput: String,
    viewModel: CalculatorViewModel,
    onValueChange: (String) -> Unit
) {
    val nciError by viewModel.nciError.collectAsState()

    OutlinedTextField(
        value = cellIdInput,
        onValueChange = onValueChange,
        label = { Text("NCI") },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = nciError != null
    )
}

@Composable
fun ResultsDisplay(gnbIdOutput: String, sectorIdOutput: String, viewModel: CalculatorViewModel) {
    Column {
        val nciError by viewModel.nciError.collectAsState()

        Text(text = "gNB ID: $gnbIdOutput", style = MaterialTheme.typography.bodyLarge)
        Text(text = "Sector ID: $sectorIdOutput", style = MaterialTheme.typography.bodyLarge)

        if (nciError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = nciError.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun CardItem(content: @Composable () -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors()) {
        Box(Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun TitleText(text: String) {
    Text(
        textAlign = TextAlign.Center,
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
    )
}
