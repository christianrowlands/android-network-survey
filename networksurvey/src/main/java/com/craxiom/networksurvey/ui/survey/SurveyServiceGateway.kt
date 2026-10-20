package com.craxiom.networksurvey.ui.survey

import android.location.Location
import android.location.LocationListener
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.engine.SurveyOrigin
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.engine.SurveyStartResult
import com.craxiom.networksurvey.survey.model.RecordType
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Future

/**
 * The slice of [NetworkSurveyService] the Survey screen uses. Every start and stop goes through
 * the service's survey coordinator; the screen never toggles a destination directly.
 */
interface SurveyServiceGateway {
    val state: StateFlow<SurveyRuntimeState>

    fun counts(): Map<RecordType, Long>

    fun lastRecordTimeMs(): Long?

    /** Per record type counts of the records saved for the community databases this session. */
    fun communityCounts(): Map<RecordType, Long>

    /** When the last record was saved for the community databases, or null if none has been. */
    fun communityLastRecordTimeMs(): Long?

    /** The Mission ID of the running mission session, or null when none is active. */
    fun activeMissionId(): String?

    /** Starts the user's working setup with the organization's locks applied. */
    fun startWorkingSetup(): Future<SurveyStartResult>

    /** Changes the running survey to the working setup (with locks) without rolling the Mission ID. */
    fun applyWorkingSetup(): Future<SurveyStartResult>

    fun stop(): Future<SurveyStartResult>

    /** Retries what the running survey asked for and left out, whoever started it. */
    fun retrySkipped(): Future<SurveyStartResult?>

    /** True when MQTT has a broker, including one an Intent API request supplied. */
    fun isMqttBrokerConfigured(): Boolean

    fun latestLocation(): Location?

    fun registerLocationListener(listener: LocationListener)

    fun unregisterLocationListener(listener: LocationListener)
}

/** The production [SurveyServiceGateway], a thin pass-through to the bound service. */
class NetworkSurveyServiceGateway(private val service: NetworkSurveyService) : SurveyServiceGateway {

    override val state: StateFlow<SurveyRuntimeState> get() = service.surveyStateFlow

    override fun counts(): Map<RecordType, Long> = service.sessionCounters.snapshot()

    override fun lastRecordTimeMs(): Long? = service.sessionCounters.lastRecordTimeMs()

    override fun communityCounts(): Map<RecordType, Long> = service.communityCounters.snapshot()

    override fun communityLastRecordTimeMs(): Long? = service.communityCounters.lastRecordTimeMs()

    override fun activeMissionId(): String? = service.rolledMissionId?.takeIf { service.isMissionSessionActive }

    override fun startWorkingSetup(): Future<SurveyStartResult> = service.startSurvey(null, SurveyOrigin.USER)

    override fun applyWorkingSetup(): Future<SurveyStartResult> = service.applySurveySetup(null, SurveyOrigin.USER)

    override fun stop(): Future<SurveyStartResult> = service.stopSurvey(SurveyOrigin.USER)

    override fun retrySkipped(): Future<SurveyStartResult?> = service.retrySkippedSurveyItems()

    override fun isMqttBrokerConfigured(): Boolean = service.hasMqttBrokerConfigured()

    override fun latestLocation(): Location? = service.primaryLocationListener?.latestLocation

    override fun registerLocationListener(listener: LocationListener) {
        service.primaryLocationListener?.registerListener(listener)
    }

    override fun unregisterLocationListener(listener: LocationListener) {
        service.primaryLocationListener?.unregisterListener(listener)
    }
}
