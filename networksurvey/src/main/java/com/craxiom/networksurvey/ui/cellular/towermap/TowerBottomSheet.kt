package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.data.PlmnColorOverrideManager
import com.craxiom.networksurvey.data.api.Tower
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.util.MeasurementFormatter
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.PlmnColorMapper
import java.util.Locale

/**
 * Represents the state of the tower bottom sheet.
 */
sealed class TowerSheetState {
    data object Hidden : TowerSheetState()
    data class TowerList(val towers: List<Tower>) : TowerSheetState()
    data class TowerDetail(
        val tower: Tower,
        val returnToList: List<Tower>? = null
    ) : TowerSheetState()
}

/**
 * A bottom sheet that displays tower information. Shows a list view for multi-tower locations
 * and a detail view for individual towers, with in-sheet navigation between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowerBottomSheet(
    state: TowerSheetState,
    onStateChange: (TowerSheetState) -> Unit,
    onColorOverrideChanged: () -> Unit = {}
) {
    if (state is TowerSheetState.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onStateChange(TowerSheetState.Hidden) },
        sheetState = sheetState
    ) {
        when (state) {
            is TowerSheetState.TowerList -> {
                TowerListView(
                    towers = state.towers,
                    onTowerSelected = { tower ->
                        onStateChange(TowerSheetState.TowerDetail(tower, state.towers))
                    },
                    onColorOverrideChanged = onColorOverrideChanged
                )
            }

            is TowerSheetState.TowerDetail -> {
                TowerDetailView(
                    tower = state.tower,
                    showBackButton = state.returnToList != null,
                    onBack = {
                        state.returnToList?.let {
                            onStateChange(TowerSheetState.TowerList(it))
                        }
                    },
                    onColorOverrideChanged = onColorOverrideChanged
                )
            }

            is TowerSheetState.Hidden -> { /* handled above */
            }
        }
    }
}

/**
 * Displays a list of towers at a shared location, allowing the user to select one for details.
 */
@Composable
private fun TowerListView(
    towers: List<Tower>,
    onTowerSelected: (Tower) -> Unit,
    onColorOverrideChanged: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_cell_tower),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.tower_list_title, towers.size, towers.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Tower list
        LazyColumn {
            itemsIndexed(towers) { index, tower ->
                TowerListRow(
                    tower = tower,
                    onClick = { onTowerSelected(tower) },
                    onColorOverrideChanged = onColorOverrideChanged
                )
                if (index < towers.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * A single row in the tower list showing key identifiers and a navigation arrow.
 */
@Composable
private fun TowerListRow(
    tower: Tower,
    onClick: () -> Unit,
    onColorOverrideChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    var providerColor by remember { mutableStateOf(PlmnColorMapper.getColor(tower.mcc, tower.mnc)) }
    var showColorPicker by remember { mutableStateOf(false) }
    var colorVersion by remember { mutableIntStateOf(0) }

    // Re-read color when version changes (after override is applied)
    providerColor = remember(colorVersion) { PlmnColorMapper.getColor(tower.mcc, tower.mnc) }

    if (showColorPicker) {
        val overrideManager = remember { PlmnColorOverrideManager(context) }
        ProviderColorPickerDialog(
            mcc = tower.mcc,
            mnc = tower.mnc,
            currentPaletteIndex = PlmnColorMapper.getColorIndex(tower.mcc, tower.mnc),
            hasOverride = overrideManager.getOverride(tower.mcc, tower.mnc) != null,
            onColorSelected = { index ->
                overrideManager.setOverride(tower.mcc, tower.mnc, index)
                colorVersion++
                onColorOverrideChanged()
                showColorPicker = false
            },
            onResetToDefault = {
                overrideManager.removeOverride(tower.mcc, tower.mnc)
                colorVersion++
                onColorOverrideChanged()
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Provider color bar (tappable to open color picker)
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 40.dp)
                    .clickable { showColorPicker = true }
                    .padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(providerColor, RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            ProtocolBadge(tower.radio)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(
                        R.string.label_value_pair,
                        stringResource(R.string.mcc_mnc_label),
                        stringResource(R.string.mcc_mnc_value, tower.mcc.toString(), tower.mnc.toString()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.label_value_pair, stringResource(R.string.calculator_cell_id), tower.cid.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lteIds = getLteIds(tower)
                if (lteIds != null) {
                    Text(
                        text = stringResource(R.string.survey_point_enb_sector, lteIds.first, lteIds.second),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = stringResource(R.string.tower_list_view_details),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Displays detailed information for a single tower within the bottom sheet.
 */
@Composable
private fun TowerDetailView(
    tower: Tower,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onColorOverrideChanged: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with optional back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.tower_detail_back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_cell_tower),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.tower_detail_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Network Identity Section
        NetworkIdentitySection(tower, onColorOverrideChanged)

        // Location Section
        LocationSection(tower)

        // Comments Section (only for towers with comments)
        if (!tower.comments.isNullOrBlank()) {
            CommentsSection(tower.comments)
        }

        // Metadata Section
        MetadataSection(tower)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun NetworkIdentitySection(tower: Tower, onColorOverrideChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var colorVersion by remember { mutableIntStateOf(0) }
    val providerColor = remember(colorVersion) { PlmnColorMapper.getColor(tower.mcc, tower.mnc) }
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        val overrideManager = remember { PlmnColorOverrideManager(context) }
        ProviderColorPickerDialog(
            mcc = tower.mcc,
            mnc = tower.mnc,
            currentPaletteIndex = PlmnColorMapper.getColorIndex(tower.mcc, tower.mnc),
            hasOverride = overrideManager.getOverride(tower.mcc, tower.mnc) != null,
            onColorSelected = { index ->
                overrideManager.setOverride(tower.mcc, tower.mnc, index)
                colorVersion++
                onColorOverrideChanged()
                showColorPicker = false
            },
            onResetToDefault = {
                overrideManager.removeOverride(tower.mcc, tower.mnc)
                colorVersion++
                onColorOverrideChanged()
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(stringResource(R.string.tower_detail_identity))
            ProtocolBadge(tower.radio)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Color dot (tappable to open color picker)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { showColorPicker = true }
                                    .padding(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(providerColor, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            CompactInfoItem(
                                stringResource(R.string.mcc_mnc_label),
                                stringResource(R.string.mcc_mnc_value, tower.mcc.toString(), tower.mnc.toString()),
                            )
                        }
                        CompactInfoItem(stringResource(R.string.survey_point_area), tower.area.toString())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.calculator_cell_id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(NsTheme.spacing.xs))
                        Text(
                            text = tower.cid.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val lteIds = getLteIds(tower)
                    if (lteIds != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CompactInfoItem(stringResource(R.string.enb_id_label), lteIds.first.toString())
                            CompactInfoItem(stringResource(R.string.sector_id_label), lteIds.second.toString())
                        }
                    }

                    if (tower.unit > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.pcs_pci_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(NsTheme.spacing.xs))
                            Text(
                                text = tower.unit.toString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (tower.source != TowerSource.BTSearch.apiName) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CompactInfoItem(
                                stringResource(R.string.tower_detail_range),
                                MeasurementFormatter.formatRange(context, tower.range)
                            )
                            CompactInfoItem(stringResource(R.string.tower_detail_samples), tower.samples.toString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSection(tower: Tower) {
    Column {
        SectionHeader(stringResource(R.string.tower_detail_location))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            SelectionContainer {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_location_pin),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${String.format(Locale.ROOT, "%.6f", tower.lat)}, ${
                            String.format(Locale.ROOT, "%.6f", tower.lon)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Displays the comments/uwagi field for towers that have one.
 */
@Composable
private fun CommentsSection(comments: String) {
    Column {
        SectionHeader(stringResource(R.string.tower_detail_comments))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            SelectionContainer {
                Text(
                    text = comments,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun MetadataSection(tower: Tower) {
    Column {
        SectionHeader(stringResource(R.string.tower_detail_more))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.tower_detail_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SourceBadge(tower.source)
                    }

                    if (tower.source != TowerSource.BTSearch.apiName) {
                        TimestampRow(stringResource(R.string.tower_detail_first_seen), tower.createdAt)
                        TimestampRow(stringResource(R.string.tower_detail_updated), tower.updatedAt)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.tower_detail_changeable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(if (tower.changeable == 1) R.string.tower_detail_yes else R.string.tower_detail_no),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (tower.changeable == 1)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        TimestampRow(stringResource(R.string.tower_detail_updated), tower.updatedAt)
                    }
                }
            }
        }
    }
}

/**
 * Derives the eNB ID and Sector ID from an LTE Cell ID, or returns null
 * if the tower is not LTE or the Cell ID is not a valid LTE value.
 */
private fun getLteIds(tower: Tower): Pair<Int, Int>? {
    if (!tower.radio.equals(NetworkSurveyConstants.LTE, ignoreCase = true)) return null
    if (tower.cid !in 1..268_435_455L) return null
    val cid = tower.cid.toInt()
    return Pair(
        CalculationUtils.getEnodebIdFromCellId(cid),
        CalculationUtils.getSectorIdFromCellId(cid)
    )
}
