/*
 * Based on https://github.com/YarikSOffice/LanguageTest/blob/master/app/src/main/java/com/yariksoffice/languagetest/LocaleManager.java
 * Licensed under MIT - https://github.com/YarikSOffice/LanguageTest/blob/master/LICENSE
 */

package com.craxiom.networksurvey.lang;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;

import com.craxiom.networksurvey.R;

import java.util.Locale;

/**
 * Dynamically changes the app locale
 */
public class LocaleManager
{
    private final SharedPreferences prefs;

    public LocaleManager(Context context)
    {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public Context setLocale(Context c)
    {
        if (!prefs.contains(c.getString(R.string.pref_key_language)))
        {
            // User hasn't set the language manually, so use the default context and locale
            return c;
        }
        return updateResources(c, getLanguage(c));
    }

    String getLanguage(Context c)
    {
        return prefs.getString(c.getString(R.string.pref_key_language),
                c.getResources().getStringArray(R.array.language_values)[0]); // Default is English
    }

    @SuppressWarnings("deprecation")
    private Context updateResources(Context context, String language)
    {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (isAtLeastVersion(JELLY_BEAN_MR1))
        {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else
        {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    private static boolean isAtLeastVersion(int version)
    {
        return Build.VERSION.SDK_INT >= version;
    }
}
