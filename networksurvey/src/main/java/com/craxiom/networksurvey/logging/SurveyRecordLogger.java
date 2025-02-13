package com.craxiom.networksurvey.logging;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.craxiom.messaging.LteBandwidth;
import com.craxiom.networksurvey.constants.CellularMessageConstants;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.MessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.csv.CellularCsvConstants;
import com.craxiom.networksurvey.constants.csv.CsvConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.SurveyRecordProcessor;
import com.craxiom.networksurvey.util.PreferenceUtils;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageFactory;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDao;
import mil.nga.geopackage.contents.ContentsDataType;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.columns.GeometryColumnsDao;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.FeatureTable;
import mil.nga.geopackage.srs.SpatialReferenceSystem;
import mil.nga.proj.ProjectionConstants;
import mil.nga.sf.GeometryType;
import timber.log.Timber;

/**
 * Abstract base class for logging survey records to a GeoPackage file.
 * <p>
 * This class initializes the GeoPackage file, and closes it out when it is done.
 *
 * @since 0.0.5
 */
public abstract class SurveyRecordLogger
{
    private static final String JOURNAL_FILE_SUFFIX = "-journal";
    private static final int RECORD_COUNT_INTERVAL = 5000;
    static final long WGS84_SRS = 4326;

    private NetworkSurveyService networkSurveyService;
    private Context applicationContext;
    Handler handler;
    private final String logDirectoryName;
    private final String fileNamePrefix;
    private final GeoPackageManager geoPackageManager;
    private final RolloverWorker rolloverWorker = new RolloverWorker();

    GeoPackage geoPackage;
    volatile boolean loggingEnabled;
    private String logFileDirectoryPath;

    /**
     * A lock to synchronize the writing of single records and the creation of a new GeoPackage file
     * during rollover.
     */
    protected final Object geoPackageLock = new Object();

    /**
     * Constructs a Logger that writes Survey records to a GeoPackage SQLite database.
     *
     * @param networkSurveyService The Service instance that is running this logger.
     * @param serviceLooper        The Looper associated with the service that can be used to do any background processing.
     * @param logDirectoryName     The parent directory name to write all the files in.
     * @param fileNamePrefix       The prefix to use for the GeoPackage file name.
     */
    SurveyRecordLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper, String logDirectoryName, String fileNamePrefix)
    {
        this.networkSurveyService = networkSurveyService;
        applicationContext = networkSurveyService.getApplicationContext();
        handler = new Handler(serviceLooper);
        this.logDirectoryName = logDirectoryName;
        this.fileNamePrefix = fileNamePrefix;

        geoPackageManager = GeoPackageFactory.getManager(networkSurveyService.getApplicationContext());
    }

    public void onDestroy()
    {
        networkSurveyService = null;
        applicationContext = null;
        handler = null;
    }

    /**
     * Create all GeoPackage table(s) that can be populated with records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    abstract void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException;

    /**
     * Sets up all the GeoPackage stuff so that the survey records can be written to a log file.
     * <p>
     * If calling this method, it is assumed that the caller will add this {@link SurveyRecordLogger} as a listener for
     * survey records.
     *
     * @param enable True if logging is being turned on, false if the log file should be closed.
     * @return True if the toggling action was successful, false if the request could not be completed.
     */
    public boolean enableLogging(boolean enable)
    {
        synchronized (geoPackageLock)
        {
            try
            {
                if (!enable)
                {
                    if (loggingEnabled)
                    {
                        loggingEnabled = false;
                        geoPackage.close();
                        geoPackage = null;
                        removeTempFiles();
                        rolloverWorker.reset();
                        return true;
                    }

                    return false;
                }

                if (!isExternalStorageWritable()) return false;

                boolean fileCreated = prepareGeoPackageForLogging();

                updateRolloverWorker();

                return loggingEnabled = fileCreated;
            } catch (Exception e)
            {
                Timber.e(e, "Caught an exception when trying prepare GeoPackage file for logging");
                if (geoPackage != null)
                {
                    geoPackage.close();
                    geoPackage = null;
                }
                return false;
            }
        }
    }

    /**
     * Creates and sets up a GeoPackage file to be ready for survey logging.
     * <p>
     * This method is NOT thread safe and it is assumed the caller has already gotten a lock on the
     * {@link #geoPackageLock} before making a call to this method.
     *
     * @return True, if the operations were successful.
     * @throws SQLException Thrown if database manipulations resulted in failure.
     */
    private boolean prepareGeoPackageForLogging() throws SQLException
    {
        final String loggingFile = createPublicStorageFilePath();

        Timber.i("Creating the log file: %s", loggingFile);

        final boolean created = geoPackageManager.create(loggingFile);
        final Context applicationContext = networkSurveyService.getApplicationContext();

        if (!created)
        {
            final String errorMessage = "Error: Unable to create the GeoPackage file.  No logging will be recorded.";
            Timber.e(errorMessage);
            Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show();
            return false;
        }

        geoPackage = geoPackageManager.open(loggingFile);
        if (geoPackage == null)
        {
            final String errorMessage = "Error: Unable to open the GeoPackage file.  No logging will be recorded.";
            Timber.e(errorMessage);
            Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show();
            return false;
        }

        final SpatialReferenceSystem spatialReferenceSystem = geoPackage.getSpatialReferenceSystemDao()
                .getOrCreateCode(ProjectionConstants.AUTHORITY_EPSG, ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

        geoPackage.createGeometryColumnsTable();
        createTables(geoPackage, spatialReferenceSystem);

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
     * Cre ate a Table in the provided GeoPackage.  This method will create a table with certain standard columns, such as time and location, and will also
     * call the {@code customColumnAddition} consumer to trigger the addition of any protocol specific columns.
     *
     * @param tableName            The name of the table to create.
     * @param geoPackage           The GeoPackage to create the table in.
     * @param srs                  The SRS to use for the table coordinates.
     * @param addCellularColumns   If true, the cellular specific columns are added to the file (see {@link CellularMessageConstants}).
     * @param customColumnAddition The consumer responsible for adding the custom columns associated with the protocol.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    void createTable(String tableName, GeoPackage geoPackage, SpatialReferenceSystem srs, boolean addCellularColumns,
                     BiConsumer<List<FeatureColumn>, Integer> customColumnAddition) throws SQLException
    {
        ContentsDao contentsDao = geoPackage.getContentsDao();

        Contents contents = new Contents();
        contents.setTableName(tableName);
        contents.setDataType(ContentsDataType.FEATURES);
        contents.setIdentifier(tableName);
        contents.setDescription(tableName);
        contents.setSrs(srs);

        int columnNumber = 0;
        List<FeatureColumn> tableColumns = new LinkedList<>();
        tableColumns.add(FeatureColumn.createPrimaryKeyColumn(columnNumber++, MessageConstants.ID_COLUMN));
        tableColumns.add(FeatureColumn.createGeometryColumn(columnNumber++, MessageConstants.GEOMETRY_COLUMN, GeometryType.POINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CsvConstants.DEVICE_SERIAL_NUMBER, GeoPackageDataType.TEXT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, MessageConstants.TIME_COLUMN, GeoPackageDataType.INT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, MessageConstants.MISSION_ID_COLUMN, GeoPackageDataType.TEXT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, MessageConstants.RECORD_NUMBER_COLUMN, GeoPackageDataType.MEDIUMINT, true, -1));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CsvConstants.SPEED, GeoPackageDataType.FLOAT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, MessageConstants.ACCURACY, GeoPackageDataType.MEDIUMINT, false, null));
        tableColumns.add(FeatureColumn.createColumn(columnNumber++, CsvConstants.LOCATION_AGE, GeoPackageDataType.MEDIUMINT, false, null));

        if (addCellularColumns)
        {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CellularMessageConstants.GROUP_NUMBER_COLUMN, GeoPackageDataType.MEDIUMINT, true, -1));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CellularMessageConstants.SERVING_CELL_COLUMN, GeoPackageDataType.BOOLEAN, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CellularMessageConstants.PROVIDER_COLUMN, GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, CellularCsvConstants.SLOT, GeoPackageDataType.SMALLINT, false, null));
        }

        customColumnAddition.accept(tableColumns, columnNumber);

        FeatureTable table = new FeatureTable(tableName, tableColumns);
        geoPackage.createFeatureTable(table);

        contentsDao.create(contents);

        GeometryColumnsDao geometryColumnsDao = geoPackage.getGeometryColumnsDao();

        GeometryColumns geometryColumns = new GeometryColumns();
        geometryColumns.setContents(contents);
        geometryColumns.setColumnName(MessageConstants.GEOMETRY_COLUMN);
        geometryColumns.setGeometryType(GeometryType.POINT);
        geometryColumns.setSrs(srs);
        geometryColumns.setZ((byte) 0);
        geometryColumns.setM((byte) 0);
        geometryColumnsDao.create(geometryColumns);
    }

    /**
     * Converts the LTE Bandwidth to a float and sets it on the provided row.
     *
     * @param featureRow   The row of the GeoPackage file to set the LTE Bandwidth on.
     * @param lteBandwidth The LTE Bandwidth enum to convert to a float.
     */
    void setLteBandwidth(FeatureRow featureRow, LteBandwidth lteBandwidth)
    {
        final String lteBandwidthString = LteMessageConstants.getLteBandwidth(lteBandwidth);
        if (!lteBandwidthString.isEmpty())
        {
            featureRow.setValue(LteMessageConstants.BANDWIDTH_COLUMN, lteBandwidthString);
        }
    }

    /**
     * Sets the provided value on the row at the specified column as an int ({@link GeoPackageDataType#MEDIUMINT}).
     *
     * @param featureRow The row to populate the value.
     * @param columnName The column to set the value in.
     * @param value      The value to set as an int.
     */
    void setIntValue(FeatureRow featureRow, String columnName, int value)
    {
        featureRow.setValue(columnName, value);
    }

    /**
     * Sets the provided value on the row at the specified column as a short ({@link GeoPackageDataType#SMALLINT}).
     *
     * @param featureRow The row to populate the value.
     * @param columnName The column to set the value in.
     * @param value      The value to set as a short.
     */
    void setShortValue(FeatureRow featureRow, String columnName, int value)
    {
        featureRow.setValue(columnName, (short) value);
    }

    /**
     * Deletes any temporary journal files in the save directory.
     *
     * @since 0.3.0
     */
    void removeTempFiles()
    {
        try
        {
            if (logFileDirectoryPath != null)
            {
                File dir = new File(logFileDirectoryPath);
                if (dir.exists())
                {
                    // If the file is not a directory, null will be returned
                    File[] files = dir.listFiles();

                    if (files != null)
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
     * @return The full path to the GeoPackage log file.
     */
    private String createPublicStorageFilePath()
    {
        logFileDirectoryPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS) + "/" + logDirectoryName + "/";

        String filePath = logFileDirectoryPath +
                fileNamePrefix + SurveyRecordProcessor.DATE_TIME_FORMATTER.format(LocalDateTime.now()) + ".gpkg";

        // I have seen a couple times now that it is possible that the RolloverWorker can create two GeoPackage files
        // within the same second. Both instances were bluetooth so I am thinking there were 500+ devices around which
        // caused the rollover worker to be run twice in the same scan iteration. I also increased the rollover
        // check to 5000 which should help prevent this error as well, but just in case it is best if we ensure the file
        // path is unique.
        int counter = 0;
        while (new File(filePath).exists())
        {
            counter++;
            filePath = logFileDirectoryPath + fileNamePrefix +
                    SurveyRecordProcessor.DATE_TIME_FORMATTER.format(LocalDateTime.now()) + "-" + counter + ".gpkg";
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
         * Update the rollover worker with perhaps new values.
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
         * the GeoPackage file size. If the file size is equal to or greater than the size
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
                    synchronized (geoPackageLock)
                    {
                        file = geoPackageManager.getFile(geoPackage.getName());
                    }
                    final long fileSizeBytes = file.length();

                    Timber.v("Checking GeoPackage file size, currently at: %s bytes", fileSizeBytes);
                    if (fileSizeBytes >= rolloverSizeBytes)
                    {
                        // This task is protected by the {@link #geoPackageLock} to prevent closing a file that is
                        // currently being logged to.
                        synchronized (geoPackageLock)
                        {
                            try
                            {
                                geoPackage.close();

                                boolean fileCreated = prepareGeoPackageForLogging();
                                if (!fileCreated)
                                {
                                    Timber.e("Failed to create a new GeoPackage file");
                                }
                            } catch (Exception e)
                            {
                                Timber.e(e, "Error occurred while trying to create a GeoPackage file");
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
}
