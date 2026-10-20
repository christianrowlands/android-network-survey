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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val mutableState = MutableStateFlow(MoreUiState(MoreItems.build(null, SurveyPolicyState.UNMANAGED, "")))
    val state: StateFlow<MoreUiState> = mutableState.asStateFlow()

    /** Re-reads the destinations and the policy. */
    fun refresh() {
        viewModelScope.launch(ioDispatcher) {
            val application = getApplication<Application>()
            val info = (reader ?: DestinationInfoReader(application).also { reader = it }).read(null)
            val policy = SurveyTypeResolver.currentPolicy(application)
            mutableState.value = MoreUiState(MoreItems.build(info, policy, NsUtils.getAppVersionName(application)), info)
        }
    }
}
