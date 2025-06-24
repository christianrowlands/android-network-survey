package com.craxiom.networksurvey.ui.activesurvey

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.craxiom.networksurvey.services.NetworkSurveyService
import timber.log.Timber

/**
 * Composable that handles service connection for the SurveyMonitorViewModel
 */
@Composable
fun ServiceConnectionHandler(
    viewModel: SurveyMonitorViewModel
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<NetworkSurveyService?>(null) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as NetworkSurveyService.SurveyServiceBinder
                service = serviceBinder.service as NetworkSurveyService
                viewModel.setNetworkSurveyService(service)
                Timber.d("SurveyMonitor connected to NetworkSurveyService")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                viewModel.setNetworkSurveyService(null)
                service = null
                Timber.d("SurveyMonitor disconnected from NetworkSurveyService")
            }
        }
    }

    DisposableEffect(context) {
        // Start the service
        val applicationContext = context.applicationContext
        val startServiceIntent = Intent(applicationContext, NetworkSurveyService::class.java)
        applicationContext.startService(startServiceIntent)

        // Bind to the service
        val serviceIntent = Intent(applicationContext, NetworkSurveyService::class.java)
        val bound = applicationContext.bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_ABOVE_CLIENT
        )
        Timber.i("NetworkSurveyService bound in ActiveSurveyScreen: $bound")

        onDispose {
            viewModel.setNetworkSurveyService(null)
            try {
                applicationContext.unbindService(serviceConnection)
                Timber.i("NetworkSurveyService unbound in ActiveSurveyScreen")
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not unbind the service because it is not bound.")
            }
        }
    }
}