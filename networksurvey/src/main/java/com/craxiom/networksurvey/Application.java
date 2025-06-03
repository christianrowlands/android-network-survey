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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.lang.LocaleManager;

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

    public static LocaleManager getLocaleManager()
    {
        return mLocaleManager;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        // If this is a debug apk, then we enable logging. If it is a release apk we don't want to output any logs.
        if (BuildConfig.DEBUG) Timber.plant(new Timber.DebugTree());

        mApp = this;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
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
     * Creates the notification Channel that is used by this Android app.
     *
     * @param context The context that is used to create the notification channel.
     * @since 1.5.0
     */
    public static void createNotificationChannel(Context context)
    {
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null)
        {
            final NotificationChannel channel = new NotificationChannel(NetworkSurveyConstants.NOTIFICATION_CHANNEL_ID,
                    context.getText(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        } else
        {
            Timber.wtf("The Notification Manager could not be retrieved to add the Network Survey notification channel");
        }
    }
}
