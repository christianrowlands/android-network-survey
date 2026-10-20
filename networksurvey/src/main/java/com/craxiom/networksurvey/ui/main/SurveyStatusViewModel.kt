package com.craxiom.networksurvey.ui.main

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.RecordType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The part of the running survey the whole app shell needs: what is recorded and whether it is
 * paused.
 */
data class SurveyStatus(
    val recording: Boolean = false,
    val recordTypes: Set<RecordType> = emptySet(),
    val pausedForBattery: Boolean = false,
    val pausedForBackpressure: Boolean = false,
) {
    /** True while scanning is paused for low battery or a full MQTT queue. */
    val paused: Boolean get() = pausedForBattery || pausedForBackpressure

    /**
     * @return True when the screen should stay on: a survey records, the user wants the screen on
     * while recording, and the survey is not paused for low battery (a lit screen would work
     * against the pause).
     */
    fun keepsScreenOn(settingOn: Boolean): Boolean = settingOn && recording && !pausedForBattery

    companion object {
        fun from(state: SurveyRuntimeState) = SurveyStatus(
            recording = state.isRecording,
            recordTypes = state.recordTypes,
            pausedForBattery = state.pausedForBattery,
            pausedForBackpressure = state.pausedForBackpressure,
        )
    }
}

/**
 * Activity-scoped survey status for the shell: the Signals scan strip reads [status], and the
 * activity window keeps the screen on while [keepScreenOn] is true.
 *
 * It only reads the service's published survey state; it registers no record or location
 * listeners, so it never keeps the service in use. Bind it with
 * [com.craxiom.networksurvey.ui.common.ServiceConnectionHandler] at the activity level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val service = MutableStateFlow<NetworkSurveyService?>(null)
    private val keepScreenOnSetting = MutableStateFlow(readKeepScreenOnSetting())

    // Held in a field: SharedPreferences keeps its listeners weakly.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == NetworkSurveyConstants.PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING) {
            keepScreenOnSetting.value = readKeepScreenOnSetting()
        }
    }

    /** The running survey, or an idle status while the service is not bound. */
    val status: StateFlow<SurveyStatus> = service
        .flatMapLatest { bound -> bound?.surveyStateFlow?.map(SurveyStatus::from) ?: flowOf(SurveyStatus()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SurveyStatus())

    /** True while the screen should stay on, whichever screen is showing. */
    val keepScreenOn: StateFlow<Boolean> = combine(status, keepScreenOnSetting) { current, settingOn ->
        current.keepsScreenOn(settingOn)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    /** Called with the bound service, or null before it is unbound. */
    fun onServiceChanged(service: NetworkSurveyService?) {
        this.service.value = service
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun readKeepScreenOnSetting(): Boolean = preferences.getBoolean(
        NetworkSurveyConstants.PROPERTY_KEEP_SCREEN_ON_WHILE_RECORDING,
        NetworkSurveyConstants.DEFAULT_KEEP_SCREEN_ON_WHILE_RECORDING,
    )
}
