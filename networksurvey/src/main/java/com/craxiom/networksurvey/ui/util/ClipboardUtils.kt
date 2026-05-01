package com.craxiom.networksurvey.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Copies [text] to the system clipboard with [clipLabel] and shows a short toast with
 * [toastMessage]. Shared by the Wi-Fi list/details rows and the OUI Lookup screen.
 */
fun copyTextToClipboard(
    context: Context,
    clipLabel: String,
    text: String,
    toastMessage: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
