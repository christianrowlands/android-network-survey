package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.UUID;

public class Config
{
    private static final String TAG = "GPSMonkey.Config";
    public static final String DEFAULT_SAVE_DIRECTORY_NAME = "GPSMonkey";
    public static final String PREFS_SAVE_DIR = "savedir";
    public static final String PREFS_AUTO_SHARE = "autoshare";
    public static final String PREFS_PROCESS_EW = "processew";
    public static final String PREFS_UUID = "callsign";
    public static final String PREFS_GPS_ONLY = "gpsonly";
    public static final String PREFS_BROADCAST = "broadcast";
    public static final String PREFS_SQAN = "sqan";
    public static final String PREFS_SEND_TO_SOS = "sendtosos";

    private static boolean gpsOnly = false;
    private static Config instance = null;

    private String saveDirectoryPath = null;
    private boolean processEwOnboard;
    private SharedPreferences prefs;
    private String uuid = null;
    private String remoteIP = null;

    public static boolean isGpsOnly()
    {
        return gpsOnly;
    }

    public static boolean isSosBroadcastEnabled(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SEND_TO_SOS, true);
    }

    public static boolean isIpcBroadcastEnabled(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_BROADCAST, true);
    }

    public static boolean isSqAnBroadcastEnabled(Context context)
    {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFS_SQAN, true);
    }

    public static synchronized Config getInstance(Context context)
    {
        if (instance == null)
        {
            instance = new Config(context);
        }
        return instance;
    }

    private Config(Context context)
    {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        processEwOnboard = prefs.getBoolean(PREFS_PROCESS_EW, false);
        if (prefs.getString(PREFS_UUID, null) == null)
        {
            SharedPreferences.Editor editor = prefs.edit();
            editor.apply();
        }
    }

    public void loadPrefs()
    {
        gpsOnly = prefs.getBoolean(PREFS_GPS_ONLY, false);
    }

    public void setProcessEwOnboard(boolean processEwOnboard)
    {
        this.processEwOnboard = processEwOnboard;
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(PREFS_PROCESS_EW, processEwOnboard);
        edit.apply();
    }

    public boolean isAutoShareEnabled()
    {
        return prefs.getBoolean(PREFS_AUTO_SHARE, true);
    }

    public void setGpsOnly(boolean gpsOnly)
    {
        Config.gpsOnly = gpsOnly;
        prefs.edit().putBoolean(PREFS_GPS_ONLY, gpsOnly).apply();
    }

    public boolean processEWOnboard()
    {
        return processEwOnboard;
    }

    public String getUuid()
    {
        if (uuid == null)
        {
            uuid = prefs.getString(PREFS_UUID, null);
            if (uuid == null)
            {
                uuid = UUID.randomUUID().toString();
                prefs.edit().putString(PREFS_UUID, uuid).apply();
            }
        }
        return uuid;
    }

    /**
     * @return The path of the directory where any GeoPackage files should be saved.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public String getSaveDirectoryPath()
    {
        // If the save directory path has already been determined, use the existing one.
        // Note: if/when we add support for the user to set the directory path in the preferences,
        // it may be possible for the user to change the desired save directory, but if we have
        // already saved files to a different one, we don't want to change the active save directory
        // until after a restart so files won't be in two places (including any temp files we would
        // need to delete).
        if (saveDirectoryPath == null)
        {
            File saveDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DEFAULT_SAVE_DIRECTORY_NAME);

            saveDirectory.mkdirs();
            saveDirectory.setReadable(true);
            saveDirectoryPath = saveDirectory.getAbsolutePath();

            Log.i(TAG, "Save directory: " + saveDirectoryPath);
        }

        return saveDirectoryPath;
    }

    public void setSavedDir(String savedDir)
    {
        SharedPreferences.Editor edit = prefs.edit();
        if (savedDir == null)
        {
            edit.remove(PREFS_SAVE_DIR);
        } else
        {
            edit.putString(PREFS_SAVE_DIR, savedDir);
        }
        edit.apply();
    }
}
