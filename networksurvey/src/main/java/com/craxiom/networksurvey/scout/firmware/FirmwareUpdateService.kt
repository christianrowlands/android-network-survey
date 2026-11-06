package com.craxiom.networksurvey.scout.firmware

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.scout.ScoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps a firmware install alive when the app leaves the foreground: a `dataSync` foreground
 * service with a progress notification and a partial wake lock, for exactly as long as
 * [ScoutFirmwareUpdater] installs. The install itself runs in the Scout manager; this service only
 * follows its state and stops when the install ends. Started only from the firmware update screen.
 */
class FirmwareUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, FirmwareConstants.WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire(FirmwareConstants.WAKE_LOCK_TIMEOUT_MS)
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = runState(applicationContext)
        val phase = (state.value as? FirmwareUpdateRun.Installing)?.phase ?: FirmwareInstallPhase.Connecting
        val notification = FirmwareUpdateNotification.build(this, FirmwareUpdateNotification.content(resources, phase))
        try {
            ServiceCompat.startForeground(this, FirmwareConstants.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: IllegalStateException) {
            // The system refused (the dataSync time limit, for one). The install goes on while the screen is open
            Timber.w(e, "The firmware update could not run as a foreground service")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        watcher?.cancel()
        watcher = scope.launch {
            followInstall(state)
            ServiceCompat.stopForeground(this@FirmwareUpdateService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /** Updates the notification until the install ends. */
    private suspend fun followInstall(state: StateFlow<FirmwareUpdateRun>) {
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        state.takeWhile { it is FirmwareUpdateRun.Installing }
            .map { FirmwareUpdateNotification.content(resources, (it as FirmwareUpdateRun.Installing).phase) }
            .distinctUntilChanged()
            .collect { content ->
                notifications.notify(FirmwareConstants.NOTIFICATION_ID, FirmwareUpdateNotification.build(this, content))
            }
    }

    /** The system ended this service's dataSync time; the install goes on while the screen is open. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        /** Where the service reads the install's state. Overridable for tests. */
        internal var runState: (Context) -> StateFlow<FirmwareUpdateRun> = { ScoutManager.getInstance(it).firmwareUpdater.state }

        /** Starts the service for an install that was just started. Call only from the foreground UI. */
        @JvmStatic
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FirmwareUpdateService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the install still runs while the screen is open
                Timber.w(e, "The firmware update service could not start")
            }
        }
    }
}
