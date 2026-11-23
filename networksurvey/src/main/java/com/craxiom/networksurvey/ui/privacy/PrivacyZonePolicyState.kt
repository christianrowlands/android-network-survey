package com.craxiom.networksurvey.ui.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.craxiom.networksurvey.privacy.PrivacyZonePolicy

/*
 * The organization's privacy zone policy, observed by the screens that show or change zones.
 */

/**
 * @return Whether the organization allows privacy zones ([PrivacyZonePolicy]), kept current while
 * the caller is composed: read again on every resume and whenever the managed configuration
 * changes, so a screen left open never acts on a policy it read once.
 */
@Composable
fun rememberPrivacyZonesAllowed(): Boolean {
    val context = LocalContext.current.applicationContext
    var allowed by remember { mutableStateOf(PrivacyZonePolicy.isAllowed(context)) }
    LifecycleResumeEffect(context) {
        allowed = PrivacyZonePolicy.isAllowed(context)
        onPauseOrDispose {}
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                allowed = PrivacyZonePolicy.isAllowed(context)
            }
        }
        // Only a receiver registered at runtime hears this broadcast; the system sends it, so the
        // receiver need not be exported
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return allowed
}
