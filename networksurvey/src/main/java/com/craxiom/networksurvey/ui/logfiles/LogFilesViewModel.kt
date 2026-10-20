package com.craxiom.networksurvey.ui.logfiles

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Log files page shows; [nowMs] is when the folder was read, for "12 minutes ago". */
data class LogFilesUiState(
    val loading: Boolean = true,
    val listing: LogFilesListing = LogFilesListing.Files(emptyList()),
    val nowMs: Long = 0L,
    val freeSpaceBytes: Long? = null,
    val rolloverMb: Int = NetworkSurveyConstants.DEFAULT_ROLLOVER_SIZE_MB.toInt(),
    val rolloverChoices: List<Int> = emptyList(),
    val rolloverLocked: Boolean = false,
)

/**
 * The Log files page: lists the recent CSV files and sets when a new file starts. The rollover size
 * an organization sets wins, and the row is then locked.
 */
class LogFilesViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: LogFilesRepository = LogFilesRepository.forLogFiles(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : AndroidViewModel(application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val restrictions = AndroidRestrictionsSource(application)

    private val _uiState = MutableStateFlow(LogFilesUiState())
    val uiState: StateFlow<LogFilesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the folder and the rollover setting, for example when the page comes back into view. */
    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            val app = getApplication<Application>()
            val overrideOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
            _uiState.value = LogFilesUiState(
                loading = false,
                listing = repository.list(),
                nowMs = clock(),
                freeSpaceBytes = repository.freeSpaceBytes(),
                rolloverMb = PreferenceUtils.getRolloverSizePreference(app),
                rolloverChoices = app.resources.getStringArray(R.array.log_rollover_size_values).mapNotNull { it.toIntOrNull() },
                rolloverLocked = !overrideOn &&
                    restrictions.snapshot().contains(NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB),
            )
        }
    }

    /** Turns on "Override organization settings", which unlocks the rollover size. */
    fun useOwnSettings() {
        viewModelScope.launch(ioDispatcher) {
            OrganizationOverride.set(preferences, true)
            refresh()
        }
    }

    /** Starts a new file every [megabytes] MB (0 means never). Ignored while the organization sets it. */
    fun setRollover(megabytes: Int) {
        if (_uiState.value.rolloverLocked) return
        viewModelScope.launch(ioDispatcher) {
            // Stored as a String: the logger and the old Settings list both read it that way.
            preferences.edit(commit = true) { putString(NetworkSurveyConstants.PROPERTY_LOG_ROLLOVER_SIZE_MB, megabytes.toString()) }
            refresh()
        }
    }
}
