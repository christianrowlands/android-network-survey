package com.craxiom.networksurvey;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.os.Handler;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.core.contents.Contents;
import mil.nga.geopackage.core.contents.ContentsDao;
import mil.nga.geopackage.core.contents.ContentsDataType;
import mil.nga.geopackage.core.srs.SpatialReferenceSystem;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.columns.GeometryColumnsDao;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.FeatureTable;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.user.UserTable;
import mil.nga.sf.GeometryType;
import mil.nga.sf.Point;
import mil.nga.sf.proj.ProjectionConstants;

import static mil.nga.geopackage.db.GeoPackageDataType.DATETIME;
import static mil.nga.geopackage.db.GeoPackageDataType.INT;

/**
 * Responsible for pulling the network values and populating the UI.  This class can also optionally
 * write the network values to a log file.
 *
 * @since 0.0.2
 */
class SurveyRecordWriter
{
    private static final String DATABASE_NAME = "LTE";
    private static final String LOG_DIRECTORY_NAME = "NetworkSurveyData";
    private static final SimpleDateFormat formatFilenameFriendlyTime = new SimpleDateFormat("YYYYMMdd HHmmss", Locale.US);
    private static final String FILE_NAME_PREFIX = "craxiom-survey";
    private static final String LTE_RECORDS_TABLE_NAME = "lte_drive_test_records";
    private static final long WGS84_SRS = 4326;

    private static final String ID_COLUMN = "id";
    private static final String GEOMETRY_COLUMN = "geom";
    private static final String TIME_COLUMN = "Time";
    private static final String RECORD_NUMBER_COLUMN = "RecordNumber";
    private static final String GROUP_NUMBER_COLUMN = "GroupNumber";
    private static final String MCC_COLUMN = "MCC";
    private static final String MNC_COLUMN = "MNC";
    private static final String TAC_COLUMN = "TAC";
    private static final String CI_COLUMN = "CI";
    private static final String EARFCN_COLUMN = "EARFCN";
    private static final String PCI_COLUMN = "PCI";
    private static final String RSRP_COLUMN = "RSRP";
    private static final String RSRQ_COLUMN = "RSRQ";
    private static final String TA_COLUMN = "TA";

    private final String LOG_TAG = SurveyRecordWriter.class.getSimpleName();

    private final LocationManager locationManager;
    private final TelephonyManager telephonyManager;
    private final NetworkDetailsActivity networkDetailsActivity;
    private final String bestProvider;
    private final Handler handler = new Handler();

    private volatile boolean loggingEnabled;
    private final GeoPackageManager geoPackageManager;
    private GeoPackage geoPackage;

    private volatile int recordNumber = 0;
    private volatile int groupNumber = -1; // This will be incremented to 0 the first time it is used.

    SurveyRecordWriter(LocationManager locationManager, TelephonyManager telephonyManager,
                       NetworkDetailsActivity networkDetailsActivity, String bestProvider)
    {
        this.locationManager = locationManager;
        this.telephonyManager = telephonyManager;
        this.networkDetailsActivity = networkDetailsActivity;
        this.bestProvider = bestProvider;

        geoPackageManager = GeoPackageFactory.getManager(networkDetailsActivity);
    }

    /**
     * Uses the {@link TelephonyManager} to pull the current network details, displays the values
     * in the UI, and also writes the values out to a log file for other systems to post process.
     */
    void logSurveyRecord() throws SecurityException
    {
        groupNumber++; // Group all the LTE records found in this scan iteration.

        final List<CellInfo> allCellInfo = telephonyManager.getAllCellInfo();
        if (allCellInfo.size() > 0)
        {
            for (CellInfo cellInfo : allCellInfo)
            {
                // For now, just look for LTE towers
                if (cellInfo instanceof CellInfoLte)
                {
                    if (cellInfo.isRegistered())
                    {
                        // This record is for the serving cell
                        parseServingCellInfo((CellInfoLte) cellInfo);
                    } else
                    {
                        // This represents a neighbor record
                        parseNeighborCellInfo((CellInfoLte) cellInfo);
                    }
                }
            }
        }
    }

    void enableLogging(boolean enable) throws SQLException
    {
        if (!loggingEnabled)
        {
            loggingEnabled = false;
            geoPackage.close(); // TODO figure out what else needs to be done to clean up.
            geoPackage = null;
            return;
        }

        if (isExternalStorageWritable())
        {
            final File loggingFile = getPrivateStorageFile(networkDetailsActivity,
                    FILE_NAME_PREFIX + "-" + formatFilenameFriendlyTime.format(System.currentTimeMillis()));

            final boolean created = geoPackageManager.createAtPath(DATABASE_NAME, loggingFile);

            if (!created)
            {
                Log.e(LOG_TAG, "Unable to create the GeoPackage File.  No logging will be recorded.");
                // TODO show a toast to the user
                return;
            }

            geoPackage = geoPackageManager.open(DATABASE_NAME);

            final SpatialReferenceSystem spatialReferenceSystem;
            spatialReferenceSystem = geoPackage.getSpatialReferenceSystemDao()
                    .getOrCreateCode(ProjectionConstants.AUTHORITY_EPSG, (long) ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

            geoPackage.createGeometryColumnsTable();
            createLteRecordTable(geoPackage, spatialReferenceSystem, LTE_RECORDS_TABLE_NAME, GeometryType.POINT);
        }

        loggingEnabled = enable;
    }

    private UserTable createLteRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs, String tableName, GeometryType type) throws SQLException
    {
        ContentsDao contentsDao = geoPackage.getContentsDao();

        Contents contents = new Contents();
        contents.setTableName(tableName);
        contents.setDataType(ContentsDataType.FEATURES);
        contents.setIdentifier(tableName);
        contents.setDescription(tableName);
        contents.setSrs(srs);

        int colNum = 0;
        List<FeatureColumn> tblcols = new LinkedList<>();
        tblcols.add(FeatureColumn.createPrimaryKeyColumn(colNum++, ID_COLUMN));
        tblcols.add(FeatureColumn.createGeometryColumn(colNum++, GEOMETRY_COLUMN, GeometryType.POINT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, "Time", DATETIME, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, RECORD_NUMBER_COLUMN, INT, true, -1));
        tblcols.add(FeatureColumn.createColumn(colNum++, GROUP_NUMBER_COLUMN, INT, true, -1));
        // TODO Why do we need Lat and Lon?  I would have guessed the geometry column would be used for that
        //tblcols.add(FeatureColumn.createColumn(colNum++, "Lat", REAL, false, null));
        //tblcols.add(FeatureColumn.createColumn(colNum++, "Lon", REAL, false, null));
        //tblcols.add(FeatureColumn.createColumn(colNum++, "Alt", REAL, false, null));

        tblcols.add(FeatureColumn.createColumn(colNum++, MCC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, MNC_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, TAC_COLUMN, GeoPackageDataType.INT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, CI_COLUMN, GeoPackageDataType.INT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, EARFCN_COLUMN, GeoPackageDataType.INT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, PCI_COLUMN, GeoPackageDataType.SMALLINT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, RSRP_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, RSRQ_COLUMN, GeoPackageDataType.FLOAT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, TA_COLUMN, GeoPackageDataType.SMALLINT, false, null));

        FeatureTable table = new FeatureTable(tableName, tblcols);
        geoPackage.createFeatureTable(table);

        contentsDao.create(contents);

        GeometryColumnsDao geometryColumnsDao = geoPackage.getGeometryColumnsDao();

        GeometryColumns geometryColumns = new GeometryColumns();
        geometryColumns.setContents(contents);
        geometryColumns.setColumnName(GEOMETRY_COLUMN);
        geometryColumns.setGeometryType(type);
        geometryColumns.setSrs(srs);
        geometryColumns.setZ((byte) 0); // TODO I am not sure if all of this is right
        geometryColumns.setM((byte) 0);
        geometryColumnsDao.create(geometryColumns);

        return (table);
    }

    /**
     * Convert the serving cell {@link CellInfoLte} object into an {@link LteSurveyRecord}, and update the UI with
     * the latest cell details.
     *
     * @param cellInfoLte The LTE serving cell details.
     */
    private void parseServingCellInfo(CellInfoLte cellInfoLte)
    {
        final LteSurveyRecord lteSurveyRecord = generateLteSurveyRecord(cellInfoLte);

        writeSurveyRecordEntryToLogFile(lteSurveyRecord);
        updateUi(lteSurveyRecord);
    }

    /**
     * Convert the neighbor cell {@link CellInfoLte} object into an {@link LteSurveyRecord}.
     *
     * @param cellInfoLte The LTE neighbor cell details.
     */
    private void parseNeighborCellInfo(CellInfoLte cellInfoLte)
    {
        //Log.i(LOG_TAG, "LTE Neighbor Cell : " + cellInfoLte.getCellIdentity().toString() + "\n LTE Neighbor Signal Values: " + cellInfoLte.getCellSignalStrength().toString());

        if (loggingEnabled)
        {
            writeSurveyRecordEntryToLogFile(generateLteSurveyRecord(cellInfoLte));
        }
    }

    /**
     * Given a {@link CellInfoLte} object, pull out the values and generate an {@link LteSurveyRecord}.
     *
     * @param cellInfoLte The object that contains the LTE Cell info.  This can be a serving cell,
     *                    or a neighbor cell.
     * @return The survey record.
     */
    private LteSurveyRecord generateLteSurveyRecord(CellInfoLte cellInfoLte)
    {
        final CellIdentityLte cellIdentity = cellInfoLte.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int tac = cellIdentity.getTac();
        final int ci = cellIdentity.getCi();
        final int earfcn = cellIdentity.getEarfcn();
        final int pci = cellIdentity.getPci();

        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
        final int rsrp = cellSignalStrengthLte.getRsrp();
        final int rsrq = cellSignalStrengthLte.getRsrq();
        final int timingAdvance = cellSignalStrengthLte.getTimingAdvance();

        final LteSurveyRecordBuilder lteSurveyRecordBuilder = new LteSurveyRecordBuilder();

        if (locationManager != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = locationManager.getLastKnownLocation(bestProvider);
            lteSurveyRecordBuilder.setLocation(lastKnownLocation);
        }

        lteSurveyRecordBuilder.setTime(System.currentTimeMillis());
        lteSurveyRecordBuilder.setRecordNumber(recordNumber++);
        lteSurveyRecordBuilder.setGroupNumber(groupNumber);
        lteSurveyRecordBuilder.setMcc(mcc);
        lteSurveyRecordBuilder.setMnc(mnc);
        lteSurveyRecordBuilder.setTac(tac);
        lteSurveyRecordBuilder.setCi(ci);
        lteSurveyRecordBuilder.setEarfcn(earfcn);
        lteSurveyRecordBuilder.setPci(pci);
        lteSurveyRecordBuilder.setRsrp(rsrp);
        lteSurveyRecordBuilder.setRsrq(rsrq);
        lteSurveyRecordBuilder.setTa(timingAdvance);

        return lteSurveyRecordBuilder.createLteSurveyRecord();
    }

    private void updateUi(LteSurveyRecord lteSurveyRecord)
    {
        checkAndSetValue(lteSurveyRecord.getMcc(), (TextView) networkDetailsActivity.findViewById(R.id.mccValue));
        checkAndSetValue(lteSurveyRecord.getMnc(), (TextView) networkDetailsActivity.findViewById(R.id.mncValue));
        checkAndSetValue(lteSurveyRecord.getTac(), (TextView) networkDetailsActivity.findViewById(R.id.tacValue));

        final int ci = lteSurveyRecord.getCi();
        if (ci != Integer.MAX_VALUE)
        {
            checkAndSetValue(ci, (TextView) networkDetailsActivity.findViewById(R.id.cidValue));

            // The Cell Identity is 28 bits long. The first 20 bits represent the Macro eNodeB ID. The last 8 bits
            // represent the sector.  Strip off the last 8 bits to get the Macro eNodeB ID.
            int eNodebId = ci >> 8;
            ((TextView) networkDetailsActivity.findViewById(R.id.enbIdValue)).setText(String.valueOf(eNodebId));

            int sectorId = ci & 0xFF;
            ((TextView) networkDetailsActivity.findViewById(R.id.sectorIdValue)).setText(String.valueOf(sectorId));
        }

        checkAndSetValue(lteSurveyRecord.getEarfcn(), (TextView) networkDetailsActivity.findViewById(R.id.earfcnValue));
        checkAndSetValue(lteSurveyRecord.getPci(), (TextView) networkDetailsActivity.findViewById(R.id.pciValue));

        checkAndSetLocation(lteSurveyRecord.getLocation());

        checkAndSetValue(lteSurveyRecord.getRsrp(), (TextView) networkDetailsActivity.findViewById(R.id.rsrpValue));
        checkAndSetValue(lteSurveyRecord.getRsrq(), (TextView) networkDetailsActivity.findViewById(R.id.rsrqValue));
        checkAndSetValue(lteSurveyRecord.getTa(), (TextView) networkDetailsActivity.findViewById(R.id.taValue));
    }

    /**
     * Checks to make sure the value is valid, and then sets it to the provided {@link TextView}.
     *
     * @param valueToCheck The value to check.  If the value is equal to {@link Integer#MAX_VALUE},
     *                     it is ignored, otherwise it is set to the text view.
     * @param textView     The text view to set the value on.
     */
    private void checkAndSetValue(int valueToCheck, TextView textView)
    {
        if (valueToCheck != Integer.MAX_VALUE)
        {
            textView.setText(String.valueOf(valueToCheck));
        } else
        {
            textView.setText("");
        }
    }

    /**
     * Checks to make sure the location is not null, and then updates the appropriate UI elements.
     *
     * @param location The location to check and use if it is valid.
     */
    private void checkAndSetLocation(Location location)
    {
        if (location != null)
        {
            ((TextView) networkDetailsActivity.findViewById(R.id.latitudeValue)).setText(String.valueOf(location.getLatitude()));
            ((TextView) networkDetailsActivity.findViewById(R.id.longitudeValue)).setText(String.valueOf(location.getLongitude()));
        } else
        {
            ((TextView) networkDetailsActivity.findViewById(R.id.latitudeValue)).setText("");
            ((TextView) networkDetailsActivity.findViewById(R.id.longitudeValue)).setText("");
        }
    }

    private void writeSurveyRecordEntryToLogFile(final LteSurveyRecord lteSurveyRecord)
    {
        if (!loggingEnabled)
        {
            Log.v(LOG_TAG, "Not writing the log file because logging is turned off");
            return;
        }

        handler.post(new Runnable()
        {
            public void run()
            {
                try
                {
                    if (geoPackage != null)
                    {
                        FeatureDao featureDao = geoPackage.getFeatureDao(LTE_RECORDS_TABLE_NAME);
                        FeatureRow row = featureDao.newRow();

                        final Location location = lteSurveyRecord.getLocation();
                        Point fix = new Point(location.getLongitude(), location.getLatitude(), location.getAltitude());

                        GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                        geomData.setGeometry(fix);

                        row.setGeometry(geomData);

                        //row.setValue("Lat", (double) loc.getLatitude());
                        //row.setValue("Lon", (double) loc.getLongitude());
                        //row.setValue("Alt", (double) loc.getAltitude());
                        row.setValue(TIME_COLUMN, lteSurveyRecord.getTime());
                        row.setValue(RECORD_NUMBER_COLUMN, lteSurveyRecord.getRecordNumber());
                        row.setValue(GROUP_NUMBER_COLUMN, lteSurveyRecord.getGroupNumber());

                        setIntValue(row, MCC_COLUMN, lteSurveyRecord.getMcc());
                        setIntValue(row, MNC_COLUMN, lteSurveyRecord.getMnc());
                        setIntValue(row, TAC_COLUMN, lteSurveyRecord.getTac());
                        setIntValue(row, CI_COLUMN, lteSurveyRecord.getCi());
                        setIntValue(row, EARFCN_COLUMN, lteSurveyRecord.getEarfcn());
                        setIntValue(row, PCI_COLUMN, lteSurveyRecord.getPci());
                        setIntValue(row, RSRP_COLUMN, lteSurveyRecord.getRsrp());
                        setIntValue(row, RSRQ_COLUMN, lteSurveyRecord.getRsrq());
                        setIntValue(row, TA_COLUMN, lteSurveyRecord.getTa());

                        featureDao.insert(row);
                    }
                } catch (Exception e)
                {
                    Log.e(LOG_TAG, "Something went wrong when trying to write an LTE survey record", e);
                }
            }
        });
    }

    /**
     * Checks to see if the provided value is valid, and if it is the value is set on the row at the
     * specified column.
     * <p>
     * An invalid value is equal to {@link Integer#MAX_VALUE}.
     *
     * @param featureRow The row to populate the value.
     * @param columnName The column to set the value in.
     * @param value      The value to check, and then set.
     */
    private void setIntValue(FeatureRow featureRow, String columnName, int value)
    {
        if (value != Integer.MAX_VALUE)
        {
            featureRow.setValue(columnName, value);
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

    private File getPrivateStorageFile(Context context, String fileName)
    {
        return new File(context.getExternalFilesDir(null), fileName);
    }

    public File getPublicStorageDir()
    {
        // Get the directory for the log file
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), LOG_DIRECTORY_NAME);
        if (!file.mkdirs())
        {
            Log.e(LOG_TAG, "The Network Survey Data Directory could not be created");
        }

        return file;
    }
}
