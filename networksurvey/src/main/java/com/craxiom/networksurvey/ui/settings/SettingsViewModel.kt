package com.craxiom.networksurvey.ui.settings

import android.app.Application
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource
import com.craxiom.networksurvey.survey.mdm.OrganizationOverride
import com.craxiom.networksurvey.ui.theme.ThemeMode
import com.craxiom.networksurvey.util.AppInstanceId
import com.craxiom.networksurvey.util.BatteryOptimizationHelper
import com.craxiom.networksurvey.util.MdmUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A question the page must ask before a change settles. */
sealed interface SettingsPrompt {
    /** The streaming queue limit was set to 0 (no limit); cancelling restores [previous]. */
    data class DisableQueueLimit(val previous: String) : SettingsPrompt

    /** The streaming queue limit was set above the recommended maximum. */
    data object HighQueueLimit : SettingsPrompt
}

/** Something the page says once, as a snackbar. */
sealed interface SettingsNotice {
    /** The Bluetooth interval was raised to the minimum Android allows. */
    data class BluetoothMinimum(val seconds: Int) : SettingsNotice
}

/**
 * The Settings page: reads every setting (and what the organization manages) into
 * [SettingsUiState], writes changes to the shared preferences with the types and checks the old
 * preference screen used, and re-reads whenever any setting changes, wherever it changed.
 */
class SettingsViewModel internal constructor(
    application: Application,
    private val preferences: SharedPreferences,
    private val environment: (appInstanceId: String?) -> SettingsReader.Environment,
    private val ioDispatcher: CoroutineDispatcher,
    fetchAppInstanceId: (Application, (String) -> Unit) -> Unit,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        PreferenceManager.getDefaultSharedPreferences(application),
        { appInstanceId -> androidEnvironment(application, appInstanceId) },
        Dispatchers.IO,
        AppInstanceId::fetch,
    )

    private var appInstanceId: String? = null
    private var previousQueueLimit: String = preferences.stringOr(
        NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT.toString(),
    )

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow<SettingsPrompt?>(null)
    val prompt: StateFlow<SettingsPrompt?> = _prompt.asStateFlow()

    private val _notice = MutableStateFlow<SettingsNotice?>(null)
    val notice: StateFlow<SettingsNotice?> = _notice.asStateFlow()

    /** The phone's side (managed configuration, statuses), read again only on [refresh]: binder calls, not per change. */
    private var current: SettingsReader.Environment = environment(null)

    // Held in a field: the preferences keep listeners only weakly
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY &&
            !preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MDM_OVERRIDE_KEY, false)
        ) {
            // Back to the organization's settings: copy its values in again, as the old screen did
            ManagedSettingsSync.write(preferences, current.restrictions)
        }
        if (key == null || key in SettingsReader.KEYS) reread()
    }

    init {
        ManagedSettingsSync.write(preferences, current.restrictions)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        reread()
        fetchAppInstanceId(application) { id ->
            appInstanceId = id
            refresh()
        }
    }

    /** Reads the phone's side again (on every return to the page) and then every setting. */
    fun refresh() {
        current = environment(appInstanceId)
        reread()
    }

    private fun reread() {
        _state.value = SettingsReader.read(preferences, current)
    }

    fun setBoolean(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }

    /** The override writes synchronously (the service reacts to it), so it runs off the main thread. */
    fun setOverride(on: Boolean) {
        viewModelScope.launch(ioDispatcher) { OrganizationOverride.set(preferences, on) }
    }

    /** Stores [seconds]; Bluetooth is raised to the minimum Android allows between scans, with a notice. */
    fun setScanRate(rate: ScanRate, seconds: Int) {
        val minimum = NetworkSurveyConstants.MINIMUM_BLUETOOTH_SCAN_INTERVAL_SECONDS
        val value = if (rate == ScanRate.BLUETOOTH && seconds < minimum) {
            _notice.value = SettingsNotice.BluetoothMinimum(minimum)
            minimum
        } else {
            seconds
        }
        preferences.edit { putString(rate.key, value.toString()) }
    }

    fun setLocationProvider(index: Int) =
        preferences.edit { putString(NetworkSurveyConstants.PROPERTY_LOCATION_PROVIDER, index.toString()) }

    /** Stores the theme and applies it app-wide (AppCompat recreates the activity). */
    fun setTheme(value: String) {
        preferences.edit(commit = true) { putString(NetworkSurveyConstants.PROPERTY_APP_THEME, value) }
        ThemeMode.applyFromPreferences(getApplication())
    }

    fun setUnits(value: String) = preferences.edit { putString(NetworkSurveyConstants.PROPERTY_MEASUREMENT_UNITS, value) }

    /** Stores the threshold as an int, kept within 0 (never pause) to [BATTERY_THRESHOLD_MAX]. */
    fun setBatteryThreshold(percent: Int) = preferences.edit {
        putInt(NetworkSurveyConstants.PROPERTY_BATTERY_THRESHOLD_PERCENT, percent.coerceIn(0, BATTERY_THRESHOLD_MAX))
    }

    /** Stores the limit; 0 (no limit) and very high limits ask first, as before. */
    fun setQueueLimit(limit: Int) {
        preferences.edit { putString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, limit.toString()) }
        when {
            limit == NetworkSurveyConstants.STREAMING_QUEUE_LIMIT_DISABLED -> _prompt.value = SettingsPrompt.DisableQueueLimit(previousQueueLimit)
            limit > NetworkSurveyConstants.HIGH_STREAMING_QUEUE_LIMIT_THRESHOLD -> _prompt.value = SettingsPrompt.HighQueueLimit
            else -> previousQueueLimit = limit.toString()
        }
    }

    /** Keeps the limit just entered (no limit, or the high one). */
    fun keepQueueLimit() {
        previousQueueLimit = preferences.stringOr(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, previousQueueLimit)
        _prompt.value = null
    }

    /** Puts back the limit from before "no limit" was entered. */
    fun revertQueueLimit(previous: String) {
        preferences.edit { putString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, previous) }
        _prompt.value = null
    }

    /** Replaces a high limit with the recommended one. */
    fun useRecommendedQueueLimit() {
        val recommended = NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT.toString()
        preferences.edit { putString(NetworkSurveyConstants.PROPERTY_STREAMING_QUEUE_LIMIT, recommended) }
        previousQueueLimit = recommended
        _prompt.value = null
    }

    fun consumeNotice() {
        _notice.value = null
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val BATTERY_THRESHOLD_MAX = 95

        /** The phone's side of the page: the managed configuration and statuses read live. */
        fun androidEnvironment(application: Application, appInstanceId: String?) = SettingsReader.Environment(
            restrictions = AndroidRestrictionsSource(application).snapshot(),
            autoStartPolicy = SurveyTypeResolver.currentPolicy(application).autoStart,
            uploadsAllowed = MdmUtils.isExternalDataUploadAllowed(application),
            nsAnalyticsAllowed = MdmUtils.isNsAnalyticsAllowed(application),
            batteryOptimizationOff = BatteryOptimizationHelper(application).isBatteryOptimizationDisabled(),
            deviceSerial = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID),
            appInstanceId = appInstanceId,
        )
    }
}
