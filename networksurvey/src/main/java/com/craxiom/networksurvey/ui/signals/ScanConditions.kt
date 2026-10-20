package com.craxiom.networksurvey.ui.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a Signals view needs before it can scan (a permission, location on, a radio on), read again
 * on demand and whenever one of the given system broadcasts arrives. The view model owns one and
 * calls [close] from onCleared.
 *
 * @param actions The broadcasts that can change the state (radio state, location providers).
 * @param read Reads the current state; it must be cheap, since it runs on the main thread.
 * @param onBroadcast Runs on the main thread after a broadcast changed [state], so the view model
 * can register or unregister to match.
 */
class ScanConditions<S>(
    private val context: Context,
    actions: List<String>,
    private val read: () -> S,
    private val onBroadcast: () -> Unit,
) {
    private val mutableState = MutableStateFlow(read())

    val state: StateFlow<S> = mutableState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
            onBroadcast()
        }
    }

    init {
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Reads the state again (on resume and after a permission answer) and returns it. */
    fun refresh(): S {
        val current = read()
        mutableState.value = current
        return current
    }

    fun close() = context.unregisterReceiver(receiver)
}
