package com.craxiom.networksurvey.ui.cellular.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.CardItem
import com.craxiom.networksurvey.ui.cellular.TitleText
import com.craxiom.networksurvey.ui.cellular.model.MAX_EARFCN
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel

@Composable
fun LteCalculators(viewModel: CalculatorViewModel, onBrowseBands: (String) -> Unit = {}) {
    Column {
        // 4G LTE Cell ID calculator
        CardItem {
            Column {
                val lteSectorIdOutput by viewModel.lteSectorIdOutput.collectAsState()
                val lteCidError by viewModel.lteCidError.collectAsState()
                val enbIdOutput by viewModel.enbIdOutput.collectAsState()

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
                    "eNodeB ID: $enbIdOutput",
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

        // Channel to Band calculator
        CardItem {
            Column {
                TitleText(text = stringResource(R.string.calculator_channel_to_band))
                ChannelToBandCard(
                    label = stringResource(R.string.calculator_channel_earfcn),
                    value = viewModel.earfcnInput.collectAsState().value,
                    lookup = viewModel.earfcnLookup.collectAsState().value,
                    maxChannel = MAX_EARFCN,
                    bandPrefix = "B",
                    onValueChange = {
                        viewModel.setEarfcnInput(it)
                        viewModel.calculateEarfcnToBand()
                    },
                    onBrowseBands = onBrowseBands,
                )
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
