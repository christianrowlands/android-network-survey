package com.craxiom.networksurvey.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import timber.log.Timber

/**
 * Helper class for managing battery optimization prompts and settings.
 *
 * This class helps ensure users disable battery optimization to prevent
 * Android from stopping the app during surveys, which could cause data loss.
 */
class BatteryOptimizationHelper(private val context: Context) {

    companion object {
        // Preference keys
        private const val PREF_DONT_SHOW_BATTERY_PROMPT = "dont_show_battery_optimization_prompt"
        private const val PREF_LAST_PROMPT_TIME = "last_battery_optimization_prompt_time"

        // Minimum time between prompts (24 hours)
        private const val MIN_PROMPT_INTERVAL_MS = 24L * 60L * 60L * 1000L

        // Manufacturer constants
        private const val MANUFACTURER_XIAOMI = "xiaomi"
        private const val MANUFACTURER_SAMSUNG = "samsung"
        private const val MANUFACTURER_OPPO = "oppo"
        private const val MANUFACTURER_VIVO = "vivo"
        private const val MANUFACTURER_HUAWEI = "huawei"
        private const val MANUFACTURER_ONEPLUS = "oneplus"
        private const val MANUFACTURER_REALME = "realme"
    }

    private val prefs = context.getSharedPreferences("battery_optimization", Context.MODE_PRIVATE)

    /**
     * Checks if battery optimization should be prompted.
     * Returns true if optimization is enabled and user hasn't opted out.
     */
    fun shouldPromptForBatteryOptimization(): Boolean {
        // Check if user has opted out
        if (prefs.getBoolean(PREF_DONT_SHOW_BATTERY_PROMPT, false)) {
            Timber.d("User has opted out of battery optimization prompts")
            return false
        }

        // Check if already optimized
        if (isBatteryOptimizationDisabled()) {
            Timber.d("Battery optimization already disabled")
            return false
        }

        // Check minimum time since last prompt (to avoid nagging)
        val lastPromptTime = prefs.getLong(PREF_LAST_PROMPT_TIME, 0)
        val timeSinceLastPrompt = System.currentTimeMillis() - lastPromptTime
        if (lastPromptTime > 0 && timeSinceLastPrompt < MIN_PROMPT_INTERVAL_MS) {
            Timber.d("Too soon since last battery optimization prompt")
            return false
        }

        return true
    }

    /**
     * Records that the battery optimization prompt was shown.
     */
    fun recordPromptShown() {
        prefs.edit { putLong(PREF_LAST_PROMPT_TIME, System.currentTimeMillis()) }
    }

    /**
     * Sets the user preference to not show battery optimization prompts again.
     */
    fun setDontShowAgain(dontShow: Boolean) {
        prefs.edit { putBoolean(PREF_DONT_SHOW_BATTERY_PROMPT, dontShow) }
    }

    /**
     * Checks if battery optimization is disabled for this app.
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService<PowerManager>()
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Opens battery optimization settings for the app.
     * Uses different approaches based on build variant and Android version.
     */
    fun openBatteryOptimizationSettings() {
        Timber.d("openBatteryOptimizationSettings called - Flavor: ${BuildConfig.FLAVOR}, SDK: ${Build.VERSION.SDK_INT}")

        try {
            // Attempt to request directly
            Timber.d("Attempting to use ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")

            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Verify the intent can be resolved
            val directResolveInfo = context.packageManager.resolveActivity(
                directIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            if (directResolveInfo != null) {
                Timber.d("Starting battery optimization request intent - resolved to: ${directResolveInfo.activityInfo?.name}")
                context.startActivity(directIntent)
                return
            } else {
                Timber.w("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent cannot be resolved")
            }

            // As a backup option, open app settings so the user can manually disable optimization
            Timber.d("Opening app details settings as fallback")
            val manualIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Verify the intent can be resolved
            val manualResolveInfo =
                context.packageManager.resolveActivity(
                    manualIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            if (manualResolveInfo != null) {
                context.startActivity(manualIntent)
            } else {
                Timber.e("ACTION_APPLICATION_DETAILS_SETTINGS intent cannot be resolved")
                // Try the general battery optimization settings
                openGeneralBatterySettings()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open battery optimization settings")
            // Fallback to general battery settings
            openGeneralBatterySettings()
        }
    }

    private fun openGeneralBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resolveInfo =
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) {
                Timber.d("Opening general battery optimization settings")
                context.startActivity(intent)
            } else {
                Timber.e("No battery optimization settings intent could be resolved")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open general battery settings")
        }
    }

    /**
     * The extra steps this phone's maker needs to keep the survey running, as a string resource, or
     * null when the maker needs none.
     */
    @StringRes
    fun getManufacturerInstructions(): Int? {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains(MANUFACTURER_XIAOMI) -> R.string.battery_steps_xiaomi
            manufacturer.contains(MANUFACTURER_SAMSUNG) -> R.string.battery_steps_samsung
            manufacturer.contains(MANUFACTURER_HUAWEI) -> R.string.battery_steps_huawei
            manufacturer.contains(MANUFACTURER_OPPO) || manufacturer.contains(MANUFACTURER_REALME) -> R.string.battery_steps_oppo
            manufacturer.contains(MANUFACTURER_VIVO) -> R.string.battery_steps_vivo
            manufacturer.contains(MANUFACTURER_ONEPLUS) -> R.string.battery_steps_oneplus
            else -> null
        }
    }
}