package com.craxiom.networksurvey.ui.main.more

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.mdm.SurveyPolicyState
import com.craxiom.networksurvey.ui.survey.DestinationInfoReader
import com.craxiom.networksurvey.ui.survey.model.DestinationInfo
import com.craxiom.networksurvey.util.NsUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.craxiom.networksurvey.scout.ScoutManager
import com.craxiom.networksurvey.scout.ScoutPolicy
import com.craxiom.networksurvey.scout.model.ScoutState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The More list.
 *
 * @property groups The rows, grouped.
 * @property info What each destination is set up as, which the destination rows describe.
 */
data class MoreUiState(val groups: List<MoreGroup>, val info: DestinationInfo = DestinationInfo())

/**
 * Builds the More list from the destination settings and the organization policy. Both are read off
 * the main thread (the reader opens encrypted storage), when the tab shows and again on each resume,
 * so a page that changed a destination is reflected on the way back.
 */
class MoreViewModel(application: Application) : AndroidViewModel(application) {

    // Overridable for tests, which run the reads on the test thread.
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var reader: DestinationInfoReader? = null

    /** What [refresh] read: the destinations, the policy, the version and whether NS Scout is allowed. */
    private data class Read(val info: DestinationInfo?, val policy: SurveyPolicyState, val version: String, val scoutAllowed: Boolean)

    // NS Scout stays out until the first read says it is allowed, so a phone that bans it never shows the row
    private val read = MutableStateFlow(Read(null, SurveyPolicyState.UNMANAGED, "", scoutAllowed = false))

    /** The NS Scout's status for the Hardware row, live while the tab shows. */
    private val scoutState: Flow<ScoutState> = ScoutManager.getInstance(application).state

    val state: StateFlow<MoreUiState> = combine(read, scoutState) { read, scout ->
        MoreUiState(MoreItems.build(read.info, read.policy, read.version, scout.takeIf { read.scoutAllowed }), read.info ?: DestinationInfo())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MoreUiState(MoreItems.build(null, SurveyPolicyState.UNMANAGED, "", scout = null)))

    /** Re-reads the destinations and the policy. */
    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            val application = getApplication<Application>()
            val info = (reader ?: DestinationInfoReader(application).also { reader = it }).read(null)
            val policy = SurveyTypeResolver.currentPolicy(application)
            read.value = Read(info, policy, NsUtils.getAppVersionName(application), ScoutPolicy.isAllowed(application))
        }
    }
}
