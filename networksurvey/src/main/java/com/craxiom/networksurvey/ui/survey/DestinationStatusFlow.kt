package com.craxiom.networksurvey.ui.survey

import android.content.Context
import com.craxiom.networksurvey.survey.engine.SurveyRuntimeState
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.status.DeliveryQueues
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.ui.survey.model.RecordingUiMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * What a destination's page says about it, worded by the same rules as its Survey row.
 *
 * @property info  What describes the destination (broker, address, workspace), for the detail line.
 * @property nowMs When it was worked out, for "next try at" and "since" times.
 */
data class DestinationPageStatus(
    val destination: Destination,
    val status: DestinationStatus,
    val info: DestinationInfo,
    val nowMs: Long,
)

/**
 * Works out a destination page's status from the same inputs the Survey screen uses: the running
 * survey, what waits on the phone, and the destination's settings, so the page and the Survey row
 * can never disagree. Ticks every second so a Connecting first attempt turns into Can't connect,
 * and "next try at" times stay right, without anything else changing.
 */
class DestinationStatusFlow(
    context: Context,
    private val destination: Destination,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val infoReader = DestinationInfoReader(appContext)

    /**
     * @param runtime The running survey's state while the service is bound, or null.
     * @return The status, or null when the page has nothing to say (a stream outside a survey).
     */
    fun of(runtime: Flow<SurveyRuntimeState?>): Flow<DestinationPageStatus?> {
        val queues = if (destination in UPLOADS_LATER) DeliveryQueueReader(appContext).queues() else flowOf(DeliveryQueues())
        val info = pollTicks(INFO_POLL_MS).map { infoReader.read(null) }
        return combine(runtime, queues, info, pollTicks(TICK_MS)) { state, waiting, destinationInfo, _ ->
            val nowMs = clock()
            // The times it shows are to the minute, so the page only redraws when something changes
            RecordingUiMapper.pageStatus(state, destination, nowMs, waiting, destinationInfo)
                ?.let { DestinationPageStatus(destination, it, destinationInfo, nowMs - nowMs % MINUTE_MS) }
        }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val INFO_POLL_MS = 5_000L
        const val MINUTE_MS = 60_000L

        /** The destinations whose status depends on what waits on the phone. */
        val UPLOADS_LATER = setOf(Destination.NS_ANALYTICS, Destination.COMMUNITY)
    }
}
