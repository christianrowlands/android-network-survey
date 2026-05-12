package com.craxiom.networksurvey.ui.plmn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.data.plmn.PlmnRepository
import com.craxiom.networksurvey.data.plmn.PlmnResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** The two input modes shared on the PLMN lookup screen. */
enum class PlmnMode { Search, Resolve }

/** Sort options surfaced by the result-strip sort pill. */
enum class PlmnSortKey { MccMnc, Country, Provider }

private const val DEBOUNCE_MS = 200L
private const val MIN_QUERY_LEN = 2
private const val MAX_QUERY_LEN = 64
private const val MCC_LEN = 3
private const val MIN_MNC_LEN = 2
private const val MAX_MNC_LEN = 3
private const val MIN_MCC_PREFIX_LEN = 2

/**
 * Backs the PLMN Lookup screen.
 *
 * Both modes feed a single internal [PlmnTrigger] stream so `debounce + flatMapLatest` correctly
 * cancels in-flight calls when the user switches modes or edits a different field. The race where
 * a slow Search response lands into the Resolve view cannot happen.
 *
 * The Resolve mode fires eagerly:
 *  - Both fields filled (MCC=3 digits, MNC=2-3 digits) → `GET /v2/plmn`.
 *  - Only MCC filled (≥2 digits) → `GET /v2/plmn/search?mcc=...`.
 *  - Only MNC filled (≥2 digits) → `GET /v2/plmn/search?mnc=...`.
 *  - Both empty / below threshold → Idle.
 *
 * Search mode fires when the trimmed query has ≥2 characters; it uses the backend's `?q=` free-text
 * parameter that matches across country/iso/operator/brand/region/mcc/mnc/plmn.
 */
@OptIn(FlowPreview::class)
class PlmnLookupViewModel(
    private val repository: PlmnRepository = PlmnRepository.getInstance()
) : ViewModel() {

    private val _mode = MutableStateFlow(PlmnMode.Search)
    val mode: StateFlow<PlmnMode> = _mode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _mcc = MutableStateFlow("")
    val mcc: StateFlow<String> = _mcc.asStateFlow()

    private val _mnc = MutableStateFlow("")
    val mnc: StateFlow<String> = _mnc.asStateFlow()

    private val _results = MutableStateFlow<PlmnResult>(PlmnResult.Idle)
    val results: StateFlow<PlmnResult> = _results.asStateFlow()

    private val _expandedPlmns = MutableStateFlow<Set<String>>(emptySet())
    val expandedPlmns: StateFlow<Set<String>> = _expandedPlmns.asStateFlow()

    private val _sortKey = MutableStateFlow(PlmnSortKey.MccMnc)
    val sortKey: StateFlow<PlmnSortKey> = _sortKey.asStateFlow()

    private val _showSortSheet = MutableStateFlow(false)
    val showSortSheet: StateFlow<Boolean> = _showSortSheet.asStateFlow()

    private val triggers = MutableStateFlow<PlmnTrigger>(PlmnTrigger.Idle)

    init {
        // `triggers` is a MutableStateFlow which already conflates identical values, so an
        // additional `distinctUntilChanged` would be a no-op (and is flagged as deprecated).
        triggers
            .debounce { trigger ->
                // Skip the debounce window for the Idle state so clearing inputs returns to the
                // empty result region without a 200 ms blank flash.
                if (trigger is PlmnTrigger.Idle) 0L else DEBOUNCE_MS
            }
            .onEach { trigger ->
                // Auto-expand the single matching group exactly once per Resolve trigger, so the
                // user can subsequently collapse it without auto-reopen on identical re-emissions.
                if (trigger !is PlmnTrigger.Resolve && trigger !is PlmnTrigger.Idle) {
                    pendingResolveAutoExpand = false
                } else if (trigger is PlmnTrigger.Resolve) {
                    pendingResolveAutoExpand = true
                }
            }
            .flatMapLatest { trigger ->
                when (trigger) {
                    is PlmnTrigger.Idle -> flowOf(PlmnResult.Idle)
                    is PlmnTrigger.Search -> dispatch { repository.search(trigger.query) }
                    is PlmnTrigger.Resolve -> dispatch {
                        repository.resolve(
                            trigger.mcc,
                            trigger.mnc
                        )
                    }

                    is PlmnTrigger.SearchByMcc -> dispatch { repository.searchByMcc(trigger.mcc) }
                    is PlmnTrigger.SearchByMnc -> dispatch { repository.searchByMnc(trigger.mnc) }
                }
            }
            .onEach { _results.value = it }
            .launchIn(viewModelScope)
    }

    private var pendingResolveAutoExpand = false

    /** Emits Loading immediately, then the terminal result. */
    private fun dispatch(call: suspend () -> PlmnResult) = flow {
        emit(PlmnResult.Loading)
        emit(call())
    }

    fun setMode(newMode: PlmnMode) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        // Expanded groups don't carry across modes.
        _expandedPlmns.value = emptySet()
        republishTrigger()
    }

    fun setQuery(value: String) {
        // Cap user-typed/pasted input length to avoid unbounded query strings hitting the gateway.
        _query.value = value.take(MAX_QUERY_LEN)
        _expandedPlmns.value = emptySet()
        republishTrigger()
    }

    fun clearQuery() = setQuery("")

    fun setMcc(value: String) {
        _mcc.value = value.filter(Char::isDigit).take(MCC_LEN)
        _expandedPlmns.value = emptySet()
        republishTrigger()
    }

    fun setMnc(value: String) {
        _mnc.value = value.filter(Char::isDigit).take(MAX_MNC_LEN)
        _expandedPlmns.value = emptySet()
        republishTrigger()
    }

    fun toggleExpanded(plmn: String) {
        val current = _expandedPlmns.value
        _expandedPlmns.value = if (plmn in current) current - plmn else current + plmn
    }

    /**
     * Auto-expand the single matching PLMN when Resolve returns exactly one group. Consumes a
     * one-shot flag set when a new Resolve trigger is dispatched, so that subsequent identical
     * re-emissions (e.g. cache hits on the same MCC+MNC) do NOT override a user collapse.
     */
    fun autoExpandIfSingleGroup(plmnIds: List<String>) {
        if (!pendingResolveAutoExpand) return
        if (_mode.value == PlmnMode.Resolve && plmnIds.size == 1) {
            _expandedPlmns.value = setOf(plmnIds.first())
            pendingResolveAutoExpand = false
        }
    }

    fun setSortKey(key: PlmnSortKey) {
        _sortKey.value = key
    }

    fun openSortSheet() {
        _showSortSheet.value = true
    }

    fun dismissSortSheet() {
        _showSortSheet.value = false
    }

    private fun republishTrigger() {
        triggers.value = computeTrigger()
    }

    private fun computeTrigger(): PlmnTrigger {
        return when (_mode.value) {
            PlmnMode.Search -> {
                val q = _query.value.trim()
                if (q.length < MIN_QUERY_LEN) PlmnTrigger.Idle else PlmnTrigger.Search(q)
            }

            PlmnMode.Resolve -> {
                val mccValue = _mcc.value
                val mncValue = _mnc.value
                when {
                    mccValue.length == MCC_LEN && mncValue.length in MIN_MNC_LEN..MAX_MNC_LEN ->
                        PlmnTrigger.Resolve(mccValue, mncValue)

                    mccValue.length >= MIN_MCC_PREFIX_LEN && mncValue.isEmpty() ->
                        PlmnTrigger.SearchByMcc(mccValue)

                    mncValue.length >= MIN_MNC_LEN && mccValue.isEmpty() ->
                        PlmnTrigger.SearchByMnc(mncValue)

                    else -> PlmnTrigger.Idle
                }
            }
        }
    }
}

/** Internal request shape fed into the trigger flow. */
private sealed class PlmnTrigger {
    object Idle : PlmnTrigger()
    data class Search(val query: String) : PlmnTrigger()
    data class Resolve(val mcc: String, val mnc: String) : PlmnTrigger()
    data class SearchByMcc(val mcc: String) : PlmnTrigger()
    data class SearchByMnc(val mnc: String) : PlmnTrigger()
}
