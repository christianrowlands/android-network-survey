package com.craxiom.networksurvey.ui.survey

import android.content.Context
import android.content.RestrictionsManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * The Survey view model's commands against a gateway that reads the stored working setup the way
 * the service does, on the real IO dispatcher so a store write and a command can race.
 */
@RunWith(RobolectricTestRunner::class)
class SurveyViewModelTest {

    private val application = RuntimeEnvironment.getApplication()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val store = SurveySetupStore(preferences)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        // The destination rows read the (here empty) MDM restrictions
        val restrictions = application.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
    }

    @Test
    fun eachApplyNow_appliesItsOwnDraft_neverTheOneBefore() {
        val viewModel = SurveyViewModel(application, preferences, store, ioDispatcher = Dispatchers.IO)
        val gateway = StoreReadingGateway(store)
        viewModel.onServiceChanged(gateway)
        val drafts = List(APPLIES) { if (it % 2 == 0) WITHOUT_BLUETOOTH else WITH_BLUETOOTH }

        drafts.forEachIndexed { index, types ->
            viewModel.applyDraft(types, setOf(Destination.LOG_FILES))
            awaitTrue { gateway.applied.size == index + 1 && !viewModel.commandInFlight.value }
        }

        assertEquals(drafts, gateway.applied.map { it.recordTypes })
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(POLL_MS)
        }
    }

    /** Applies what the store holds when the apply runs, as the service's survey coordinator does. */
    private class StoreReadingGateway(private val store: SurveySetupStore) : SurveyServiceGateway {
        val applied: MutableList<SurveySetup> = Collections.synchronizedList(mutableListOf())
        private val result = SurveyStartResult(emptyMap(), emptyMap(), recording = true)

        override val state: StateFlow<SurveyRuntimeState> = MutableStateFlow(SurveyRuntimeState())
        override fun counts(): Map<RecordType, Long> = emptyMap()
        override fun lastRecordTimeMs(): Long? = null
        override fun communityCounts(): Map<RecordType, Long> = emptyMap()
        override fun communityLastRecordTimeMs(): Long? = null
        override fun activeMissionId(): String? = null
        override fun startWorkingSetup(): Future<SurveyStartResult> = CompletableFuture.completedFuture(result)
        override fun applyWorkingSetup(): Future<SurveyStartResult> {
            applied += store.getCurrentSetup()
            return CompletableFuture.completedFuture(result)
        }

        override fun stop(): Future<SurveyStartResult> = CompletableFuture.completedFuture(result)
        override fun retrySkipped(): Future<SurveyStartResult?> = CompletableFuture.completedFuture(result)
        override fun isMqttBrokerConfigured(): Boolean = false
        override fun latestLocation(): Location? = null
        override fun registerLocationListener(listener: LocationListener) = Unit
        override fun unregisterLocationListener(listener: LocationListener) = Unit
    }

    private companion object {
        const val APPLIES = 10
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
        val WITHOUT_BLUETOOTH = setOf(RecordType.CELLULAR, RecordType.WIFI)
        val WITH_BLUETOOTH = setOf(RecordType.CELLULAR, RecordType.WIFI, RecordType.BLUETOOTH)
    }
}
