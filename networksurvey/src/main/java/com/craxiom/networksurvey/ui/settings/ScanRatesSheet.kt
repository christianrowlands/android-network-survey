package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.common.NsFormField
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The five scan intervals in one form: a number field each, with what the interval does under it.
 * An interval the organization set shows as a locked row instead of a field. Save stores every
 * changed interval (Bluetooth is raised to its minimum by the view model).
 */
@Composable
fun ScanRatesSheet(
    rates: Map<ScanRate, SettingValue<Int>>,
    onSave: (Map<ScanRate, Int>) -> Unit,
    onLocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val drafts = remember { mutableStateMapOf<ScanRate, String>().apply { rates.forEach { (rate, value) -> put(rate, value.value.toString()) } } }
    val editable = ScanRate.entries.filterNot { rates.getValue(it).locked }
    // Only what was changed is checked, so an old stored value nobody touched never blocks Save
    val changed = editable.filter { drafts[it]?.trim() != rates.getValue(it).value.toString() }
    val parsed = changed.associateWith { drafts[it]?.trim()?.toIntOrNull() }
    val valid = parsed.values.all { it != null && it > 0 }
    val invalid = stringResource(R.string.settings_whole_number)

    NsFormBottomSheet(
        title = stringResource(R.string.settings_scan_rates),
        confirmText = stringResource(R.string.save),
        confirmEnabled = valid,
        onConfirm = {
            onSave(parsed.mapNotNull { (rate, value) -> value?.let { rate to it } }.toMap())
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
            ScanRate.entries.forEach { rate ->
                val setting = rates.getValue(rate)
                if (setting.locked) {
                    NsRow(
                        title = stringResource(rate.title),
                        subtitle = stringResource(R.string.settings_seconds, setting.value),
                        locked = true,
                        onClick = onLocked,
                    )
                } else {
                    val draft = drafts[rate].orEmpty()
                    NsFormField(
                        label = rate.title,
                        value = draft,
                        error = invalid.takeIf { rate in parsed && draft.isNotBlank() && parsed[rate].let { it == null || it <= 0 } },
                        supporting = stringResource(rate.description),
                        keyboardType = KeyboardType.Number,
                        imeAction = if (rate == editable.lastOrNull()) ImeAction.Done else ImeAction.Next,
                        digitsOnly = true,
                        onChange = { drafts[rate] = it },
                    )
                }
            }
        }
    }
}
