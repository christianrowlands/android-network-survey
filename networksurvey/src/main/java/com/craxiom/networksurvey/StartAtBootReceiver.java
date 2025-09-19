package com.craxiom.networksurvey;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * Starts the Network Survey Service when Android is booted if the {@link NetworkSurveyConstants#PROPERTY_MQTT_START_ON_BOOT}
 * property has been set by the MDM managed configuration.  The {@link NetworkSurveyService} will then try to start the
 * connection to an MQTT Broker using the MDM provided connection information.
 *
 * @since 0.1.1
 */
public class StartAtBootReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Timber.d("Received the boot completed broadcast message in the Network Survey broadcast receiver");

        if (PreferenceUtils.getMqttStartOnBootPreference(context))
        {
            Timber.i("Auto starting the Network Survey Service based on the user or MDM MQTT auto start preference");

            startNetworkSurveyService(context);
        } else
        {
            // Finally, check to see if we want to auto-start any of the log files.
            if (PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CELLULAR_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_WIFI_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_BLUETOOTH_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_GNSS_LOGGING, false, context)
                    || PreferenceUtils.getAutoStartPreference(NetworkSurveyConstants.PROPERTY_AUTO_START_CDR_LOGGING, false, context))
            {
                Timber.i("Auto starting the Network Survey Service based on one of the auto start logging preferences");

                startNetworkSurveyService(context);
            }
        }
    }

    /**
     * Kick off the {@link NetworkSurveyService} using an intent. The {@link NetworkSurveyConstants#EXTRA_STARTED_AT_BOOT}
     * flag is used so that the {@link NetworkSurveyService} can handle being started at boot instead of when the app is
     * opened by the user.
     * <p>
     * Starting from Android 12+, we need to be more careful about starting foreground services from
     * background contexts. This method now includes permission checks and fallback to WorkManager.
     */
    private void startNetworkSurveyService(Context context)
    {
        final Context applicationContext = context.getApplicationContext();

        // Check if we have the necessary permissions for starting a location foreground service
        boolean hasBackgroundLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        boolean hasFineLocationPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean hasCoarseLocationPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        // We need at least one location permission and background location for boot start
        boolean hasRequiredPermissions = (hasFineLocationPermission || hasCoarseLocationPermission)
                && hasBackgroundLocationPermission;

        if (!hasRequiredPermissions)
        {
            Timber.w("Missing required permissions for starting service at boot. " +
                            "Background location: %b, Fine location: %b, Coarse location: %b",
                    hasBackgroundLocationPermission, hasFineLocationPermission, hasCoarseLocationPermission);
            // Don't try to start the service without proper permissions
            return;
        }

        // Try to start the service directly first
        try
        {
            final Intent startServiceIntent = new Intent(applicationContext, NetworkSurveyService.class);
            startServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, true);

            context.startForegroundService(startServiceIntent);
            Timber.i("Successfully started NetworkSurveyService using startForegroundService");
        } catch (Exception e)
        {
            Timber.e(e, "Failed to start NetworkSurveyService directly, falling back to WorkManager");

            // Fallback to WorkManager with a delay
            scheduleServiceStartWithWorkManager(context);
        }
    }

    /**
     * Schedules the NetworkSurveyService to start using WorkManager with a small delay.
     * This is used as a fallback when direct service start fails due to Android 12+ restrictions.
     * <p>
     * This delay approach was added because of crashes that were observed on some devices with the error message:
     * "ForegroundServiceStartNotAllowedException - startForegroundService() not allowed due to mAllowStartForeground false"
     * It is not entirely clear why this happens because most devices seem to handle the direct start just fine, so
     * update this code if new information comes to light.
     */
    private void scheduleServiceStartWithWorkManager(Context context)
    {
        try
        {
            Constraints constraints = new Constraints.Builder()
                    .build();

            // Create work request with a 5-second delay to let system stabilize
            OneTimeWorkRequest startServiceWork = new OneTimeWorkRequest.Builder(ServiceStartWorker.class)
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .addTag("network_survey_boot_start")
                    .build();

            // Enqueue the work
            WorkManager.getInstance(context).enqueue(startServiceWork);

            Timber.i("Scheduled NetworkSurveyService start via WorkManager with 5-second delay");
        } catch (Exception e)
        {
            Timber.e(e, "Failed to schedule service start with WorkManager");
        }
    }

    /**
     * Worker class to start the NetworkSurveyService from WorkManager.
     */
    public static class ServiceStartWorker extends Worker
    {
        public ServiceStartWorker(Context context, WorkerParameters params)
        {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork()
        {
            Context context = getApplicationContext();

            try
            {
                Intent startServiceIntent = new Intent(context, NetworkSurveyService.class);
                startServiceIntent.putExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, true);

                context.startForegroundService(startServiceIntent);

                Timber.i("ServiceStartWorker: Successfully started NetworkSurveyService at boot");
                return Result.success();
            } catch (Exception e)
            {
                Timber.e(e, "ServiceStartWorker: Failed to start NetworkSurveyService at boot");

                // Retry up to 3 times with exponential backoff (handled by WorkManager)
                if (getRunAttemptCount() < 3)
                {
                    return Result.retry();
                } else
                {
                    return Result.failure();
                }
            }
        }
    }
}
