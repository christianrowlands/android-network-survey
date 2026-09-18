package com.craxiom.networksurvey.ui.cellular.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.data.band.BandMatchGroup
import com.craxiom.networksurvey.data.band.BandReading
import com.craxiom.networksurvey.data.band.BandReferenceRepository
import com.craxiom.networksurvey.data.band.BandSearch
import com.craxiom.networksurvey.data.band.BandTechnology
import com.craxiom.networksurvey.data.band.CellularBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How the band list is ordered. */
enum class BandSortKey { BAND, FREQUENCY, WIDEST_CHANNEL }

/**
 * State backing the band browser: the catalogue, the active filters, and the interpretation of
 * whatever the user has typed.
 *
 * Search never replaces the catalogue. Interpretation groups are shown above a list that is
 * always present, so a query matching nothing reads as "that value means nothing" rather than as
 * an empty table.
 */
class BandBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val _allBands = MutableStateFlow<List<CellularBand>>(emptyList())

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _reading = MutableStateFlow<BandReading?>(null)
    val reading: StateFlow<BandReading?> = _reading.asStateFlow()

    private val _technologies = MutableStateFlow(setOf(BandTechnology.LTE, BandTechnology.NR))
    val technologies: StateFlow<Set<BandTechnology>> = _technologies.asStateFlow()

    private val _resolvingOnly = MutableStateFlow(false)
    val resolvingOnly: StateFlow<Boolean> = _resolvingOnly.asStateFlow()

    private val _sortKey = MutableStateFlow(BandSortKey.BAND)
    val sortKey: StateFlow<BandSortKey> = _sortKey.asStateFlow()

    private val _matches = MutableStateFlow<List<BandMatchGroup>>(emptyList())
    val matches: StateFlow<List<BandMatchGroup>> = _matches.asStateFlow()

    private val _catalogue = MutableStateFlow<List<CellularBand>>(emptyList())
    val catalogue: StateFlow<List<CellularBand>> = _catalogue.asStateFlow()

    private val _selectedBand = MutableStateFlow<CellularBand?>(null)
    val selectedBand: StateFlow<CellularBand?> = _selectedBand.asStateFlow()

    init {
        viewModelScope.launch {
            _allBands.value = BandReferenceRepository.bands(getApplication())
            _loading.value = false
            recompute()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        recompute()
    }

    fun setReading(value: BandReading?) {
        _reading.value = value
        recompute()
    }

    fun toggleTechnology(technology: BandTechnology) {
        val current = _technologies.value
        // Never let the user filter everything away; the last one stays on.
        _technologies.value = when {
            technology !in current -> current + technology
            current.size > 1 -> current - technology
            else -> current
        }
        recompute()
    }

    fun setResolvingOnly(value: Boolean) {
        _resolvingOnly.value = value
        recompute()
    }

    fun setSortKey(value: BandSortKey) {
        _sortKey.value = value
        recompute()
    }

    fun selectBand(band: CellularBand?) {
        _selectedBand.value = band
    }

    private fun recompute() {
        val visible = _allBands.value
            .filter { it.technology in _technologies.value }
            .filter { !_resolvingOnly.value || it.resolvesLiveCells }
        _catalogue.value = sort(visible)
        // Interpretation runs against every band, not the filtered view, so a chip cannot hide
        // the answer to a question the user explicitly asked.
        _matches.value = BandSearch.interpret(_query.value, _allBands.value, _reading.value)
            .map { it.copy(bands = sort(it.bands)) }
    }

    private fun sort(bands: List<CellularBand>): List<CellularBand> = when (_sortKey.value) {
        BandSortKey.BAND -> bands.sortedWith(compareBy({ it.technology }, { it.number }))
        BandSortKey.FREQUENCY -> bands.sortedWith(
            compareBy({ it.primaryMhz?.start ?: Double.MAX_VALUE }, { it.number })
        )

        BandSortKey.WIDEST_CHANNEL -> bands.sortedWith(
            compareByDescending<CellularBand> { it.widestBandwidthMhz ?: -1.0 }.thenBy { it.number }
        )
    }
}
