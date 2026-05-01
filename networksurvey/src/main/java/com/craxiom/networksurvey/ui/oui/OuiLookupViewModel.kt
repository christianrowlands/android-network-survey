package com.craxiom.networksurvey.ui.oui

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.data.oui.OuiRepository
import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.ui.manufacturer.ManufacturerLabel
import com.craxiom.networksurvey.ui.manufacturer.toManufacturerLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_HEX_CHARS = 12
private const val OUI_HEX_CHARS = 6
private const val LOADING_GRACE_MS = 250L

/**
 * Backs the OUI Lookup screen. Owns the formatted query string and the per-keystroke lookup
 * dispatch. The screen reads a single coherent [OuiLookupUiState] so query and phase never disagree.
 *
 * Lookup memoization: typing additional NIC bytes after the OUI is fully entered does not re-fetch,
 * since the 24-bit prefix is unchanged. Combined with the 250 ms loading-grace delay, this keeps
 * the screen quiet on cache hits.
 */
@HiltViewModel
class OuiLookupViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val repository: OuiRepository = OuiRepository.getInstance(application)

    private val _state = MutableStateFlow(OuiLookupUiState.empty())
    val state: StateFlow<OuiLookupUiState> = _state.asStateFlow()

    private var currentJob: Job? = null
    private var lastLookedUpPrefix: String? = null
    private var lastResolvedPhase: OuiLookupUiState.Phase? = null

    fun onQueryChanged(raw: String) {
        val formatted = formatMacInput(raw)
        val hexCount = countHexChars(formatted)

        when {
            hexCount == 0 -> {
                currentJob?.cancel()
                lastLookedUpPrefix = null
                lastResolvedPhase = null
                _state.value = OuiLookupUiState.empty()
            }

            hexCount < OUI_HEX_CHARS -> {
                currentJob?.cancel()
                lastLookedUpPrefix = null
                lastResolvedPhase = null
                _state.value = OuiLookupUiState(
                    query = formatted,
                    hexCharCount = hexCount,
                    phase = OuiLookupUiState.Phase.Partial(OUI_HEX_CHARS - hexCount)
                )
            }

            else -> {
                val newPrefix = formatted.replace(":", "").substring(0, OUI_HEX_CHARS)
                if (newPrefix == lastLookedUpPrefix && lastResolvedPhase != null) {
                    // Same OUI prefix, just keep the existing phase and update the displayed query.
                    _state.value = OuiLookupUiState(
                        query = formatted,
                        hexCharCount = hexCount,
                        phase = lastResolvedPhase!!
                    )
                } else {
                    dispatchLookup(formatted, hexCount, newPrefix)
                }
            }
        }
    }

    fun onClear() {
        onQueryChanged("")
    }

    private fun dispatchLookup(formattedQuery: String, hexCount: Int, prefix: String) {
        currentJob?.cancel()
        // Optimistically update query without changing phase yet, so the partial-to-result
        // transition does not flicker. The grace job below will swap to Loading or Resolved.
        _state.update {
            it.copy(query = formattedQuery, hexCharCount = hexCount)
        }

        val paddedFullMac = prefix.padEnd(MAX_HEX_CHARS, '0')

        currentJob = viewModelScope.launch {
            val gracePhase = OuiLookupUiState.Phase.Loading
            val gracePromotion = launch {
                delay(LOADING_GRACE_MS)
                _state.update { current ->
                    if (current.query == formattedQuery) {
                        current.copy(phase = gracePhase)
                    } else current
                }
            }

            val result: OuiResult = try {
                repository.lookup(paddedFullMac)
            } catch (t: Throwable) {
                OuiResult.OFFLINE
            }

            gracePromotion.cancel()

            val phase = result.toPhase()
            // Only memoize terminal phases. Loading is a non-terminal marker; if it ever leaks
            // through (e.g. from `OuiResult.LOADING`'s constant), do not cache it as the answer.
            if (phase !is OuiLookupUiState.Phase.Loading) {
                lastLookedUpPrefix = prefix
                lastResolvedPhase = phase
            }
            _state.update { current ->
                if (current.query == formattedQuery) {
                    current.copy(phase = phase)
                } else current
            }
        }
    }

    private fun OuiResult.toPhase(): OuiLookupUiState.Phase {
        return when (val label = this.toManufacturerLabel(getApplication())) {
            is ManufacturerLabel.Resolved -> OuiLookupUiState.Phase.Resolved(label.vendor)
            ManufacturerLabel.Randomized -> OuiLookupUiState.Phase.Randomized
            ManufacturerLabel.Unknown -> OuiLookupUiState.Phase.NotFound
            ManufacturerLabel.Private -> OuiLookupUiState.Phase.PrivateVendor
            ManufacturerLabel.SharedVendorBlock -> OuiLookupUiState.Phase.SharedVendorBlock
            ManufacturerLabel.Offline -> OuiLookupUiState.Phase.Offline
            ManufacturerLabel.DisabledByUser -> OuiLookupUiState.Phase.DisabledByUser
            ManufacturerLabel.DisabledByAdmin -> OuiLookupUiState.Phase.DisabledByAdmin
            ManufacturerLabel.Loading -> OuiLookupUiState.Phase.Loading
        }
    }
}

/**
 * Coherent UI state for the OUI Lookup screen. The [query] and [phase] always describe the same
 * input; the screen never has to reconcile them across two flows.
 */
data class OuiLookupUiState(
    val query: String,
    val hexCharCount: Int,
    val phase: Phase,
) {
    sealed interface Phase {
        data object Empty : Phase
        data class Partial(val remaining: Int) : Phase
        data object Loading : Phase
        data class Resolved(val vendor: String) : Phase
        data object NotFound : Phase
        data object Randomized : Phase
        data object PrivateVendor : Phase
        data object SharedVendorBlock : Phase
        data object Offline : Phase
        data object DisabledByUser : Phase
        data object DisabledByAdmin : Phase
    }

    companion object {
        fun empty() = OuiLookupUiState(query = "", hexCharCount = 0, phase = Phase.Empty)
    }
}

/**
 * Strips non-hex characters from [raw], uppercases, caps at 12 hex chars, and re-inserts colons
 * every two characters. Pure function: easy to unit test.
 */
fun formatMacInput(raw: String): String {
    val hex = raw.uppercase()
        .filter { it in '0'..'9' || it in 'A'..'F' }
        .take(MAX_HEX_CHARS)
    if (hex.isEmpty()) return ""
    return hex.chunked(2).joinToString(":")
}

/**
 * Formats [raw] and recomputes the cursor position so it lands after the same hex character the
 * user just typed, even when the formatter inserts a colon in front of it. Solves the cursor-jump
 * bug present when an `OutlinedTextField` is fed a length-changing formatted value via the plain
 * `String` overload.
 *
 * `OutlinedTextField` also fires `onValueChange` on selection-only changes (handle drags, cursor
 * moves). When the formatter does not actually change the text, we return [raw] unchanged so the
 * user's selection range is preserved instead of being collapsed to a single cursor.
 */
fun formatMacInput(raw: TextFieldValue): TextFieldValue {
    val (text, cursor) = computeFormattedCursor(raw.text, raw.selection.end)
    if (text == raw.text) return raw
    return TextFieldValue(text, TextRange(cursor))
}

/**
 * Pure helper that returns the formatted MAC text and the cursor index that should follow the
 * same hex character the user's cursor was after in [rawText]. Lives next to [formatMacInput] so
 * the cursor logic is unit-testable on the JVM with no Compose dependency.
 *
 * Algorithm: count hex chars in `rawText[0, rawCursor)` (clamping `rawCursor` defensively, and
 * capping at the 12-hex limit), then walk the formatted output left-to-right counting non-colon
 * chars until that count is reached. The cursor is the position immediately after the last
 * counted hex char.
 */
internal fun computeFormattedCursor(rawText: String, rawCursor: Int): Pair<String, Int> {
    val formatted = formatMacInput(rawText)
    val safeCursor = rawCursor.coerceIn(0, rawText.length)
    val hexBefore = rawText.substring(0, safeCursor)
        .count { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        .coerceAtMost(MAX_HEX_CHARS)
    if (hexBefore == 0) return formatted to 0
    var seenHex = 0
    var idx = 0
    while (idx < formatted.length && seenHex < hexBefore) {
        if (formatted[idx] != ':') seenHex++
        idx++
    }
    return formatted to idx
}

private fun countHexChars(formatted: String): Int = formatted.count { it != ':' }
