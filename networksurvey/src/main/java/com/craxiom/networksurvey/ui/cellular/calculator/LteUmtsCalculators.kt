package com.craxiom.networksurvey.ui.cellular.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.CalculatorError
import com.craxiom.networksurvey.ui.cellular.CalculatorField
import com.craxiom.networksurvey.ui.cellular.CalculatorPanel
import com.craxiom.networksurvey.ui.cellular.CalculatorResults
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel
import com.craxiom.networksurvey.ui.cellular.model.MAX_EARFCN
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The LTE calculators: Cell ID to eNB ID and sector, PCI to PSS and SSS, and channel to band. */
@Composable
fun LteCalculators(viewModel: CalculatorViewModel, onBrowseBands: (String) -> Unit = {}) {
    val lteCellIdInput by viewModel.lteCellIdInput.collectAsState()
    val lteSectorIdOutput by viewModel.lteSectorIdOutput.collectAsState()
    val lteCidError by viewModel.lteCidError.collectAsState()
    val enbIdOutput by viewModel.enbIdOutput.collectAsState()
    val pciInput by viewModel.pciInput.collectAsState()
    val pssOutput by viewModel.pssOutput.collectAsState()
    val sssOutput by viewModel.sssOutput.collectAsState()
    val ltePciError by viewModel.ltePciError.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.panelGap)) {
        CalculatorPanel(title = stringResource(R.string.calculator_lte_cell_id_title)) {
            CalculatorField(label = stringResource(R.string.calculator_cell_id), value = lteCellIdInput, isError = lteCidError != null) {
                viewModel.setLteCellIdInput(it)
                viewModel.calculateLteCellId()
            }
            CalculatorResults(
                stringResource(R.string.enb_id_label) to enbIdOutput,
                stringResource(R.string.sector_id_label) to lteSectorIdOutput,
            )
            CalculatorError(stringResource(R.string.calculator_cell_id), lteCidError)
        }

        CalculatorPanel(title = stringResource(R.string.calculator_pci_title)) {
            CalculatorField(label = stringResource(R.string.pci_label), value = pciInput, isError = ltePciError != null) {
                viewModel.setPciInput(it)
                viewModel.calculatePciToPssAndSss()
            }
            CalculatorResults(stringResource(R.string.pss_label) to pssOutput, stringResource(R.string.sss_label) to sssOutput)
            CalculatorError(stringResource(R.string.pci_label), ltePciError)
        }

        CalculatorPanel(title = stringResource(R.string.calculator_channel_to_band)) {
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

/** The UMTS calculator: Cell ID to RNC ID and short Cell ID. */
@Composable
fun UmtsCalculators(viewModel: CalculatorViewModel) {
    val umtsCellIdInput by viewModel.umtsCellIdInput.collectAsState()
    val rncIdOutput by viewModel.rncIdOutput.collectAsState()
    val shortCellIdOutput by viewModel.shortCellIdOutput.collectAsState()
    val umtsCidError by viewModel.umtsCidError.collectAsState()

    CalculatorPanel(title = stringResource(R.string.calculator_umts_title)) {
        CalculatorField(label = stringResource(R.string.calculator_umts_cell_id), value = umtsCellIdInput, isError = umtsCidError != null) {
            viewModel.setUmtsCellIdInput(it)
        }
        CalculatorResults(
            stringResource(R.string.rnc_label) to rncIdOutput,
            stringResource(R.string.calculator_short_cell_id) to shortCellIdOutput,
        )
        CalculatorError(stringResource(R.string.calculator_umts_cell_id), umtsCidError)
    }
}
