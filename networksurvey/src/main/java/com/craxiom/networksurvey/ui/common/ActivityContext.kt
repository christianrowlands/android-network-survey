package com.craxiom.networksurvey.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity

/**
 * Walks the context wrapper chain to find the hosting [ComponentActivity]. Needed to scope view
 * models to the activity from inside the composition.
 */
tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
