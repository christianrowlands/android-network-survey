package com.craxiom.networksurvey.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.craxiom.networksurvey.R

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
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

/**
 * Copies [missionId] to the clipboard using the shared Mission ID label and toast strings. The
 * dashboard Mission ID card and the active survey monitor both expose this same copy action, so
 * the string choices live here to keep the two in sync.
 */
fun copyMissionIdToClipboard(context: Context, missionId: String) {
    copyTextToClipboard(
        context,
        context.getString(R.string.mission_id_card_title),
        missionId,
        context.getString(R.string.mission_id_copied),
    )
}
