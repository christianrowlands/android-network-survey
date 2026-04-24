package com.craxiom.networksurvey.ui.wifi

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Info button used by the Wi-Fi Spectrum screen to explain what "scan rate" means. Retained
 * after the Wi-Fi Details redesign moved scan rate into a dedicated card.
 */
@Composable
fun ScanRateInfoButton() {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            Icons.Default.Info,
            contentDescription = "About Wi-Fi Scan Rate",
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Wi-Fi Scan Rate Info") },
            text = {
                Text(
                    "The rate at which Wi-Fi networks will be scanned for in " +
                        "seconds. Smaller values will decrease battery life but larger values will " +
                        "cause the Signal Strength Graph to be out of date. If you want values " +
                        "closer to real time then set the scan rate to 5 seconds or less."
                )
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
