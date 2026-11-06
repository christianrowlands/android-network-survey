package com.craxiom.networksurvey.scout

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a Scout link up for [lingerMs] after its last owner let go, so moving from one Scout screen
 * to the next (the page releases in `onPause` before the next screen asks) doesn't drop the link and
 * build it again. Only the link lingers: the board is told to Stop as soon as no capture is wanted.
 *
 * Used on the Scout thread only.
 */
internal class ScoutLinkLinger(private val scope: CoroutineScope, private val lingerMs: Long = LINGER_MS) {
    private var job: Job? = null

    /** True while a let-go is pending. */
    val isPending: Boolean get() = job?.isActive == true

    /** The link is wanted again: forget the pending let-go. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Runs [letGo] after the linger unless [cancel] comes first; a pending let-go keeps its deadline. */
    fun letGoLater(letGo: () -> Unit) {
        if (isPending) return
        job = scope.launch {
            delay(lingerMs)
            job = null
            letGo()
        }
    }

    companion object {
        /** How long a link outlives its last owner. */
        const val LINGER_MS = 3_000L
    }
}
