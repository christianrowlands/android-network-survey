package com.craxiom.networksurvey.helpers;

import android.os.Environment;

import java.io.File;
import java.io.FilenameFilter;

public class AndroidFiles
{

    public static File getDownloadDirectoryFiles()
    {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static File getNetworkSurveyDataFiles()
    {
        File downloadDirectory = getDownloadDirectoryFiles();
        return new File(downloadDirectory.getAbsolutePath() + "/" + "NetworkSurveyData");
    }

    public static File getLatestSurveyFile(String date, String type)
    {

        long lastModifiedTime = Long.MIN_VALUE;

        File[] files = getNetworkSurveyDataFiles().listFiles(filenameFilter(date, type));

        File chosenFile = null;

        if (files != null)
        {
            for (File file : files)
            {
                if (file.lastModified() > lastModifiedTime)
                {
                    chosenFile = file;
                    lastModifiedTime = file.lastModified();
                }
            }
        }

        return chosenFile;
    }

    private static FilenameFilter filenameFilter(String date, String type)
    {
        String fileRegex = "craxiom-" + type + "-" + date + ".*.gpkg";
        return (dir, name) -> name.matches(fileRegex);
    }
}
