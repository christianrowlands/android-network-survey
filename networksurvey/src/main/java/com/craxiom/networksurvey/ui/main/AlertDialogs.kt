package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun GnssFailureDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    val checkedState = remember { mutableStateOf(false) }

    // TODO Validate that this dialog works
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(checkedState.value) }
            ) { Text("OK") }
        },
        text = {
            Column {
                Text("GNSS Failure")
                Checkbox(
                    checked = checkedState.value,
                    onCheckedChange = { checkedState.value = it }
                )
            }
        }
    )
}
