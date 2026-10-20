/*
 * Copyright (C) 2013 Sean J. Barbeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.craxiom.networksurvey;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.preference.PreferenceManager;

import com.craxiom.networksurvey.lang.LocaleManager;
import com.craxiom.networksurvey.notification.NotificationChannels;
import com.craxiom.networksurvey.survey.mdm.AndroidRestrictionsSource;
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfile;
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileMigration;
import com.craxiom.networksurvey.mqtt.profile.MqttBrokerProfileStore;
import com.craxiom.networksurvey.survey.setup.SurveySetupMigration;
import com.craxiom.networksurvey.survey.setup.SurveySetupStore;
import com.craxiom.networksurvey.gpstest.util.SatSortMigration;
import com.craxiom.networksurvey.ui.map.MapPreferenceCleanup;
import com.craxiom.networksurvey.ui.settings.SettingsDefaults;
import com.craxiom.networksurvey.ui.theme.ThemeMode;
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage;
import com.craxiom.networksurvey.util.CredentialSecureStorage;

import dagger.hilt.android.HiltAndroidApp;
import timber.log.Timber;

/**
 * Holds application-wide state.
 * <p>
 * This class was pulled from the GPS Test app
 *
 * @author Sean J. Barbeau
 */
@HiltAndroidApp
public class Application extends android.app.Application
{
    private static Application mApp;

    private SharedPreferences mPrefs;

    public static Application get()
    {
        return mApp;
    }

    public static SharedPreferences getPrefs()
    {
        return get().mPrefs;
    }

    private static LocaleManager mLocaleManager;

    @Override
    public void onCreate()
    {
        super.onCreate();

        // If this is a debug apk, then we enable logging. If it is a release apk we don't want to output any logs.
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        mApp = this;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        CredentialSecureStorage.INSTANCE.migrateFromPlainTextIfNeeded(this);

        // Turn the single MQTT broker of earlier versions into a broker profile, once. It waits for
        // the credentials migration above, and runs before the survey setup migration reads it.
        MqttBrokerProfileMigration.migrateIfNeeded(this);

        // Turn the old per-destination survey preferences into a survey setup, once. This runs
        // before any activity, receiver or service reads the setup (including a start at boot).
        final MqttBrokerProfileStore brokerProfileStore = MqttBrokerProfileStore.create(this);
        new SurveySetupMigration(mPrefs, new SurveySetupStore(mPrefs), new AndroidRestrictionsSource(this),
                () -> NsAnalyticsSecureStorage.INSTANCE.isRegistered(this),
                () -> brokerProfileStore.profiles().stream()
                        .anyMatch(profile -> profile.getOrigin() == MqttBrokerProfile.Origin.USER),
                () -> SettingsDefaults.wereApplied(this),
                getString(R.string.survey_setup_ns_analytics_name)).migrateIfNeeded();

        // The survey map is gone with the Survey Monitor; drop its copy of the map settings
        MapPreferenceCleanup.removeRetiredKeys(mPrefs);

        // The GNSS sort was stored as its label, which a translation or rewording silently reset
        SatSortMigration.migrate(mPrefs);

        // Apply the Theme setting before any activity inflates, so Views and Compose agree
        ThemeMode.applyFromPreferences(this);
    }

    @Override
    public void onTerminate()
    {
        super.onTerminate();
        mApp = null;
    }

    @Override
    protected void attachBaseContext(Context base)
    {
        mLocaleManager = new LocaleManager(base);
        super.attachBaseContext(mLocaleManager.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mLocaleManager.setLocale(this);
    }

    /**
     * Registers every notification channel used by this app. Kept as the single entry point so the
     * service and the activity both register the same set; see {@link NotificationChannels}.
     *
     * @param context The context that is used to create the notification channels.
     * @since 1.5.0
     */
    public static void createNotificationChannel(Context context)
    {
        NotificationChannels.INSTANCE.createAll(context);
    }
}
