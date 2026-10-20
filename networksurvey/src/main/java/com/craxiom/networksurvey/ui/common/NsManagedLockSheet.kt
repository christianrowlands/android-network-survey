package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The one sheet every item the organization sets opens, on the Survey screen and the destination
 * pages: it says the organization chose this. "See all" opens the Organization settings page, and
 * "Use my own settings" leads to the override, which always asks for confirmation first. Pass no
 * [onUseOwnSettings] for something the override cannot change (an upload the organization blocks).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NsManagedLockSheet(
    onSeeAll: () -> Unit,
    onUseOwnSettings: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NsTheme.spacing.xxl)
                .padding(bottom = NsTheme.spacing.xxl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            Text(text = stringResource(R.string.survey_set_by_organization), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.survey_lock_body), style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NsTextAction(stringResource(R.string.survey_org_see_all), {
                    onDismiss()
                    onSeeAll()
                })
                if (onUseOwnSettings != null) NsTextAction(stringResource(R.string.survey_lock_use_own), onUseOwnSettings)
                Row(modifier = Modifier.weight(1f)) {}
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget)) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}
