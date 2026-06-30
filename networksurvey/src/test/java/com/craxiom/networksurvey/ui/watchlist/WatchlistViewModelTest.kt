package com.craxiom.networksurvey.ui.watchlist

import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [WatchlistViewModel]'s synchronous enable/disable behavior. Guards the `watchlist_enabled`
 * preference write that the deep-link import relies on (an import auto-enables the feature). The main
 * dispatcher is a test dispatcher so the ViewModel's init collector does not run; `setWatchlistEnabled`
 * itself is synchronous and is exercised directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Clear any import stashed by another test so the ViewModel's init consume is a no-op.
        WatchlistImportHolder.consume()
        runBlocking {
            SurveyDatabase.getInstance(RuntimeEnvironment.getApplication()).watchlistDao().deleteAll()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setWatchlistEnabled writes the preference and updates ui state`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val viewModel = WatchlistViewModel(app)

        viewModel.setWatchlistEnabled(true)
        assertTrue(prefs.getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, false))
        assertTrue(viewModel.uiState.value.watchlistEnabled)

        viewModel.setWatchlistEnabled(false)
        assertFalse(prefs.getBoolean(NetworkSurveyConstants.PROPERTY_WATCHLIST_ENABLED, true))
        assertFalse(viewModel.uiState.value.watchlistEnabled)
    }
}
