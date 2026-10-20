package com.craxiom.networksurvey.ui.signals

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Activity-scoped state shared by the Signals tab and its views: the scan interval of each sub-tab,
 * the GNSS page showing (which decides the top bar actions), whether the GNSS filter is on, and the
 * SIM the Cellular view is showing (for the scan strip and the map). The views write the last two; the tab and the map read
 * them.
 */
class SignalsViewModel(application: Application) : AndroidViewModel(application) {

    // Overridable for tests, which run the reads on the test thread.
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val mutableIntervals = MutableStateFlow(SignalsSubTab.entries.associateWith { it.defaultIntervalSeconds })

    /** The scan interval of each sub-tab in seconds, organization settings applied. */
    val intervals: StateFlow<Map<SignalsSubTab, Int>> = mutableIntervals.asStateFlow()

    private val mutableGnssPage = MutableStateFlow(GnssPage.STATUS)

    /** The GNSS page showing, kept while the activity lives (Status after a restart). */
    val gnssPage: StateFlow<GnssPage> = mutableGnssPage.asStateFlow()

    private val mutableViewedSim = MutableStateFlow<ViewedSim?>(null)

    /** The SIM the Cellular view shows, or null before it has said. */
    val viewedSim: StateFlow<ViewedSim?> = mutableViewedSim.asStateFlow()

    private val gnssFilterKey = application.getString(R.string.pref_key_default_sat_filter)
    private val mutableGnssFilterActive = MutableStateFlow(readGnssFilterActive())

    /** Whether the GNSS filter hides some constellations, which badges the GNSS filter action. */
    val gnssFilterActive: StateFlow<Boolean> = mutableGnssFilterActive.asStateFlow()

    // Held in a field: SharedPreferences keeps its listeners weakly.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (SignalsSubTab.entries.any { it.intervalKey == key }) refreshIntervals()
        if (key == gnssFilterKey) mutableGnssFilterActive.value = readGnssFilterActive()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshIntervals()
    }

    /**
     * Re-reads the scan intervals, off the main thread because an organization's value comes from
     * the restrictions service. Called on resume too, since a configuration push sends no
     * preference change.
     */
    fun refreshIntervals() {
        viewModelScope.launch(ioDispatcher) {
            mutableIntervals.value = SignalsSubTab.entries.associateWith { subTab ->
                val ms = PreferenceUtils.getScanRatePreferenceMs(subTab.intervalKey, subTab.defaultIntervalSeconds, getApplication())
                (ms / MS_PER_SECOND).coerceAtLeast(1)
            }
        }
    }

    /** Shows [page] in the GNSS view; it also decides the top bar actions. */
    fun selectGnssPage(page: GnssPage) {
        mutableGnssPage.value = page
    }

    /** Called by the Cellular view with the SIM it shows, or null when it no longer knows. */
    fun setViewedSim(sim: ViewedSim?) {
        mutableViewedSim.value = sim
    }

    private fun readGnssFilterActive(): Boolean = PreferenceUtils.gnssFilter(getApplication(), preferences).isNotEmpty()

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private companion object {
        const val MS_PER_SECOND = 1000
    }
}
