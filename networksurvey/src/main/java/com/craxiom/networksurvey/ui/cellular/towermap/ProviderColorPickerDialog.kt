package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.PlmnColorMapper

/**
 * Reusable color picker dialog that displays the 16-color palette in a 4x4 grid.
 * Used by both the tower bottom sheet (tap color dot) and the settings override screen.
 *
 * @param mcc The Mobile Country Code of the provider.
 * @param mnc The Mobile Network Code of the provider.
 * @param currentPaletteIndex The currently active palette index (override or default).
 * @param hasOverride Whether the current color is from a user override (vs. the hash default).
 * @param onColorSelected Called with the selected palette index when the user picks a color.
 * @param onResetToDefault Called when the user taps "Reset to Default".
 * @param onDismiss Called when the dialog is dismissed.
 */
@Composable
fun ProviderColorPickerDialog(
    mcc: String,
    mnc: String,
    currentPaletteIndex: Int,
    hasOverride: Boolean,
    onColorSelected: (Int) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val defaultIndex =
        PlmnColorMapper.getDefaultColorIndex(mcc.toIntOrNull() ?: 0, mnc.toIntOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.mcc_mnc_format, mcc, mnc))
                Text(
                    text = stringResource(
                        R.string.provider_color_default_label,
                        PlmnColorMapper.PALETTE_NAMES[defaultIndex]
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                // 4x4 color grid
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0 until 4) {
                            val index = row * 4 + col
                            ColorCell(
                                index = index,
                                isSelected = index == currentPaletteIndex,
                                isDefault = index == defaultIndex,
                                onClick = { onColorSelected(index) }
                            )
                        }
                    }
                    if (row < 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            if (hasOverride) {
                TextButton(
                    onClick = onResetToDefault,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.provider_color_reset_to_default))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * A single color cell in the picker grid showing the palette color, its name, a checkmark
 * if currently selected, and a "(Default)" label for the hash-assigned color.
 */
@Composable
private fun RowScope.ColorCell(
    index: Int,
    isSelected: Boolean,
    isDefault: Boolean,
    onClick: () -> Unit
) {
    val color = PlmnColorMapper.getColorByIndex(index)
    val name = PlmnColorMapper.PALETTE_NAMES[index]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.selected),
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isDefault) "$name*" else name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
