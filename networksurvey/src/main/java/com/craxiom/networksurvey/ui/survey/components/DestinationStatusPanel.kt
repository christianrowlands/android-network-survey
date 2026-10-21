package com.craxiom.networksurvey.ui.survey.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.ui.common.NsStatusPanel
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.survey.DestinationPageStatus
import com.craxiom.networksurvey.ui.survey.DestinationStatusFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The status at the top of a destination's page: the same word, color and detail line as the
 * destination's row on the Survey screen ("Will retry", "Last upload failed: OpenCelliD: Server
 * error · next try at 12:43").
 *
 * @param action A button for what the page offers about this status, under the text.
 */
@Composable
fun DestinationStatusPanel(page: DestinationPageStatus, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val (word, color) = destinationStatusLabel(page.status)
    NsStatusPanel(
        icon = page.destination.iconRes,
        title = word,
        subtitle = destinationStatusDetail(page.destination, page.status, page.info, page.nowMs),
        modifier = modifier,
        iconTint = color,
        titleColor = color,
        action = action,
    )
}

/**
 * @return The status [destination]'s page shows, kept up to date while the page is in front of the
 * user; null when there is nothing to say (a stream outside a survey). Binds the survey service for
 * as long as the page is resumed, the way the other destination pages do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberDestinationStatus(destination: Destination): DestinationPageStatus? {
    val context = LocalContext.current
    val service = remember { MutableStateFlow<NetworkSurveyService?>(null) }
    ServiceConnectionHandler { service.value = it }
    val status = remember(destination) {
        DestinationStatusFlow(context, destination).of(service.flatMapLatest { it?.surveyStateFlow ?: flowOf(null) })
    }
    val page by status.collectAsStateWithLifecycle(initialValue = null)
    return page
}
