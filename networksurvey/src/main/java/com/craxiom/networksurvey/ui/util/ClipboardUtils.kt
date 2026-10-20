package com.craxiom.networksurvey.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

/**
 * Copies [text] to the system clipboard with [clipLabel] and shows a short toast with
 * [toastMessage]. This is the shared copy-to-clipboard entry point for the Compose screens, so
 * prefer it over calling [ClipboardManager] directly.
 */
fun copyTextToClipboard(
    context: Context,
    clipLabel: String,
    text: String,
    toastMessage: String,
) {
    setClip(context, clipLabel, text)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

/**
 * Copies [text] like [copyTextToClipboard], but toasts only before Android 13: from Android 13 the
 * system shows its own confirmation, and a toast on top of it is a second message.
 */
fun copyWithSystemConfirmation(context: Context, clipLabel: String, text: String, toastMessage: String) {
    setClip(context, clipLabel, text)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

private fun setClip(context: Context, clipLabel: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
}
