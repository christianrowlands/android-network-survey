package com.craxiom.networksurvey.ui.acknowledgments

import androidx.lifecycle.ViewModel
import com.craxiom.networksurvey.data.AcknowledgmentsRepository
import com.craxiom.networksurvey.model.LibraryAcknowledgment
import com.craxiom.networksurvey.model.LibraryCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Acknowledgments screen.
 */
class AcknowledgmentsViewModel : ViewModel() {

    private val repository = AcknowledgmentsRepository()

    private val _uiState = MutableStateFlow(AcknowledgmentsUiState())
    val uiState: StateFlow<AcknowledgmentsUiState> = _uiState.asStateFlow()

    init {
        loadAcknowledgments()
    }

    private fun loadAcknowledgments() {
        val allLibraries = repository.getLibraryAcknowledgments()
        val groupedLibraries = allLibraries.groupBy { it.category }

        _uiState.value = AcknowledgmentsUiState(
            specialAcknowledgments = groupedLibraries[LibraryCategory.SPECIAL_ACKNOWLEDGMENTS]
                ?: emptyList(),
            coreLibraries = groupedLibraries[LibraryCategory.CORE_LIBRARIES] ?: emptyList(),
            uiLibraries = groupedLibraries[LibraryCategory.UI_LIBRARIES] ?: emptyList(),
            utilityLibraries = groupedLibraries[LibraryCategory.UTILITY_LIBRARIES] ?: emptyList(),
            isLoading = false
        )
    }
}

/**
 * UI state for the Acknowledgments screen.
 */
data class AcknowledgmentsUiState(
    val specialAcknowledgments: List<LibraryAcknowledgment> = emptyList(),
    val coreLibraries: List<LibraryAcknowledgment> = emptyList(),
    val uiLibraries: List<LibraryAcknowledgment> = emptyList(),
    val utilityLibraries: List<LibraryAcknowledgment> = emptyList(),
    val isLoading: Boolean = true
)