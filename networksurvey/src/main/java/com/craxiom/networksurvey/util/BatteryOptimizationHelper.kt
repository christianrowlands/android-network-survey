package com.craxiom.networksurvey.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.craxiom.networksurvey.BuildConfig
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
        private const val PREF_HAS_SHOWN_FIRST_PROMPT =
            "has_shown_battery_optimization_first_prompt"

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
     * Checks if this is the first time we should show the prompt.
     * Used to show immediately on first app launch.
     */
    fun shouldShowFirstTimePrompt(): Boolean {
        if (prefs.getBoolean(PREF_HAS_SHOWN_FIRST_PROMPT, false)) {
            return false
        }

        // Mark as shown for next time
        prefs.edit { putBoolean(PREF_HAS_SHOWN_FIRST_PROMPT, true) }

        return shouldPromptForBatteryOptimization()
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
     * Gets manufacturer-specific instructions for disabling battery optimization.
     */
    fun getManufacturerInstructions(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains(MANUFACTURER_XIAOMI) -> {
                "Xiaomi devices require additional steps:\n" +
                        "1. Go to Settings → Battery & performance\n" +
                        "2. Choose 'App battery saver'\n" +
                        "3. Select Network Survey\n" +
                        "4. Choose 'No restrictions'\n" +
                        "5. Also enable 'Autostart' in Security app"
            }

            manufacturer.contains(MANUFACTURER_SAMSUNG) -> {
                "Samsung devices:\n" +
                        "1. Go to Settings → Device care → Battery\n" +
                        "2. Tap 'Background usage limits'\n" +
                        "3. Add Network Survey to 'Never sleeping apps'\n" +
                        "4. Disable 'Put unused apps to sleep'"
            }

            manufacturer.contains(MANUFACTURER_HUAWEI) -> {
                "Huawei devices:\n" +
                        "1. Go to Settings → Battery → App launch\n" +
                        "2. Find Network Survey\n" +
                        "3. Disable 'Manage automatically'\n" +
                        "4. Enable all three options (Auto-launch, Secondary launch, Run in background)"
            }

            manufacturer.contains(MANUFACTURER_OPPO) || manufacturer.contains(MANUFACTURER_REALME) -> {
                "Oppo/Realme devices:\n" +
                        "1. Go to Settings → Battery → App battery management\n" +
                        "2. Find Network Survey\n" +
                        "3. Turn off background restriction\n" +
                        "4. Also check Security Center → Privacy Permissions → Startup manager"
            }

            manufacturer.contains(MANUFACTURER_VIVO) -> {
                "Vivo devices:\n" +
                        "1. Go to Settings → Battery → Background power consumption\n" +
                        "2. Find Network Survey\n" +
                        "3. Allow background activity\n" +
                        "4. Also check i-Manager → App manager → Autostart"
            }

            manufacturer.contains(MANUFACTURER_ONEPLUS) -> {
                "OnePlus devices:\n" +
                        "1. Go to Settings → Battery → Battery optimization\n" +
                        "2. Find Network Survey\n" +
                        "3. Choose 'Don't optimize'\n" +
                        "4. Also disable 'Adaptive Battery' and 'Sleep standby optimization'"
            }

            else -> null
        }
    }
}