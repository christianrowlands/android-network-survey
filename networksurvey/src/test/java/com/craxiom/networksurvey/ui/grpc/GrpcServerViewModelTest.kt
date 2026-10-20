package com.craxiom.networksurvey.ui.grpc

import android.content.Context
import androidx.core.content.edit
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import com.craxiom.networksurvey.ui.survey.DestinationSurveyLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Tests saving on the gRPC page: validation, and what a save does to the survey. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GrpcServerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val context = RuntimeEnvironment.getApplication()
    private val preferences = context.getSharedPreferences("grpc_view_model_test", Context.MODE_PRIVATE)
    private val store = SurveySetupStore(preferences)
    private var restrictions: Map<String, Any?> = emptyMap()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences.edit(commit = true) { clear() }
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = GrpcServerViewModel(
        context,
        DestinationSurveyLink(context, Destination.GRPC, store) { SurveyPolicy.evaluate(RestrictionsSnapshot(restrictions), false) },
        preferences,
        lastFailure = { null },
        ioDispatcher = dispatcher,
    )

    private fun TestScope.savesOf(viewModel: GrpcServerViewModel): List<GrpcSaved> {
        val saves = mutableListOf<GrpcSaved>()
        backgroundScope.launch(dispatcher) { viewModel.saved.collect { saves += it } }
        return saves
    }

    private val recording = SurveyRuntimeState(destinations = mapOf(Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.WIFI))))

    private val streaming = SurveyRuntimeState(
        destinations = mapOf(
            Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.WIFI)),
            Destination.GRPC to DestinationRuntime(setOf(RecordType.WIFI)),
        ),
    )

    @Test
    fun `a first save while idle saves the server and turns gRPC on`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val saves = savesOf(viewModel)
        assertTrue(viewModel.form.value.savesAndTurnsOn)

        viewModel.edit { it.copy(host = "grpc.example.net", port = "9090", deviceName = "field-phone-1") }
        viewModel.save()

        assertEquals(listOf(GrpcSaved(GrpcSaveResult.TURNED_ON, firstSetup = true)), saves)
        assertEquals(GrpcConnectionSettings("grpc.example.net", 9090, "field-phone-1"), GrpcConnectionSettings.fromPreferences(preferences))
        assertTrue(Destination.GRPC in store.getCurrentSetup().destinations)
        assertEquals(GrpcStatus.Ready("grpc.example.net:9090"), viewModel.form.value.status)
    }

    @Test
    fun `a first save while recording leaves the running survey alone and says how to stream`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val saves = savesOf(viewModel)
        viewModel.onRuntime(recording)

        viewModel.edit { it.copy(host = "grpc.example.net") }
        viewModel.save()

        assertEquals(listOf(GrpcSaved(GrpcSaveResult.TURN_ON_TO_STREAM, firstSetup = true)), saves)
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
    }

    @Test
    fun `a first save that the organization already sends to gRPC starts the stream`() = runTest(dispatcher) {
        restrictions = mapOf(SurveyMdmKeys.DESTINATION_GRPC to "on")
        val viewModel = viewModel()
        val saves = savesOf(viewModel)
        viewModel.onRuntime(recording)

        viewModel.edit { it.copy(host = "grpc.example.net") }
        viewModel.save()

        assertEquals(listOf(GrpcSaved(GrpcSaveResult.STARTING, firstSetup = true)), saves)
    }

    @Test
    fun `an organization that keeps gRPC off still lets the server be saved`() = runTest(dispatcher) {
        restrictions = mapOf(SurveyMdmKeys.DESTINATION_GRPC to "off")
        val viewModel = viewModel()
        val saves = savesOf(viewModel)
        assertTrue(viewModel.form.value.organizationOff)

        viewModel.edit { it.copy(host = "grpc.example.net") }
        viewModel.save()

        assertEquals(listOf(GrpcSaved(GrpcSaveResult.SAVED, firstSetup = true)), saves)
        assertEquals(setOf(Destination.LOG_FILES), store.getCurrentSetup().destinations)
    }

    @Test
    fun `a new address while streaming reconnects, and other edits just save`() = runTest(dispatcher) {
        GrpcConnectionSettings.save(preferences, GrpcConnectionSettings("old.example.net", 2621, ""))
        store.setCurrentSetup(SurveySetup.custom(setOf(RecordType.WIFI), setOf(Destination.LOG_FILES, Destination.GRPC)))
        val viewModel = viewModel()
        val saves = savesOf(viewModel)
        viewModel.onRuntime(streaming)
        assertEquals("old.example.net", viewModel.form.value.host)

        viewModel.edit { it.copy(deviceName = "field-phone-1") }
        viewModel.save()
        viewModel.edit { it.copy(host = "grpc.example.net") }
        viewModel.save()

        assertEquals(
            listOf(GrpcSaved(GrpcSaveResult.SAVED, firstSetup = false), GrpcSaved(GrpcSaveResult.RECONNECTING, firstSetup = false)),
            saves,
        )
        assertEquals(setOf(Destination.LOG_FILES, Destination.GRPC), store.getCurrentSetup().destinations)
    }

    @Test
    fun `invalid fields are flagged and nothing is saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val saves = savesOf(viewModel)

        viewModel.edit { it.copy(host = "", port = "0") }
        viewModel.save()

        val form = viewModel.form.value
        assertTrue(form.hostError)
        assertTrue(form.portError)
        assertEquals(1, form.errorEpoch)
        assertEquals(emptyList<GrpcSaved>(), saves)
        assertEquals(null, GrpcConnectionSettings.fromPreferences(preferences))
    }
}
