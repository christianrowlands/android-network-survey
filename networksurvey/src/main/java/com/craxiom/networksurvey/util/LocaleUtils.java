/**
 * Based on https://github.com/YarikSOffice/LanguageTest/blob/master/app/src/main/java/com/yariksoffice/languagetest/Utility.java
 * Licensed under MIT - https://github.com/YarikSOffice/LanguageTest/blob/master/LICENSE
 */
package com.craxiom.networksurvey.util;

import android.os.Build;

/**
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
public class LocaleUtils
{
    public static boolean isAtLeastVersion(int version)
    {
        return Build.VERSION.SDK_INT >= version;
    }
}
