package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.util.copyWithSystemConfirmation

/**
 * Lets a long press copy [value] on a details page, the way Cellular and GNSS values copy: the
 * clipboard gets [label] as the clip's name, Android 13+ confirms it (a toast before that), and
 * TalkBack offers it as "Copy". Adds nothing when there is nothing to copy.
 */
@Composable
fun Modifier.longPressCopy(label: String, value: String?): Modifier {
    if (value.isNullOrBlank()) return this
    val context = LocalContext.current
    val action = stringResource(R.string.cellular_copy_action)
    val confirmation = stringResource(R.string.value_copied_to_clipboard)
    return copyOnLongPress(action, value) { copyWithSystemConfirmation(context, label, value, confirmation) }
}
