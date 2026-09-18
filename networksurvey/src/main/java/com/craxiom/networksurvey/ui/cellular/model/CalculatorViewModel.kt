package com.craxiom.networksurvey.ui.cellular.model

import androidx.lifecycle.ViewModel
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.data.band.BandSearch
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.band.LteBandTable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CalculatorNetworkType { UMTS, LTE, NR }

/** Re-exported from [BandSearch] so the calculator and the browser share one definition. */
const val MAX_EARFCN = BandSearch.MAX_EARFCN
const val MAX_NARFCN = BandSearch.MAX_NARFCN

class CalculatorViewModel : ViewModel() {
    private val _networkType = MutableStateFlow(CalculatorNetworkType.LTE)
    val networkType: StateFlow<CalculatorNetworkType> = _networkType.asStateFlow()

    private val _gnbIdLengthOptions = listOf(
        GnbIdLengthOption("22 bits + 14 bits", 22),
        GnbIdLengthOption("23 bits + 13 bits", 23),
        GnbIdLengthOption("24 bits + 12 bits", 24),
        GnbIdLengthOption("25 bits + 11 bits", 25),
        GnbIdLengthOption("26 bits + 10 bits", 26),
        GnbIdLengthOption("27 bits + 9 bits", 27),
        GnbIdLengthOption("28 bits + 8 bits", 28),
        GnbIdLengthOption("29 bits + 7 bits", 29),
        GnbIdLengthOption("30 bits + 6 bits", 30),
        GnbIdLengthOption("31 bits + 5 bits", 31),
        GnbIdLengthOption("32 bits + 4 bits", 32)
    )
    val gnbIdLengthOptions: List<GnbIdLengthOption> = _gnbIdLengthOptions

    // Store the selected option as a state
    private val _selectedGnbIdLength = MutableStateFlow(_gnbIdLengthOptions[2])
    val selectedGnbIdLength: StateFlow<GnbIdLengthOption> = _selectedGnbIdLength.asStateFlow()

    private val _nciInput = MutableStateFlow("")
    val nciInput: StateFlow<String> = _nciInput.asStateFlow()

    // Outputs
    private val _gnbIdOutput = MutableStateFlow("")
    val gnbIdOutput: StateFlow<String> = _gnbIdOutput.asStateFlow()

    private val _nrSectorIdOutput = MutableStateFlow("")
    val nrSectorIdOutput: StateFlow<String> = _nrSectorIdOutput.asStateFlow()

    // Errors
    private val _nciError = MutableStateFlow<String?>(null)
    val nciError: StateFlow<String?> = _nciError.asStateFlow()

    private val _lteCidError = MutableStateFlow<String?>(null)
    val lteCidError: StateFlow<String?> = _lteCidError.asStateFlow()

    private val _ltePciError = MutableStateFlow<String?>(null)
    val ltePciError: StateFlow<String?> = _ltePciError.asStateFlow()

    // Inputs for LTE Calculators
    private val _lteCellIdInput = MutableStateFlow("")
    val lteCellIdInput: StateFlow<String> = _lteCellIdInput.asStateFlow()

    private val _pciInput = MutableStateFlow("")
    val pciInput: StateFlow<String> = _pciInput.asStateFlow()

    private val _earfcnInput = MutableStateFlow("")
    val earfcnInput: StateFlow<String> = _earfcnInput.asStateFlow()

    // Outputs for LTE Calculators
    private val _enbIdOutput = MutableStateFlow("")
    val enbIdOutput: StateFlow<String> = _enbIdOutput.asStateFlow()

    private val _lteSectorIdOutput = MutableStateFlow("")
    val lteSectorIdOutput: StateFlow<String> = _lteSectorIdOutput.asStateFlow()

    private val _pssOutput = MutableStateFlow("")
    val pssOutput: StateFlow<String> = _pssOutput.asStateFlow()

    private val _sssOutput = MutableStateFlow("")
    val sssOutput: StateFlow<String> = _sssOutput.asStateFlow()

    private val _earfcnLookup = MutableStateFlow<ChannelLookup?>(null)
    val earfcnLookup: StateFlow<ChannelLookup?> = _earfcnLookup.asStateFlow()

    private val _narfcnInput = MutableStateFlow("")
    val narfcnInput: StateFlow<String> = _narfcnInput.asStateFlow()

    private val _narfcnLookup = MutableStateFlow<ChannelLookup?>(null)
    val narfcnLookup: StateFlow<ChannelLookup?> = _narfcnLookup.asStateFlow()

    // UMTS
    private val _umtsCellIdInput = MutableStateFlow("")
    val umtsCellIdInput: StateFlow<String> = _umtsCellIdInput

    private val _rncIdOutput = MutableStateFlow("")
    val rncIdOutput: StateFlow<String> = _rncIdOutput

    private val _shortCellIdOutput = MutableStateFlow("")
    val shortCellIdOutput: StateFlow<String> = _shortCellIdOutput

    private val _umtsCidError = MutableStateFlow<String?>(null)
    val umtsCidError: StateFlow<String?> = _umtsCidError

    fun setNetworkType(type: CalculatorNetworkType) {
        _networkType.value = type
    }

    fun setSelectedGnbIdLength(option: GnbIdLengthOption) {
        _selectedGnbIdLength.value = option
        calculate5GNrGnbIdAndSectorId()
    }

    fun setCellIdInput(input: String) {
        _nciInput.value = input
    }

    fun calculate5GNrGnbIdAndSectorId() {
        if (nciInput.value.isEmpty()) {
            _nciError.value = null
            _gnbIdOutput.value = ""
            _nrSectorIdOutput.value = ""
            return
        }

        val nci = _nciInput.value.toLongOrNull()
        if (nci == null || nci !in 0..68_719_476_735) {
            _nciError.value = "Invalid NCI. Valid range is 0 - 68,719,476,735"
            return
        }

        val gnbBits = _selectedGnbIdLength.value.gnbBitCount

        val gnbId = CalculationUtils.getGnbIdFromNci(nci, gnbBits)
        val sectorId = CalculationUtils.getSectorIdFromNci(nci, gnbBits)
        _nciError.value = null
        _gnbIdOutput.value = gnbId.toString()
        _nrSectorIdOutput.value = sectorId.toString()
    }

    fun setLteCellIdInput(input: String) {
        _lteCellIdInput.value = input
    }

    fun setPciInput(input: String) {
        _pciInput.value = input
    }

    fun setEarfcnInput(input: String) {
        _earfcnInput.value = input
    }

    fun calculateLteCellId() {
        if (lteCellIdInput.value.isEmpty()) {
            _lteCidError.value = null
            _enbIdOutput.value = ""
            _lteSectorIdOutput.value = ""
            return
        }

        val cellId = lteCellIdInput.value.toIntOrNull()
        if (cellId == null || !CalculationUtils.isLteCellIdValid(cellId)) {
            _lteCidError.value = "Invalid Cell ID. Valid Range is 0 - 268435455"
            _enbIdOutput.value = ""
            _lteSectorIdOutput.value = ""
        } else {
            val enbId = CalculationUtils.getEnodebIdFromCellId(cellId)
            val sectorId = CalculationUtils.getSectorIdFromCellId(cellId)
            _lteCidError.value = null
            _enbIdOutput.value = enbId.toString()
            _lteSectorIdOutput.value = sectorId.toString()
        }
    }

    fun calculatePciToPssAndSss() {
        if (pciInput.value.isEmpty()) {
            _ltePciError.value = null
            _pssOutput.value = ""
            _sssOutput.value = ""
            return
        }

        val pci = pciInput.value.toIntOrNull()
        if (pci == null || pci !in 0..503) {
            _ltePciError.value = "Invalid PCI. Valid Range is 0 - 503"
            _pssOutput.value = ""
            _sssOutput.value = ""
        } else {
            val pss = CalculationUtils.getPrimarySyncSequence(pci)
            val sss = CalculationUtils.getSecondarySyncSequence(pci)
            _ltePciError.value = null
            _pssOutput.value = pss.toString()
            _sssOutput.value = sss.toString()
        }
    }

    fun calculateEarfcnToBand() {
        val input = earfcnInput.value
        if (input.isEmpty()) {
            _earfcnLookup.value = null
            return
        }

        val earfcn = input.toIntOrNull()
        if (earfcn == null || earfcn !in 0..MAX_EARFCN) {
            _earfcnLookup.value = ChannelLookup(-1, null, emptyList(), outOfRange = true)
            return
        }

        // A valid EARFCN can still fall in no band: the table has interior gaps, for example
        // between band 11 and band 12. That is an answer, not an error, so it is reported as an
        // empty band list rather than as the -1 sentinel this used to print verbatim.
        val band = CellularUtils.downlinkEarfcnToBand(earfcn)
        _earfcnLookup.value = ChannelLookup(
            channel = earfcn,
            frequencyMhz = LteBandTable.downlinkEarfcnToFrequencyMhz(earfcn).takeIf { it >= 0 },
            bandNumbers = if (band == -1) emptyList() else listOf(band),
        )
    }

    fun setNarfcnInput(input: String) {
        _narfcnInput.value = input
        calculateNarfcnToBand()
    }

    fun calculateNarfcnToBand() {
        val input = narfcnInput.value
        if (input.isEmpty()) {
            _narfcnLookup.value = null
            return
        }

        val narfcn = input.toIntOrNull()
        if (narfcn == null || narfcn !in 0..MAX_NARFCN) {
            _narfcnLookup.value = ChannelLookup(-1, null, emptyList(), outOfRange = true)
            return
        }

        // NR ranges overlap heavily, so this deliberately reports every candidate rather than
        // collapsing to one or refusing to answer.
        val frequency = CellularUtils.narfcnToFrequencyMhz(narfcn).takeIf { it >= 0 }
        _narfcnLookup.value = ChannelLookup(
            channel = narfcn,
            frequencyMhz = frequency,
            bandNumbers = CellularUtils.downlinkNarfcnToBands(narfcn).toList(),
        )
    }

    // Function to update input
    fun setUmtsCellIdInput(input: String) {
        _umtsCellIdInput.value = input
        calculateUmtsCellId()
    }

    // Function to compute RNC ID and Short Cell ID
    private fun calculateUmtsCellId() {
        if (_umtsCellIdInput.value.isEmpty()) {
            _umtsCidError.value = null
            _rncIdOutput.value = ""
            _shortCellIdOutput.value = ""
            return
        }

        val cellId = _umtsCellIdInput.value.toLongOrNull()
        if (cellId == null || cellId < 0 || cellId > 268_435_455) {
            _umtsCidError.value = "Invalid UMTS Cell ID. Valid Range is 0 - 268435455"
            _rncIdOutput.value = ""
            _shortCellIdOutput.value = ""
            return
        }

        val rncId = CalculationUtils.getUmtsRncFromCid(cellId.toInt()).toLong()
        val shortCellId = CalculationUtils.getUmtsShortCellIdFromCid(cellId.toInt()).toLong()

        _rncIdOutput.value = rncId.toString()
        _shortCellIdOutput.value = shortCellId.toString()
        _umtsCidError.value = null
    }
}
