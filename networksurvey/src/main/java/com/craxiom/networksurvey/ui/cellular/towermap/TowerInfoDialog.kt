package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.cellular.Tower
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TowerInfoDialog(
    tower: Tower,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Tower Information", fontWeight = FontWeight.Bold)
        },
        text = {
            Box {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Column {
                            // Protocol
                            Text(text = "Protocol: ${tower.radio}", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Location
                            Text(text = "Location: ${String.format("%.6f", tower.lat)}, ${String.format("%.6f", tower.lon)}")
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Cell identifiers
                            Text(text = "MCC: ${tower.mcc}")
                            Text(text = "MNC: ${tower.mnc}")
                            Text(text = "Area: ${tower.area}")
                            Text(text = "Cell ID: ${tower.cid}")
                            Text(text = "Unit: ${tower.unit}")
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Signal information (only for non-BTSearch sources)
                            if (tower.source != "BTSearch") {
                                Text(text = "Range: ${tower.range} meters")
                                Text(text = "Samples: ${tower.samples}")
                                Text(text = "Average Signal: ${tower.averageSignal} dBm")
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(text = "Changeable: ${if (tower.changeable == 1) "Yes" else "No"}")
                                Text(text = "Created: ${formatDateTime(tower.createdAt)}")
                                Text(text = "Updated: ${formatDateTime(tower.updatedAt)}")
                            } else {
                                Text(text = "Updated: ${formatDate(tower.updatedAt)}")
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Source: ${tower.source}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun formatDateTime(timestamp: Long): String {
    val date = Date(timestamp * 1000) // Convert to milliseconds
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(date)
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp * 1000) // Convert to milliseconds
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(date)
}