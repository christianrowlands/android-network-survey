package com.craxiom.networksurvey.logging;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Abstract base class for logging survey records to a CSV file.
 * <p>
 * This class initializes the CSV file, and closes it out when it is done.
 *
 * @since 1.11
 */
public abstract class CsvRecordLogger
{
    private static final int RECORD_COUNT_INTERVAL = 5000;

    /**
     * A lock to synchronize the writing of single records and the creation of a new CSV file
     * during rollover.
     */
    protected final Object csvFileLock = new Object();

    private final DecimalFormat twoDecimalFormat = new DecimalFormat("#.##");

    private Context applicationContext;
    final Handler handler;
    private final String logDirectoryName;
    private final String fileNamePrefix;
    private final boolean lazyFileCreation;
    private final RolloverWorker rolloverWorker = new RolloverWorker();

    CSVPrinter printer;
    volatile boolean loggingEnabled;
    private String logFileDirectoryPath;

    private String loggingFileName;

    /**
     * Constructs a Logger that writes Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     * @param logDirectoryName     The parent directory name to write all the files in.
     * @param fileNamePrefix       The prefix to use for the GeoPackage file name.
     */
    CsvRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper,
                    String logDirectoryName, String fileNamePrefix, boolean lazyFileCreation)
    {
        applicationContext = networkSurveyService.getApplicationContext();
        handler = new Handler(serviceLooper);
        this.logDirectoryName = logDirectoryName;
        this.fileNamePrefix = fileNamePrefix;
        this.lazyFileCreation = lazyFileCreation;

        twoDecimalFormat.setRoundingMode(RoundingMode.CEILING);
    }

    public void onDestroy()
    {
        applicationContext = null;
    }

    abstract String[] getHeaders();

    /**
     * @return A String array of comments and other information that should be written to the top of the CSV file.
     */
    abstract String[] getHeaderComments();

    /**
     * Sets up all the GeoPackage stuff so that the survey records can be written to a log file.
     * <p>
     * If calling this method, it is assumed that the caller will add this {@link CsvRecordLogger} as a listener for
     * survey records.
     *
     * @param enable True if logging is being turned on, false if the log file should be closed.
     * @return True if the toggling action was successful, false if the request could not be completed.
     */
    public boolean enableLogging(boolean enable)
    {
        synchronized (csvFileLock)
        {
            try
            {
                Timber.i("Toggling CSV logging to %s", enable);
                if (!enable)
                {
                    if (loggingEnabled)
                    {
                        loggingEnabled = false;
                        loggingFileName = null;
                        printer.close(true);
                        printer = null;
                        rolloverWorker.reset();
                        return true;
                    }

                    return false;
                }

                if (!isExternalStorageWritable()) return false;

                updateRolloverWorker();

                if (lazyFileCreation) return true;

                loggingFileName = null;
                boolean fileCreated = prepareCsvForLogging();

                return loggingEnabled = fileCreated;
            } catch (Exception e)
            {
                Timber.e(e, "Caught an exception when trying prepare CSV file for logging");
                if (printer != null)
                {
                    try
                    {
                        printer.close(true);
                    } catch (IOException ioe)
                    {
                        Timber.e(ioe, "Caught another exception when trying to close the printer to handle the previous error");
                    } finally
                    {
                        printer = null;
                        loggingFileName = null;
                    }
                }
                return false;
            }
        }
    }

    synchronized void writeCsvRecord(Object[] row, boolean flush) throws IOException
    {
        if (lazyFileCreation) lazyCreateFileIfNecessary();
        printer.printRecord(row);
        if (flush) printer.flush();
        checkIfRolloverNeeded();
    }

    /**
     * If lazy file creation is enabled, and the file has not yet been created, then this method
     * creates the CSV file.
     * <p>
     * This method should only be called if lazy file creation was set to true when creating this
     * logger. And it is important to call this method at least once before using the printer.
     */
    private void lazyCreateFileIfNecessary()
    {
        if (loggingFileName == null) prepareCsvForLogging();
    }

    /**
     * Creates and sets up a CSV file to be ready for logging.
     * <p>
     * This method is NOT thread safe and it is assumed the caller has already gotten a lock on the
     * {@link #csvFileLock} before making a call to this method.
     *
     * @return True, if the operations were successful.
     */
    private synchronized boolean prepareCsvForLogging()
    {
        loggingFileName = createPublicStorageFilePath();

        Timber.i("Creating the log file: %s", loggingFileName);

        final String versionName = NsUtils.getAppVersionName(applicationContext);
        final List<String> headerComments = new ArrayList<>();
        headerComments.add("Created by Network Survey version=" + versionName);

        String[] additionalComments = getHeaderComments();
        Collections.addAll(headerComments, additionalComments);

        CSVFormat csvFormat = CSVFormat.Builder.create()
                .setCommentMarker('#')
                .setHeaderComments(headerComments.toArray(new String[0]))
                .setHeader(getHeaders())
                .build();
        try
        {
            final FileWriter out = new FileWriter(loggingFileName);
            printer = new CSVPrinter(out, csvFormat);
            printer.flush();
        } catch (IOException e)
        {
            final String errorMessage = "Error: Unable to create the CSV file.  No logging will be recorded.";
            Timber.e(e, errorMessage);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show());

            loggingFileName = null;

            if (printer != null)
            {
                try
                {
                    printer.close();
                } catch (IOException ignore)
                {
                } finally
                {
                    printer = null;
                }
            }
            return false;
        }

        return true;
    }

    /**
     * Updates the rollover size from the SharedPreferences, or the MDM properties if enabled.
     *
     * @since 0.4.0
     */
    private void updateRolloverWorker()
    {
        final int logRolloverSize = PreferenceUtils.getRolloverSizePreference(applicationContext);

        rolloverWorker.update(logRolloverSize);
    }

    /**
     * Checks to see if the rollover worker needs to initiate the creation of a new log file.
     *
     * @since 0.4.0
     */
    protected void checkIfRolloverNeeded()
    {
        rolloverWorker.incrementRolloverCounter();
    }

    /**
     * Update the max log size if the preference has changed via shared preferences.
     *
     * @since 0.4.0
     */
    public void onSharedPreferenceChanged()
    {
        updateRolloverWorker();
    }

    /**
     * Update the max log size if the preference has changed via MDM.
     *
     * @since 0.4.0
     */
    public void onMdmPreferenceChanged()
    {
        updateRolloverWorker();
    }

    /**
     * Checks to see if we can write to the external storage area.  It might be unavailable if the
     * storage is connected to a computer.
     *
     * @return True if available, false if the storage volume is not able to be written to.
     */
    private boolean isExternalStorageWritable()
    {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Creates the absolute path to the log file that this class writes to.
     * <p>
     * Also, as a side effect, this class creates and sets the {@link #logFileDirectoryPath} instance variable.
     *
     * @return The full path to the CSV log file.
     */
    private String createPublicStorageFilePath()
    {
        logFileDirectoryPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS) + "/" + logDirectoryName + "/";

        try
        {
            Files.createDirectories(Paths.get(logFileDirectoryPath));
        } catch (IOException e)
        {
            Timber.e(e, "Could not create the CSV log file directory");
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(applicationContext, "Error: Could not create the CSV directory", Toast.LENGTH_SHORT).show());
        }

        String filePath = logFileDirectoryPath +
                fileNamePrefix + SurveyRecordProcessor.DATE_TIME_FORMATTER.format(LocalDateTime.now()) + ".csv";

        // I have seen a couple times now that it is possible that the RolloverWorker can create two CSV files
        // within the same second. Both instances were bluetooth so I am thinking there were 500+ devices around which
        // caused the rollover worker to be run twice in the same scan iteration. I also increased the rollover
        // check to 5000 which should help prevent this error as well, but just in case it is best if we ensure the file
        // path is unique.
        int counter = 0;
        while (new File(filePath).exists())
        {
            counter++;
            filePath = logFileDirectoryPath + fileNamePrefix +
                    SurveyRecordProcessor.DATE_TIME_FORMATTER.format(LocalDateTime.now()) + "-" + counter + ".csv";
        }

        return filePath;
    }

    /**
     * Private class that kicks off a rollover task when the max file size has been reached.
     *
     * @since 0.4.0
     */
    private class RolloverWorker
    {
        private static final int BYTES_TO_MEGABYTES = 1_048_576;

        /**
         * A lock that synchronizes read and write operations on {@link #rolloverSizeBytes}. For
         * instance, this lock protects against an update of 0 to the rollover size occurring after
         * we check if the rollover indeed equals 0. That could potentially roll over the file when
         * the user sets the rollover option to 'Never'.
         */
        private final Object rolloverSizeLock = new Object();

        /**
         * The record count since last reset, or last rollover.
         */
        private final AtomicInteger recordCount = new AtomicInteger();

        /**
         * The max log size for a GeoPackage file before a new one is created, in bytes. When this
         * value is set to 0, rollover is de-activated.
         */
        private int rolloverSizeBytes = Integer.parseInt(NetworkSurveyConstants.DEFAULT_ROLLOVER_SIZE_MB) * BYTES_TO_MEGABYTES;

        /**
         * Update the rollover worker with new values.
         *
         * @param logRolloverSizeMb The limit of each log file before it is rolled over, in MB
         */
        public void update(int logRolloverSizeMb)
        {
            synchronized (rolloverSizeLock)
            {
                Timber.i("Log Rollover Size updated to %s MB", logRolloverSizeMb);
                rolloverSizeBytes = logRolloverSizeMb * BYTES_TO_MEGABYTES;
            }
        }

        /**
         * Increments rollover record count. At the mark of {@link #RECORD_COUNT_INTERVAL} records, we check
         * the CSV file size. If the file size is equal to or greater than the size
         * threshold, we roll over. If no rollover is enabled, the method immediately returns.
         */
        public void incrementRolloverCounter()
        {
            synchronized (rolloverSizeLock)
            {
                if (rolloverSizeBytes == 0)
                {
                    return; // A rollover of size 0 means rollover is not active
                }

                if (recordCount.compareAndSet(RECORD_COUNT_INTERVAL, 0))
                {
                    File file;
                    // Need to synchronize so that we don't try to get the file while a new one is being created.
                    synchronized (csvFileLock)
                    {
                        file = new File(loggingFileName);
                    }
                    final long fileSizeBytes = file.length();

                    Timber.v("Checking GeoPackage file size, currently at: %s bytes", fileSizeBytes);
                    if (fileSizeBytes >= rolloverSizeBytes)
                    {
                        // This task is protected by the {@link #csvFileLock} to prevent closing a file that is
                        // currently being logged to.
                        synchronized (csvFileLock)
                        {
                            try
                            {
                                printer.close(true);

                                boolean fileCreated = prepareCsvForLogging();
                                if (!fileCreated)
                                {
                                    Timber.e("Failed to create a new rollover CSV file");
                                }
                            } catch (Exception e)
                            {
                                Timber.e(e, "Error occurred while trying to create a rollover CSV file");
                            }
                        }
                    }

                    return;
                }
            }

            recordCount.getAndIncrement();
        }

        /**
         * Resets the record count.
         */
        public void reset()
        {
            recordCount.set(0);
        }
    }

    /**
     * Trims a double (location) to six decimal places, not removing extra zeros.
     *
     * @param value The double to trim.
     * @return The trimmed double as a string.
     */
    String trimToSixDecimalPlaces(double value)
    {
        return String.format(Locale.getDefault(), "%.6f", value);
    }

    /**
     * Rounds a double to 2 decimal places, removing extra zeros.
     *
     * @param value The double to round.
     * @return The rounded double as a string.
     */
    String roundToTwoDecimalPlaces(double value)
    {
        return twoDecimalFormat.format(value);
    }
}
