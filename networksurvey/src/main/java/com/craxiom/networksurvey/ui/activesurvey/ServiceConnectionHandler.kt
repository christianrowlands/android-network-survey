package com.craxiom.networksurvey.ui.activesurvey

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.craxiom.networksurvey.services.NetworkSurveyService
import timber.log.Timber

/**
 * Binds the Survey Monitor screen to the [NetworkSurveyService] for as long as the screen is
 * actually in front of the user.
 *
 * The binding follows the lifecycle rather than composition: inside a NavHost destination
 * `LocalLifecycleOwner` is the back stack entry, whose lifecycle tracks both navigating away and
 * the app being backgrounded, while the composable itself stays composed through a backgrounding.
 * That distinction matters because [SurveyMonitorViewModel] registers as a cellular survey record
 * listener, and a registered listener makes `NetworkSurveyService.isBeingUsed()` report true. If
 * the registration outlived the visible screen, the service would never shut itself down and its
 * ongoing notification would stay up with no survey running.
 */
@Composable
fun ServiceConnectionHandler(
    viewModel: SurveyMonitorViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as NetworkSurveyService.SurveyServiceBinder
                viewModel.setNetworkSurveyService(serviceBinder.service as NetworkSurveyService)
                Timber.d("SurveyMonitor connected to NetworkSurveyService")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                viewModel.setNetworkSurveyService(null)
                Timber.d("SurveyMonitor disconnected from NetworkSurveyService")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val applicationContext = context.applicationContext

        // Unregistering is what triggers the service's own stopSelf check, so it has to happen
        // before the unbind that lets the service actually be destroyed.
        val release = {
            viewModel.setNetworkSurveyService(null)
            try {
                applicationContext.unbindService(serviceConnection)
                Timber.i("NetworkSurveyService unbound in the Survey Monitor")
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not unbind the service because it is not bound.")
            }
        }

        // Tracks that bindService was called, not that it succeeded: a failed bind still leaves the
        // connection registered, so it still has to be unbound.
        var bindAttempted = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        applicationContext.startService(
                            Intent(applicationContext, NetworkSurveyService::class.java)
                        )
                        bindAttempted = true
                        val bound = applicationContext.bindService(
                            Intent(applicationContext, NetworkSurveyService::class.java),
                            serviceConnection,
                            Context.BIND_ABOVE_CLIENT
                        )
                        Timber.i("NetworkSurveyService bound in the Survey Monitor: %s", bound)
                    } catch (e: IllegalStateException) {
                        // Android refuses startService from the background; the screen can be
                        // resumed and then immediately backgrounded, so swallow it like the
                        // activity does instead of crashing.
                        Timber.w(e, "Could not start the Network Survey service.")
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    if (bindAttempted) {
                        bindAttempted = false
                        release()
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (bindAttempted) {
                bindAttempted = false
                release()
            }
        }
    }
}
