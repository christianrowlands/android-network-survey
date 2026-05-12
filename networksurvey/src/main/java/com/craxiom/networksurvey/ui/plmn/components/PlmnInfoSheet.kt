package com.craxiom.networksurvey.ui.plmn.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * Educational bottom sheet for the PLMN lookup and details screens.
 *
 * Explains the terms that show up in result rows and on the details page: PLMN, MCC, MNC,
 * the Brand vs Operator distinction, and TADIG. Uses a local [PlmnHelpSection] (instead of
 * the shared [com.craxiom.networksurvey.ui.common.HelpSection]) because that helper emits
 * its title + spacer + content as sibling composables; combined with the outer Column's
 * `Arrangement.spacedBy(...)`, the title ends up further from its own content than from the
 * previous section. [PlmnHelpSection] wraps each title+content pair in its own Column so the
 * outer arrangement only governs inter-section spacing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlmnInfoSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.plmn_info_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            PlmnHelpSection(title = stringResource(R.string.plmn_info_section_plmn)) {
                HelpItem(
                    label = stringResource(R.string.plmn_info_plmn_label),
                    description = stringResource(R.string.plmn_info_plmn_desc),
                )
            }

            PlmnHelpSection(title = stringResource(R.string.plmn_info_section_codes)) {
                HelpItem(
                    label = stringResource(R.string.plmn_info_mcc_label),
                    description = stringResource(R.string.plmn_info_mcc_desc),
                )
                HelpItem(
                    label = stringResource(R.string.plmn_info_mnc_label),
                    description = stringResource(R.string.plmn_info_mnc_desc),
                )
            }

            PlmnHelpSection(title = stringResource(R.string.plmn_info_section_brand_operator)) {
                HelpItem(
                    label = stringResource(R.string.plmn_info_brand_label),
                    description = stringResource(R.string.plmn_info_brand_desc),
                )
                HelpItem(
                    label = stringResource(R.string.plmn_info_operator_label),
                    description = stringResource(R.string.plmn_info_operator_desc),
                )
            }

            PlmnHelpSection(title = stringResource(R.string.plmn_info_section_tadig)) {
                Text(
                    text = stringResource(R.string.plmn_info_tadig_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PlmnHelpSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = WifiTokens.SsidAccent,
        )
        content()
    }
}
