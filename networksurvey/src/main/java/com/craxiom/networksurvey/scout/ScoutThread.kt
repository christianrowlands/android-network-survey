package com.craxiom.networksurvey.scout

import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * The dedicated thread that owns the Scout link, decoding and state, started the first time
 * anything runs on it. The manager only runs work once a Scout is paired or someone asks for one,
 * so a phone that never pairs a Scout never starts this thread.
 */
internal class ScoutThread(private val name: String = ScoutConstants.THREAD_NAME) {

    private val lazyHandler = lazy { Handler(HandlerThread(name).apply { start() }.looper) }

    /** The thread's handler; reading it starts the thread. */
    val handler: Handler get() = lazyHandler.value

    /** Runs coroutines on the thread, starting it on the first dispatch. */
    val dispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        private val target by lazy { handler.asCoroutineDispatcher() }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = target.isDispatchNeeded(context)

        override fun dispatch(context: CoroutineContext, block: Runnable) = target.dispatch(context, block)
    }

    /** True once the thread was started. */
    val isStarted: Boolean get() = lazyHandler.isInitialized()
}
