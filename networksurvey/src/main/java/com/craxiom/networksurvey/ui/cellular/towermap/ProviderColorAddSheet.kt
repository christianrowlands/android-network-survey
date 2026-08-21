package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsFormBottomSheet
import com.craxiom.networksurvey.ui.common.NsSectionLabel
import com.craxiom.networksurvey.util.PlmnColorMapper

/**
 * Bottom sheet for adding a provider color override: MCC and MNC fields plus a palette grid. The
 * confirm button stays disabled until both fields are filled and a color is chosen; range
 * validation happens on confirm and keeps the sheet open with field errors when out of range.
 */
@Composable
fun ProviderColorAddSheet(
    onAdd: (mcc: String, mnc: String, paletteIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mccText by remember { mutableStateOf("") }
    var mncText by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var mccError by remember { mutableStateOf<String?>(null) }
    var mncError by remember { mutableStateOf<String?>(null) }

    val mccRangeError = stringResource(R.string.provider_color_mcc_range)
    val mncRangeError = stringResource(R.string.provider_color_mnc_range)

    NsFormBottomSheet(
        title = stringResource(R.string.provider_color_add_title),
        confirmText = stringResource(R.string.provider_color_add_confirm),
        confirmEnabled = mccText.isNotBlank() && mncText.isNotBlank() && selectedIndex >= 0,
        onConfirm = {
            val mccInt = mccText.toIntOrNull()
            val mncInt = mncText.toIntOrNull()
            var hasError = false

            if (mccInt == null || mccInt !in 100..999) {
                mccError = mccRangeError
                hasError = true
            }
            if (mncInt == null || mncInt !in 0..999) {
                mncError = mncRangeError
                hasError = true
            }

            if (!hasError) {
                onAdd(mccText, mncText, selectedIndex)
                onDismiss()
            }
        },
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = mccText,
                onValueChange = {
                    mccText = it.filter { c -> c.isDigit() }.take(3)
                    mccError = null
                },
                label = { Text(stringResource(R.string.provider_color_mcc_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = mccError != null,
                supportingText = mccError?.let { { Text(it) } },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = mncText,
                onValueChange = {
                    mncText = it.filter { c -> c.isDigit() }.take(3)
                    mncError = null
                },
                label = { Text(stringResource(R.string.provider_color_mnc_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = mncError != null,
                supportingText = mncError?.let { { Text(it) } },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(16.dp))

        NsSectionLabel(text = stringResource(R.string.provider_color_sheet_color_label))
        Spacer(Modifier.height(8.dp))
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0 until 4) {
                    val index = row * 4 + col
                    ProviderColorGridCell(
                        index = index,
                        isSelected = index == selectedIndex,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    }
}

/**
 * A single selectable palette cell in the color grid: the color dot with its name below, with a
 * highlight ring and bold label when selected.
 */
@Composable
private fun RowScope.ProviderColorGridCell(
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = PlmnColorMapper.getColorByIndex(index)
    val name = PlmnColorMapper.PALETTE_NAMES[index]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (isSelected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(4.dp)
                .background(color, CircleShape)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
