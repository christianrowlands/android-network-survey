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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.ui.cellular.model.CalculatorNetworkType
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel
import com.craxiom.networksurvey.ui.cellular.model.GnbIdLengthOption

@Composable
fun CalculatorScreen(viewModel: CalculatorViewModel = viewModel()) {
    // This will hold the state for which calculator section is being displayed
    val networkType by viewModel.networkType.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.padding(4.dp)) {
            NetworkTypeDropdown(networkType, viewModel::setNetworkType)
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (networkType) {
            CalculatorNetworkType.NR -> {
                NrCalculators(viewModel = viewModel)
            }

            CalculatorNetworkType.LTE -> {
                LteCalculators(viewModel = viewModel)
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
fun NrCalculators(viewModel: CalculatorViewModel) {
    val selectedGnbIdLength by viewModel.selectedGnbIdLength.collectAsState()
    val gnbIdLengthOptions = viewModel.gnbIdLengthOptions
    val cellIdInput by viewModel.nciInput.collectAsState()
    val gnbIdOutput by viewModel.gnbIdOutput.collectAsState()
    val sectorIdOutput by viewModel.nrSectorIdOutput.collectAsState()

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
            Spacer(modifier = Modifier.height(8.dp))
            ResultsDisplay(gnbIdOutput, sectorIdOutput, viewModel)
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
fun LteCalculators(viewModel: CalculatorViewModel) {
    Column {
        // 4G LTE Cell ID calculator
        CardItem {
            Column {
                val lteSectorIdOutput by viewModel.lteSectorIdOutput.collectAsState()
                val lteCidError by viewModel.lteCidError.collectAsState()
                val collectAsState by viewModel.enbIdOutput.collectAsState()

                TitleText(text = "Cell ID to eNB ID and Sector ID")

                OutlinedTextField(
                    value = viewModel.lteCellIdInput.collectAsState().value,
                    onValueChange = {
                        viewModel.setLteCellIdInput(it)
                        viewModel.calculateLteCellId()
                    },
                    label = { Text("Cell ID") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = lteCidError != null
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "eNodeB ID: $collectAsState",
                    modifier = Modifier.padding(start = 8.dp)
                )
                Text(
                    "Sector ID: $lteSectorIdOutput",
                    modifier = Modifier.padding(start = 8.dp)
                )

                if (lteCidError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = lteCidError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PCI to PSS and SSS calculator
        CardItem {
            Column {
                val ltePciError by viewModel.ltePciError.collectAsState()

                TitleText(text = "PCI to PSS and SSS")

                OutlinedTextField(
                    value = viewModel.pciInput.collectAsState().value,
                    onValueChange = {
                        viewModel.setPciInput(it)
                        viewModel.calculatePciToPssAndSss()
                    },
                    label = { Text("PCI") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = ltePciError != null
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "PSS: ${viewModel.pssOutput.collectAsState().value}",
                    modifier = Modifier.padding(start = 8.dp)
                )
                Text(
                    "SSS: ${viewModel.sssOutput.collectAsState().value}",
                    modifier = Modifier.padding(start = 8.dp)
                )

                if (ltePciError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = ltePciError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // EARFCN to Band Number calculator
        CardItem {
            Column {
                val lteEarfcnError by viewModel.lteEarfcnError.collectAsState()

                TitleText(text = "EARFCN to Band Number")

                OutlinedTextField(
                    value = viewModel.earfcnInput.collectAsState().value,
                    onValueChange = {
                        viewModel.setEarfcnInput(it)
                        viewModel.calculateEarfcnToBand()
                    },
                    label = { Text("EARFCN") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = lteEarfcnError != null
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Band: ${viewModel.bandOutput.collectAsState().value}",
                    modifier = Modifier.padding(start = 8.dp)
                )

                if (lteEarfcnError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = lteEarfcnError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun UmtsCalculators(viewModel: CalculatorViewModel) {
    val umtsCellIdInput by viewModel.umtsCellIdInput.collectAsState()
    val rncIdOutput by viewModel.rncIdOutput.collectAsState()
    val shortCellIdOutput by viewModel.shortCellIdOutput.collectAsState()
    val umtsCidError by viewModel.umtsCidError.collectAsState()

    CardItem {
        Column {
            TitleText(text = "UMTS Cell ID to RNC ID and Short Cell ID")

            OutlinedTextField(
                value = umtsCellIdInput,
                onValueChange = {
                    viewModel.setUmtsCellIdInput(it)
                },
                label = { Text("UMTS Cell ID") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = umtsCidError != null
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(text = "RNC ID: $rncIdOutput", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Short Cell ID: $shortCellIdOutput",
                style = MaterialTheme.typography.bodyLarge
            )

            if (umtsCidError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = umtsCidError.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}


@Composable
private fun CardItem(content: @Composable () -> Unit) {
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
