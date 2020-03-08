package com.craxiom.networksurvey.services;

import android.content.Context;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.craxiom.networksurvey.util.Config;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Saves GNSS data into a GeoPackage. This class is responsible for creating new GeoPackage
 * databases as necessary, providing data to the database, and closing databases when they are no
 * longer needed.
 */
public class GnssGeoPackageRecorder extends HandlerThread
{
    private static final String LOG_TAG = GnssGeoPackageRecorder.class.getSimpleName();
    private static final String FILENAME_PREFIX = "GNSS-MONKEY";

    @SuppressWarnings("SpellCheckingInspection")
    private static final SimpleDateFormat FILENAME_FRIENDLY_TIME_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
    private static final String JOURNAL_FILE_SUFFIX = "-journal";

    private final Context context;
    private Handler handler;
    private final AtomicBoolean isDataRecorded = new AtomicBoolean(false);
    private final AtomicBoolean geoPackageOpen = new AtomicBoolean(false);
    private GnssGeoPackageDatabase gpkgDatabase;
    private String gpkgFilePath;
    private String gpkgFolderPath;

    GnssGeoPackageRecorder(Context context)
    {
        super("GeoPkgRcdr");
        this.context = context;
        Config config = Config.getInstance(context);
        gpkgFolderPath = config.getSaveDirectoryPath();

        if (gpkgFolderPath == null)
        {
            Log.e(LOG_TAG, "Unable to find GPSMonkey storage location; using Download directory");
            gpkgFolderPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        }
    }

    @Override
    protected void onLooperPrepared()
    {
        handler = new Handler();
    }

    void onGnssMeasurementsReceived(final GnssMeasurementsEvent event)
    {
        provideDataToDatabase((e) -> gpkgDatabase.writeGnssMeasurements(e), event);
    }

    /**
     * Performs all the common logic for providing data to the database.
     *
     * @param consumer The method to call with the data
     * @param data     The data
     * @param <T>      The type of the data
     */
    private <T> void provideDataToDatabase(Consumer<T> consumer, T data)
    {
        if (geoPackageOpen.get() && (data != null))
        {
            handler.post(() -> {
                consumer.accept(data);
                isDataRecorded.getAndSet(true);
            });
        }
    }

    void onLocationChanged(final Location location)
    {
        provideDataToDatabase((l) -> gpkgDatabase.writeLocation(l), location);
    }

    void onSatelliteStatusChanged(final GnssStatus status)
    {
        provideDataToDatabase((s) -> gpkgDatabase.writeSatelliteStatus(s), status);
    }

    /**
     * Shuts down the recorder and provides the file path for the database
     */
    void shutdown()
    {
        Log.d(LOG_TAG, "GeoPackageRecorder.shutdown()");
        closeGeoPackageDatabase();

        getLooper().quit();

        removeTempFiles();
    }

    /**
     * Deletes any temporary journal files in the save directory.
     */
    private void removeTempFiles()
    {
        try
        {
            if (gpkgFolderPath != null)
            {
                File dir = new File(gpkgFolderPath);
                if (dir.exists())
                {
                    // If the file is not a directory, null will be returned
                    File[] files = dir.listFiles();

                    if ((files != null) && (files.length > 0))
                    {
                        for (File file : files)
                        {
                            String fileName = file.getName();
                            if (fileName.endsWith(JOURNAL_FILE_SUFFIX))
                            {
                                //noinspection ResultOfMethodCallIgnored
                                file.delete();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore)
        {
        }
    }

    /**
     * @return True if the recorder has an open database that is available to store data.
     */
    private boolean isActive()
    {
        return geoPackageOpen.get();
    }

    /**
     * @return The file path for the currently opened GeoPackage or the last GeoPackage if a file is
     * not open (use {@link #isActive()} to differentiate).
     */
    public String getFilePath()
    {
        return gpkgFilePath;
    }

    /**
     * Opens a new GeoPackage database.
     *
     * @return True if the GeoPackage database was successfully opened, false if something went wrong.
     */
    synchronized boolean openGeoPackageDatabase()
    {
        if (geoPackageOpen.get())
        {
            Log.w(LOG_TAG, "Open called while another gpkg that was already open: " + gpkgFilePath +
                    "; closing it now.");
            closeGeoPackageDatabase();
        }

        try
        {
            gpkgFilePath = gpkgFolderPath + "/" + createGpkgFilename();

            gpkgDatabase = new GnssGeoPackageDatabase(context);
            gpkgDatabase.start(gpkgFilePath);
            Log.d(LOG_TAG, "Opened file: " + gpkgFilePath);
            geoPackageOpen.set(true);
        } catch (SQLException e)
        {
            Log.e(LOG_TAG, "Error setting up GeoPackage", e);
            return false;
        }

        return geoPackageOpen.get();
    }

    /**
     * Creates a new GeoPackage filename using the filename prefix and the current time.
     *
     * @return The filename
     */
    private String createGpkgFilename()
    {
        String timestamp = FILENAME_FRIENDLY_TIME_FORMAT.format(System.currentTimeMillis());
        return FILENAME_PREFIX + "-" + timestamp + ".gpkg";
    }

    /**
     * Closes the current GeoPackage database.
     */
    private synchronized void closeGeoPackageDatabase()
    {
        geoPackageOpen.set(false);

        if (gpkgDatabase == null) return;

        gpkgDatabase.shutdown();
        Log.d(LOG_TAG, "Closed file: " + gpkgFilePath);

        // Delete the journal file for the database that was just shutdown.
        try
        {
            if (gpkgFilePath != null)
            {
                File journalFile = new File(gpkgFilePath + JOURNAL_FILE_SUFFIX);

                if (journalFile.exists())
                {
                    //noinspection ResultOfMethodCallIgnored
                    journalFile.delete();
                }
            }
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Could not clear out the GNSS journal file", e);
        }
    }
}
