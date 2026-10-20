package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSelection
import com.craxiom.networksurvey.ui.cellular.towermap.CameraPositionState
import com.craxiom.networksurvey.ui.cellular.towermap.TowerSheetState
import com.craxiom.networksurvey.ui.watchlist.SheetDetent

/**
 * The map's UI state that both the map and the chrome around it use: the camera, which sheet or
 * dialog is open, and the survey point the user tapped. The Map tab owns it so its action row can
 * open the map's sheets and hide while a survey point sheet is up.
 */
@Stable
class TowerMapUiState(val camera: CameraPositionState) {
    var showLayers by mutableStateOf(false)
    var showFilters by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var showSurveyedOptions by mutableStateOf(false)
    var showSurveyPointsAbout by mutableStateOf(false)
    var towerSheet by mutableStateOf<TowerSheetState>(TowerSheetState.Hidden)

    /** The survey point (or coarse cell) the user tapped, shown in a sheet; null when none is. */
    var surveyedSelection by mutableStateOf<SurveyedPointSelection?>(null)

    /** The survey point sheet's drag state; it outlives the sheet, so a new tap starts it small. */
    val surveyedSheetState = AnchoredDraggableState(initialValue = SheetDetent.Peek)
}

/** Remembers a [TowerMapUiState] around [camera]. */
@Composable
fun rememberTowerMapUiState(camera: CameraPositionState): TowerMapUiState = remember(camera) { TowerMapUiState(camera) }
