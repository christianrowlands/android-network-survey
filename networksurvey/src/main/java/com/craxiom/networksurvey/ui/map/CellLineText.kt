package com.craxiom.networksurvey.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R

/*
 * The words the Map's serving-cell card uses for a cell's band and identity, shared with the New
 * towers page so a tower reads the same in both places.
 */

/** Joins the parts of a cell line ("Mint · LTE Band 66 · -79 dBm"). */
internal const val CELL_PART_SEPARATOR = " · "

/** "Band 66" for an LTE band, the NR designators ("n78") as they are, or null when neither is known. */
@Composable
internal fun bandText(lteBand: Int?, nrBands: String?): String? =
    lteBand?.let { stringResource(R.string.map_card_band_lte, it) } ?: nrBands

/**
 * "eNB 100000 · Sector 3" for an LTE cell, "NCI 123" or "CID 123" for the others, or null when
 * neither is known. See [ServingCellSummary.enbSectorOf] and [ServingCellSummary.identityOf].
 */
@Composable
internal fun cellIdentityText(enbSector: Pair<Int, Int>?, identity: CellMetric?): String? =
    enbSector?.let { (enb, sector) -> stringResource(R.string.survey_point_enb_sector, enb, sector) }
        ?: identity?.let { "${it.label} ${it.value}" }
