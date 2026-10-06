package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.mdm.RestrictionsSource
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore
import timber.log.Timber
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/**
 * Runs every survey start, change and stop on one serial executor, so a STOP from the Intent API
 * can never interleave with a START from the screen, and none of the work (file creation, keystore
 * reads) runs on the main thread.
 *
 * It also owns the parts of a request that are not the orchestrator's job: which setup an auto
 * start uses, the "stopped by the user" flag on a managed phone, the start origin shown on the
 * Recording screen, and caching the organization policy.
 *
 * @param orchestrator      The orchestrator to drive.
 * @param store             The user's setups and survey preferences.
 * @param restrictions      The managed configuration.
 * @param isOverrideOn      Reads the "Override organization settings" switch.
 * @param executor          A single thread executor owned by the service.
 * @param onStateChanged    Called after every request so the service republishes its state.
 */
class SurveyCoordinator(
    val orchestrator: SurveyOrchestrator,
    private val store: SurveySetupStore,
    private val restrictions: RestrictionsSource,
    private val isOverrideOn: () -> Boolean,
    private val executor: ExecutorService,
    private val onStateChanged: () -> Unit,
) {
    @Volatile
    private var cachedPolicy: SurveyPolicyState? = null

    /** What started the running survey, or null when nothing runs. */
    @Volatile
    var startOrigin: SurveyOrigin? = null
        private set

    /** The result of the last start or change, for the attention plate. */
    @Volatile
    var lastResult: SurveyStartResult? = null
        private set

    /** @return The organization policy in force, re-read only after [refreshPolicy]. */
    fun policy(): SurveyPolicyState = cachedPolicy ?: refreshPolicy()

    /** Re-reads the managed configuration and the override switch. */
    fun refreshPolicy(): SurveyPolicyState {
        val policy = SurveyPolicy.evaluate(restrictions.snapshot(), isOverrideOn())
        cachedPolicy = policy
        return policy
    }

    /** @return The setup Start uses: the user's working setup with the organization's locks applied. */
    fun effectiveCurrentSetup(): SurveySetup = policy().effectiveSetup(store.getCurrentSetup())

    /** Starts [setup] (or the effective working setup when null). */
    fun startSurvey(setup: SurveySetup?, origin: SurveyOrigin): Future<SurveyStartResult> = submit {
        val result = orchestrator.startSurvey(setup ?: effectiveCurrentSetup(), origin)
        if (result.recording) {
            store.stoppedByUser = false
            if (startOrigin == null) startOrigin = origin
        }
        result
    }

    /** Changes the running survey to [setup] without rolling the Mission ID. */
    fun applySetup(setup: SurveySetup, origin: SurveyOrigin): Future<SurveyStartResult> = submit {
        val wasRecording = orchestrator.isRecording()
        val result = orchestrator.applySetup(setup, origin)
        if (!wasRecording && result.recording) startOrigin = origin
        if (!orchestrator.isRecording()) startOrigin = null
        result
    }

    /** Adds an Intent API request to whatever is running. */
    fun mergeIntoRunningSurvey(setup: SurveySetup, origin: SurveyOrigin): Future<SurveyStartResult> = submit {
        val wasRecording = orchestrator.isRecording()
        val result = orchestrator.mergeIntoRunningSurvey(setup, origin)
        if (!wasRecording && result.recording) startOrigin = origin
        result
    }

    /**
     * Stops everything. A stop by the user on a phone whose organization shapes the survey sets the
     * "stopped by the user" flag, so opening the app does not start it again; the next boot or
     * organization change clears it.
     */
    fun stopSurvey(origin: SurveyOrigin): Future<SurveyStartResult> = submit {
        val result = orchestrator.stopSurvey(origin)
        if (origin == SurveyOrigin.USER && policy().hasOrganizationSetup) store.stoppedByUser = true
        startOrigin = null
        result
    }

    /**
     * Starts the effective working setup if auto start is on and nothing runs yet.
     *
     * @param origin [SurveyOrigin.BOOT] or [SurveyOrigin.APP_OPEN]. A boot clears the "stopped by
     *               the user" flag first; app open respects it.
     * @return The pending result, or null when auto start does not apply.
     */
    fun startAutoSurveyIfEnabled(origin: SurveyOrigin): Future<SurveyStartResult>? {
        if (origin == SurveyOrigin.BOOT) store.stoppedByUser = false
        if (!isAutoStartEnabled()) return null
        if (origin == SurveyOrigin.APP_OPEN && store.stoppedByUser) {
            Timber.i("Auto start skipped because the user stopped the survey")
            return null
        }
        return submit {
            if (orchestrator.isRecording()) {
                SurveyStartResult(emptyMap(), emptyMap(), recording = true)
            } else {
                val result = orchestrator.startSurvey(effectiveCurrentSetup(), origin)
                if (result.recording) startOrigin = origin
                result
            }
        }
    }

    /** @return True if the survey should start on its own at app open and boot. */
    fun isAutoStartEnabled(): Boolean = policy().isAutoStartEnabled(store.userAutoStart)

    /**
     * Records that something outside the coordinator changed the running state (an older screen
     * toggled a destination directly), so the start origin is cleared when the survey stops.
     */
    fun onRunningStateChanged() {
        if (!orchestrator.isRecording()) startOrigin = null
    }

    /**
     * Runs [block] on the serial executor, publishing the state when it finishes. After the service
     * shut the executor down, the returned future fails instead of throwing here.
     */
    fun <T> submit(block: () -> T): Future<T> = try {
        executor.submit(Callable {
            try {
                val value = block()
                if (value is SurveyStartResult) lastResult = value
                value
            } finally {
                onStateChanged()
            }
        })
    } catch (e: RejectedExecutionException) {
        Timber.w("A survey request arrived after the survey service shut down; it is ignored")
        CompletableFuture<T>().apply { completeExceptionally(e) }
    }
}
