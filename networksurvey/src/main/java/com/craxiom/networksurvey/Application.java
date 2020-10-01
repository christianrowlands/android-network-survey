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
import android.preference.PreferenceManager;

import com.craxiom.networksurvey.lang.LocaleManager;

import timber.log.Timber;

/**
 * Holds application-wide state.
 * <p>
 * TODO: This class was pulled from the GPS Test app and needs to be removed once all the usages of this class are updated/removed
 *
 * @author Sean J. Barbeau
 */
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
}
