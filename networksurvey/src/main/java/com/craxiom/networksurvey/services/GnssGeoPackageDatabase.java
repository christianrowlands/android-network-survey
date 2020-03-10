package com.craxiom.networksurvey.services;

import android.content.Context;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import com.craxiom.networksurvey.model.SatelliteStatus;
import com.craxiom.networksurvey.util.GpsTestUtil;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.core.contents.Contents;
import mil.nga.geopackage.core.contents.ContentsDao;
import mil.nga.geopackage.core.contents.ContentsDataType;
import mil.nga.geopackage.core.srs.SpatialReferenceSystem;
import mil.nga.geopackage.core.srs.SpatialReferenceSystemDao;
import mil.nga.geopackage.extension.related.ExtendedRelation;
import mil.nga.geopackage.extension.related.RelatedTablesExtension;
import mil.nga.geopackage.extension.related.UserMappingDao;
import mil.nga.geopackage.extension.related.UserMappingRow;
import mil.nga.geopackage.extension.related.UserMappingTable;
import mil.nga.geopackage.extension.related.simple.SimpleAttributesDao;
import mil.nga.geopackage.extension.related.simple.SimpleAttributesRow;
import mil.nga.geopackage.extension.related.simple.SimpleAttributesTable;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.columns.GeometryColumnsDao;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.FeatureTable;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.user.custom.UserCustomColumn;
import mil.nga.sf.GeometryType;
import mil.nga.sf.Point;
import mil.nga.sf.proj.ProjectionConstants;

import static java.time.Instant.now;
import static mil.nga.geopackage.db.GeoPackageDataType.DATETIME;
import static mil.nga.geopackage.db.GeoPackageDataType.INTEGER;
import static mil.nga.geopackage.db.GeoPackageDataType.REAL;
import static mil.nga.geopackage.db.GeoPackageDataType.TEXT;

/**
 * A class to represent the GNSS GeoPackage database. This class is responsible for managing the database
 * details such as table and column names.
 */
public class GnssGeoPackageDatabase
{
    private static final String TAG = GnssGeoPackageDatabase.class.getSimpleName();

    private static final String CLOCK_TABLE_NAME = "rcvr_clock";
    private static final String POINTS_TABLE_NAME = "gps_observation_points";
    private static final String SAT_TABLE_NAME = "sat_data";

    private static final String CLOCK_MAP_TABLE_NAME = SAT_TABLE_NAME + "_" + CLOCK_TABLE_NAME;
    private static final String SAT_MAP_TABLE_NAME = POINTS_TABLE_NAME + "_" + SAT_TABLE_NAME;

    private static final String CLK_TIME_NANOS = "time_nanos";
    private static final String CLK_TIME_UNCERTAINTY_NANOS = "time_uncertainty_nanos";
    private static final String CLK_HAS_TIME_UNCERTAINTY_NANOS = "has_time_uncertainty_nanos";
    private static final String CLK_BIAS_NANOS = "bias_nanos";
    private static final String CLK_HAS_BIAS_NANOS = "has_bias_nanos";
    private static final String CLK_FULL_BIAS_NANOS = "full_bias_nanos";
    private static final String CLK_HAS_FULL_BIAS_NANOS = "has_full_bias_nanos";
    private static final String CLK_BIAS_UNCERTAINTY_NANOS = "bias_uncertainty_nanos";
    private static final String CLK_HAS_BIAS_UNCERTAINTY_NANOS = "has_bias_uncertainty_nanos";
    private static final String CLK_DRIFT_NANOS_PER_SEC = "drift_nanos_per_sec";
    private static final String CLK_HAS_DRIFT_NANOS_PER_SEC = "has_drift_nanos_per_sec";
    private static final String CLK_DRIFT_UNCERTAINTY_NPS = "drift_uncertainty_nps";
    private static final String CLK_HAS_DRIFT_UNCERTAINTY_NPS = "has_drift_uncertainty_nps";
    private static final String CLK_LEAP_SECOND = "leap_second";
    private static final String CLK_HAS_LEAP_SECOND = "has_leap_second";
    private static final String CLK_HW_CLOCK_DISCONTINUITY_COUNT = "hw_clock_discontinuity_count";

    private static final String DATA_DUMP = "data_dump";

    private static final String GPS_OBS_PT_LAT = "Lat";
    private static final String GPS_OBS_PT_LNG = "Lon";
    private static final String GPS_OBS_PT_ALT = "Alt";
    private static final String GPS_OBS_PT_GPS_TIME = "GPSTime";
    private static final String GPS_OBS_PT_PROB_RFI = "ProbabilityRFI";
    private static final String GPS_OBS_PT_PROB_CN0AGC = "ProbSpoofCN0AGC";
    private static final String GPS_OBS_PT_PROB_CONSTELLATION = "ProbSpoofConstellation";
    private static final String GPS_OBS_PT_PROVIDER = "Provider";
    private static final String GPS_OBS_PT_FIX_SAT_COUNT = "FixSatCount";
    private static final String GPS_OBS_PT_HAS_RADIAL_ACCURACY = "HasRadialAccuracy";
    private static final String GPS_OBS_PT_HAS_VERTICAL_ACCURACY = "HasVerticalAccuracy";
    private static final String GPS_OBS_PT_RADIAL_ACCURACY = "RadialAccuracy";
    private static final String GPS_OBS_PT_VERTICAL_ACCURACY = "VerticalAccuracy";
    private static final String GPS_OBS_PT_HAS_SPEED = "HasSpeed";
    private static final String GPS_OBS_PT_HAS_SPEED_ACCURACY = "HasSpeedAccuracy";
    private static final String GPS_OBS_PT_SPEED = "Speed";
    private static final String GPS_OBS_PT_SPEED_ACCURACY = "SpeedAccuracy";
    private static final String GPS_OBS_PT_HAS_BEARING = "HasBearing";
    private static final String GPS_OBS_PT_HAS_BEARING_ACCURACY = "HasBearingAccuracy";
    private static final String GPS_OBS_PT_BEARING = "Bearing";
    private static final String GPS_OBS_PT_BEARING_ACCURACY = "BearingAccuracy";
    private static final String GPS_OBS_PT_ELAPSED_REALTIME_NANOS = "ElapsedRealtimeNanos";
    private static final String GPS_OBS_PT_SYS_TIME = "SysTime";

    private static final String SAT_DATA_MEASURED_TIME = "local_time";
    private static final String SAT_DATA_SVID = "svid";
    private static final String SAT_DATA_CONSTELLATION = "constellation";
    private static final String SAT_DATA_CN0 = "cn0";
    private static final String SAT_DATA_AGC = "agc";
    private static final String SAT_DATA_HAS_AGC = "has_agc";
    private static final String SAT_DATA_IN_FIX = "in_fix";
    private static final String SAT_DATA_SYNC_STATE_FLAGS = "sync_state_flags";
    private static final String SAT_DATA_SYNC_STATE_TXT = "sync_state_txt";
    private static final String SAT_DATA_SAT_TIME_NANOS = "sat_time_nanos";
    private static final String SAT_DATA_SAT_TIME_1_SIGMA_NANOS = "sat_time_1sigma_nanos";
    private static final String SAT_DATA_RCVR_TIME_OFFSET_NANOS = "rcvr_time_offset_nanos";
    private static final String SAT_DATA_MULTIPATH = "multipath";
    private static final String SAT_DATA_HAS_CARRIER_FREQ = "has_carrier_freq";
    private static final String SAT_DATA_CARRIER_FREQ_HZ = "carrier_freq_hz";
    private static final String SAT_DATA_ACCUM_DELTA_RANGE = "accum_delta_range";
    private static final String SAT_DATA_ACCUM_DELTA_RANGE_1_SIGMA = "accum_delta_range_1sigma";
    private static final String SAT_DATA_ACCUM_DELTA_RANGE_STATE_FLAGS = "accum_delta_range_state_flags";
    private static final String SAT_DATA_ACCUM_DELTA_RANGE_STATE_TXT = "accum_delta_range_state_txt";
    private static final String SAT_DATA_PSEUDORANGE_RATE_MPS = "pseudorange_rate_mps";
    private static final String SAT_DATA_PSEUDORANGE_RATE_1_SIGMA = "pseudorange_rate_1sigma";
    private static final String SAT_DATA_HAS_EPHEMERIS = "has_ephemeris";
    private static final String SAT_DATA_HAS_ALMANAC = "has_almanac";
    private static final String SAT_DATA_AZIMUTH_DEG = "azimuth_deg";
    private static final String SAT_DATA_ELEVATION_DEG = "elevation_deg";

    private static final String ID_COLUMN = "id";
    private static final String GEOMETRY_COLUMN = "geom";
    private static final long WGS84_SRS = 4326;

    private final GeoPackageManager gpkgManager;
    private GeoPackage gpsGpkg;

    private HashMap<String, SatelliteStatus> satStatus = new HashMap<>();
    private HashMap<String, Long> satRowsToMap = new HashMap<>();
    private SimpleAttributesDao clkDao;
    private UserMappingDao clkMapDao;
    private SimpleAttributesDao satDao;
    private UserMappingDao satMapDao;

    GnssGeoPackageDatabase(Context context)
    {
        gpkgManager = GeoPackageFactory.getManager(context);
    }

    /**
     * Creates, opens, and adds tables to a GeoPackage database with the provided name. Note: this
     * method and any other method that accesses the GeoPackage is synchronized to ensure the
     * database doesn't get closed while it is being updated.
     *
     * @param databaseName the name of the database
     * @throws SQLException If an error occurs creating the database SRS or tables
     */
    public synchronized void start(String databaseName) throws SQLException
    {
        if (!gpkgManager.exists(databaseName))
        {
            gpkgManager.create(databaseName);
        }

        gpsGpkg = openDatabase(databaseName);

        // create SRS & feature tables
        SpatialReferenceSystemDao srsDao = gpsGpkg.getSpatialReferenceSystemDao();
        SpatialReferenceSystem srs = srsDao.getOrCreateCode(ProjectionConstants.AUTHORITY_EPSG,
                ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

        gpsGpkg.createGeometryColumnsTable();

        createObservationTable(srs);
        String bbSql = "UPDATE gpkg_contents SET min_x = 180.0, max_x = -180.0, min_y = 90.0, max_y = -90.0 WHERE table_name = '" + POINTS_TABLE_NAME + "';";
        gpsGpkg.execSQL(bbSql);

        Contents contents = new Contents();
        RelatedTablesExtension rte = new RelatedTablesExtension(gpsGpkg);

        createSatelliteTable(contents, rte, srs);
        createClockTable(contents, rte, srs);
    }

    /**
     * Opens the GeoPackage database with the provided name.
     *
     * @param databaseName the name of the database
     * @return The opened GeoPackage.
     * @throws GeoPackageException if the database doesn't exist.
     */
    private GeoPackage openDatabase(String databaseName) throws GeoPackageException
    {
        GeoPackage gpkg = gpkgManager.open(databaseName, true);

        // Method will return null if database doesn't exist.
        if (gpkg == null)
        {
            throw new GeoPackageException("Can't open non-existent GeoPackage database " + databaseName);
        }

        return gpkg;
    }

    /**
     * Creates the points observation table, which is the main feature table from the GeoPackage
     * database.
     *
     * @param srs The spatial reference system
     * @throws SQLException if there is a creation error
     */
    private void createObservationTable(SpatialReferenceSystem srs) throws SQLException
    {
        ContentsDao contentsDao = gpsGpkg.getContentsDao();

        Contents contents = new Contents();
        contents.setTableName(POINTS_TABLE_NAME);
        contents.setDataType(ContentsDataType.FEATURES);
        contents.setIdentifier(POINTS_TABLE_NAME);
        contents.setDescription(POINTS_TABLE_NAME);
        contents.setSrs(srs);

        int colNum = 0;
        List<FeatureColumn> tblcols = new LinkedList<>();
        tblcols.add(FeatureColumn.createPrimaryKeyColumn(colNum++, ID_COLUMN));
        tblcols.add(FeatureColumn.createGeometryColumn(colNum++, GEOMETRY_COLUMN, GeometryType.POINT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_SYS_TIME, DATETIME, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_LAT, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_LNG, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_ALT, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_PROVIDER, TEXT, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_GPS_TIME, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_FIX_SAT_COUNT, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_HAS_RADIAL_ACCURACY, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_HAS_VERTICAL_ACCURACY, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_RADIAL_ACCURACY, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_VERTICAL_ACCURACY, REAL, false, null));

        // EW risk probability estimates
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_PROB_RFI, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_PROB_CN0AGC, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_PROB_CONSTELLATION, REAL, false, null));

        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_ELAPSED_REALTIME_NANOS, REAL, false, null));

        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_HAS_SPEED, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_HAS_SPEED_ACCURACY, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_SPEED, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_SPEED_ACCURACY, REAL, false, null));

        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_HAS_BEARING, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_HAS_BEARING_ACCURACY, INTEGER, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_BEARING, REAL, false, null));
        tblcols.add(FeatureColumn.createColumn(colNum++, GPS_OBS_PT_BEARING_ACCURACY, REAL, false, null));

        tblcols.add(FeatureColumn.createColumn(colNum, DATA_DUMP, TEXT, false, null));

        FeatureTable table = new FeatureTable(POINTS_TABLE_NAME, tblcols);
        gpsGpkg.createFeatureTable(table);

        contentsDao.create(contents);

        GeometryColumnsDao geometryColumnsDao = gpsGpkg.getGeometryColumnsDao();

        GeometryColumns geometryColumns = new GeometryColumns();
        geometryColumns.setContents(contents);
        geometryColumns.setColumnName(GEOMETRY_COLUMN);
        geometryColumns.setGeometryType(GeometryType.POINT);
        geometryColumns.setSrs(srs);
        geometryColumns.setZ((byte) 0);
        geometryColumns.setM((byte) 0);
        geometryColumnsDao.create(geometryColumns);
    }

    /**
     * Creates the satellite data table and initializes the {@link #satDao} and {@link #satMapDao}
     * fields, which can be used to add data to the table.
     *
     * @param contents The database contents
     * @param rte      The related tables extension
     * @param srs      The spatial reference system
     */
    private void createSatelliteTable(Contents contents, RelatedTablesExtension rte, SpatialReferenceSystem srs)
    {
        contents.setTableName(SAT_TABLE_NAME);
        contents.setDataType(ContentsDataType.FEATURES);
        contents.setIdentifier(SAT_TABLE_NAME);
        contents.setDescription(SAT_TABLE_NAME);
        contents.setSrs(srs);

        int colNum = 1;
        List<UserCustomColumn> tblcols = new LinkedList<>();

        // android GNSS measurements
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_MEASURED_TIME, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_SVID, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_CONSTELLATION, TEXT, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_CN0, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_AGC, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_HAS_AGC, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_IN_FIX, INTEGER, true, null));

        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_SYNC_STATE_FLAGS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_SYNC_STATE_TXT, TEXT, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_SAT_TIME_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_SAT_TIME_1_SIGMA_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_RCVR_TIME_OFFSET_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_MULTIPATH, INTEGER, true, null));

        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_HAS_CARRIER_FREQ, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_CARRIER_FREQ_HZ, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_ACCUM_DELTA_RANGE, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_ACCUM_DELTA_RANGE_1_SIGMA, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_ACCUM_DELTA_RANGE_STATE_FLAGS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_ACCUM_DELTA_RANGE_STATE_TXT, TEXT, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_PSEUDORANGE_RATE_MPS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_PSEUDORANGE_RATE_1_SIGMA, REAL, true, null));

        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_HAS_EPHEMERIS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_HAS_ALMANAC, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_AZIMUTH_DEG, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, SAT_DATA_ELEVATION_DEG, REAL, true, null));

        tblcols.add(UserCustomColumn.createColumn(colNum, DATA_DUMP, TEXT, true, null));

        SimpleAttributesTable table = SimpleAttributesTable.create(SAT_TABLE_NAME, tblcols);

        UserMappingTable mappingTable = UserMappingTable.create(SAT_MAP_TABLE_NAME);

        ExtendedRelation satExtRel = rte.addSimpleAttributesRelationship(POINTS_TABLE_NAME, table, mappingTable);
        satDao = rte.getSimpleAttributesDao(SAT_TABLE_NAME);
        satMapDao = rte.getMappingDao(satExtRel);
    }

    /**
     * Creates the clock table and initializes the {@link #clkDao} and {@link #clkMapDao}
     * fields, which can be used to add data to the table.
     *
     * @param contents The database contents
     * @param rte      The related tables extension
     * @param srs      The spatial reference system
     */
    private void createClockTable(Contents contents, RelatedTablesExtension rte, SpatialReferenceSystem srs)
    {
        contents.setTableName(CLOCK_TABLE_NAME);
        contents.setDataType(ContentsDataType.FEATURES);
        contents.setIdentifier(CLOCK_TABLE_NAME);
        contents.setDescription(CLOCK_TABLE_NAME);
        contents.setSrs(srs);

        int colNum = 1;
        List<UserCustomColumn> tblcols = new LinkedList<>();

        // android GNSS measurements
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_TIME_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_TIME_UNCERTAINTY_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_TIME_UNCERTAINTY_NANOS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_BIAS_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_BIAS_NANOS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_BIAS_UNCERTAINTY_NANOS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_BIAS_UNCERTAINTY_NANOS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_FULL_BIAS_NANOS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_FULL_BIAS_NANOS, INTEGER, true, null));

        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_DRIFT_NANOS_PER_SEC, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_DRIFT_NANOS_PER_SEC, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_DRIFT_UNCERTAINTY_NPS, REAL, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_DRIFT_UNCERTAINTY_NPS, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HW_CLOCK_DISCONTINUITY_COUNT, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_LEAP_SECOND, INTEGER, true, null));
        tblcols.add(UserCustomColumn.createColumn(colNum++, CLK_HAS_LEAP_SECOND, INTEGER, true, null));

        tblcols.add(UserCustomColumn.createColumn(colNum, DATA_DUMP, TEXT, true, null));

        SimpleAttributesTable table = SimpleAttributesTable.create(CLOCK_TABLE_NAME, tblcols);

        UserMappingTable mappingTable = UserMappingTable.create(CLOCK_MAP_TABLE_NAME);

        ExtendedRelation clkExtRel = rte.addSimpleAttributesRelationship(SAT_TABLE_NAME, table, mappingTable);
        clkDao = rte.getSimpleAttributesDao(CLOCK_TABLE_NAME);
        clkMapDao = rte.getMappingDao(clkExtRel);
    }

    /**
     * Writes the provide GNSS measurement event data to the database. Note: this method and any
     * other method that accesses the GeoPackage is synchronized to ensure the database doesn't get
     * closed while it is being updated.
     *
     * @param event The event providing the GNSS measurement data
     */
    synchronized void writeGnssMeasurements(final GnssMeasurementsEvent event)
    {
        try
        {
            Collection<GnssMeasurement> gnssMeasurements = event.getMeasurements();

            GnssClock clk = event.getClock();

            SimpleAttributesRow clkRow = clkDao.newRow();

            clkRow.setValue(CLK_TIME_NANOS, (double) clk.getTimeNanos());

            boolean hasTimeUncertaintyNanos = clk.hasTimeUncertaintyNanos();
            clkRow.setValue(CLK_TIME_UNCERTAINTY_NANOS, hasTimeUncertaintyNanos ? clk.getTimeUncertaintyNanos() : 0d);
            clkRow.setValue(CLK_HAS_TIME_UNCERTAINTY_NANOS, hasTimeUncertaintyNanos ? 1 : 0);

            boolean hasBiasNanos = clk.hasBiasNanos();
            clkRow.setValue(CLK_BIAS_NANOS, hasBiasNanos ? clk.getBiasNanos() : 0d);
            clkRow.setValue(CLK_HAS_BIAS_NANOS, hasBiasNanos ? 1 : 0);

            boolean hasFullBiasNanos = clk.hasFullBiasNanos();
            clkRow.setValue(CLK_FULL_BIAS_NANOS, hasFullBiasNanos ? clk.getFullBiasNanos() : 0L);
            clkRow.setValue(CLK_HAS_FULL_BIAS_NANOS, hasFullBiasNanos ? 1 : 0);

            boolean hasBiasUncertaintyNanos = clk.hasBiasUncertaintyNanos();
            clkRow.setValue(CLK_BIAS_UNCERTAINTY_NANOS, hasBiasUncertaintyNanos ? clk.getBiasUncertaintyNanos() : 0d);
            clkRow.setValue(CLK_HAS_BIAS_UNCERTAINTY_NANOS, hasBiasUncertaintyNanos ? 1 : 0);

            boolean hasDriftNanosPerSecond = clk.hasDriftNanosPerSecond();
            clkRow.setValue(CLK_DRIFT_NANOS_PER_SEC, hasDriftNanosPerSecond ? clk.getDriftNanosPerSecond() : 0d);
            clkRow.setValue(CLK_HAS_DRIFT_NANOS_PER_SEC, hasDriftNanosPerSecond ? 1 : 0);

            boolean hasDriftUncertaintyNanosPerSecond = clk.hasDriftUncertaintyNanosPerSecond();
            clkRow.setValue(CLK_DRIFT_UNCERTAINTY_NPS, hasDriftUncertaintyNanosPerSecond ? clk.getDriftUncertaintyNanosPerSecond() : 0d);
            clkRow.setValue(CLK_HAS_DRIFT_UNCERTAINTY_NPS, hasDriftUncertaintyNanosPerSecond ? 1 : 0);

            boolean hasLeapSecond = clk.hasLeapSecond();
            clkRow.setValue(CLK_LEAP_SECOND, hasLeapSecond ? clk.getLeapSecond() : 0);
            clkRow.setValue(CLK_HAS_LEAP_SECOND, hasLeapSecond ? 1 : 0);

            clkRow.setValue(CLK_HW_CLOCK_DISCONTINUITY_COUNT, clk.getHardwareClockDiscontinuityCount());

            clkRow.setValue(DATA_DUMP, clk.toString());

            clkDao.insert(clkRow);

            synchronized (this)
            {
                // TODO KMB: I'm not sure I understand why we are clearing the map here...
                satRowsToMap.clear();
            }

            final long time = System.currentTimeMillis();
            for (final GnssMeasurement gnssMeasurement : gnssMeasurements)
            {
                String constellation = GpsTestUtil.getGnssConstellationType(gnssMeasurement.getConstellationType()).name();
                String satelliteId = constellation + gnssMeasurement.getSvid();

                SimpleAttributesRow satRow = satDao.newRow();

                satRow.setValue(SAT_DATA_MEASURED_TIME, time);
                satRow.setValue(SAT_DATA_SVID, gnssMeasurement.getSvid());
                satRow.setValue(SAT_DATA_CONSTELLATION, constellation);
                satRow.setValue(SAT_DATA_CN0, gnssMeasurement.getCn0DbHz());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                {
                    boolean hasAutomaticGainControlLevelDb = gnssMeasurement.hasAutomaticGainControlLevelDb();
                    satRow.setValue(SAT_DATA_AGC, hasAutomaticGainControlLevelDb ? gnssMeasurement.getAutomaticGainControlLevelDb() : 0d);
                    satRow.setValue(SAT_DATA_HAS_AGC, hasAutomaticGainControlLevelDb ? 1 : 0);
                } else
                {
                    satRow.setValue(SAT_DATA_AGC, 0d);
                    satRow.setValue(SAT_DATA_HAS_AGC, 0);
                }

                satRow.setValue(SAT_DATA_SYNC_STATE_FLAGS, gnssMeasurement.getState());
                satRow.setValue(SAT_DATA_SYNC_STATE_TXT, " ");
                satRow.setValue(SAT_DATA_SAT_TIME_NANOS, (double) gnssMeasurement.getReceivedSvTimeNanos());
                satRow.setValue(SAT_DATA_SAT_TIME_1_SIGMA_NANOS, (double) gnssMeasurement.getReceivedSvTimeUncertaintyNanos());
                satRow.setValue(SAT_DATA_RCVR_TIME_OFFSET_NANOS, gnssMeasurement.getTimeOffsetNanos());
                satRow.setValue(SAT_DATA_MULTIPATH, gnssMeasurement.getMultipathIndicator());

                boolean hasCarrierFrequencyHz = gnssMeasurement.hasCarrierFrequencyHz();
                satRow.setValue(SAT_DATA_CARRIER_FREQ_HZ, hasCarrierFrequencyHz ? (double) gnssMeasurement.getCarrierFrequencyHz() : 0d);
                satRow.setValue(SAT_DATA_HAS_CARRIER_FREQ, hasCarrierFrequencyHz ? 1 : 0);

                satRow.setValue(SAT_DATA_ACCUM_DELTA_RANGE, gnssMeasurement.getAccumulatedDeltaRangeMeters());
                satRow.setValue(SAT_DATA_ACCUM_DELTA_RANGE_1_SIGMA, gnssMeasurement.getAccumulatedDeltaRangeUncertaintyMeters());
                satRow.setValue(SAT_DATA_ACCUM_DELTA_RANGE_STATE_FLAGS, gnssMeasurement.getAccumulatedDeltaRangeState());
                satRow.setValue(SAT_DATA_ACCUM_DELTA_RANGE_STATE_TXT, " ");
                satRow.setValue(SAT_DATA_PSEUDORANGE_RATE_MPS, gnssMeasurement.getPseudorangeRateMetersPerSecond());
                satRow.setValue(SAT_DATA_PSEUDORANGE_RATE_1_SIGMA, gnssMeasurement.getPseudorangeRateUncertaintyMetersPerSecond());

                SatelliteStatus satelliteStatus = satStatus.get(satelliteId);
                boolean satStatusNotNull = satelliteStatus != null;
                satRow.setValue(SAT_DATA_IN_FIX, satStatusNotNull && satelliteStatus.getUsedInFix() ? 1 : 0);
                satRow.setValue(SAT_DATA_HAS_ALMANAC, satStatusNotNull && satelliteStatus.getHasAlmanac() ? 1 : 0);
                satRow.setValue(SAT_DATA_HAS_EPHEMERIS, satStatusNotNull && satelliteStatus.getHasEphemeris() ? 1 : 0);
                satRow.setValue(SAT_DATA_HAS_CARRIER_FREQ, satStatusNotNull && satelliteStatus.getHasCarrierFrequency() ? 1 : 0);

                satRow.setValue(SAT_DATA_ELEVATION_DEG, satStatusNotNull ? (double) satelliteStatus.getElevationDegrees() : 0.0d);
                satRow.setValue(SAT_DATA_AZIMUTH_DEG, satStatusNotNull ? (double) satelliteStatus.getAzimuthDegrees() : 0.0d);

                satRow.setValue(DATA_DUMP, gnssMeasurement.toString());
                satDao.insert(satRow);

                UserMappingRow clkMapRow = clkMapDao.newRow();
                clkMapRow.setBaseId(satRow.getId());
                clkMapRow.setRelatedId(clkRow.getId());
                clkMapDao.create(clkMapRow);

                synchronized (this)
                {
                    satRowsToMap.put(satelliteId, satRow.getId());
                }
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error adding GNSS measurements to GeoPackage", e);
        }
    }

    /**
     * Writes the provided location data to the database. Note: this method and any other method
     * that accesses the GeoPackage is synchronized to ensure the database doesn't get closed while
     * it is being updated.
     *
     * @param location The updated location
     */
    synchronized void writeLocation(final Location location)
    {
        try
        {
            // TODO KMB: I think we might have this backwards... we should probably create
            //  this mapping when the GNSS data is received using the current location at
            //  that point. By doing it this way, it seems like we are actually associating
            //  the GNSS data with a future location instead of the actual location when the
            //  GNSS data was observed.
            HashMap<String, Long> mapRows;
            synchronized (this)
            {
                // Save a copy of the sat data rows map so we can populate the mapping table
                // with mappings to the appropriate row in the points table
                mapRows = satRowsToMap;
                satRowsToMap = new HashMap<>();
            }

            if (gpsGpkg != null)
            {
                FeatureDao featDao = gpsGpkg.getFeatureDao(POINTS_TABLE_NAME);
                FeatureRow featureRow = featDao.newRow();

                Point fix = new Point(location.getLongitude(), location.getLatitude(), location.getAltitude());

                GeoPackageGeometryData geomData = new GeoPackageGeometryData(WGS84_SRS);
                geomData.setGeometry(fix);

                featureRow.setGeometry(geomData);

                featureRow.setValue(GPS_OBS_PT_LAT, location.getLatitude());
                featureRow.setValue(GPS_OBS_PT_LNG, location.getLongitude());
                featureRow.setValue(GPS_OBS_PT_ALT, location.getAltitude());
                featureRow.setValue(GPS_OBS_PT_PROVIDER, location.getProvider());
                featureRow.setValue(GPS_OBS_PT_GPS_TIME, location.getTime());
                featureRow.setValue(GPS_OBS_PT_FIX_SAT_COUNT, location.getExtras().getInt("satellites"));

                boolean hasAccuracy = location.hasAccuracy();
                featureRow.setValue(GPS_OBS_PT_RADIAL_ACCURACY, hasAccuracy ? (double) location.getAccuracy() : 0d);
                featureRow.setValue(GPS_OBS_PT_HAS_RADIAL_ACCURACY, hasAccuracy ? 1 : 0);

                boolean hasSpeed = location.hasSpeed();
                featureRow.setValue(GPS_OBS_PT_SPEED, hasSpeed ? (double) location.getAccuracy() : 0d);
                featureRow.setValue(GPS_OBS_PT_HAS_SPEED, hasSpeed ? 1 : 0);

                boolean hasBearing = location.hasBearing();
                featureRow.setValue(GPS_OBS_PT_BEARING, hasBearing ? (double) location.getAccuracy() : 0d);
                featureRow.setValue(GPS_OBS_PT_HAS_BEARING, hasBearing ? 1 : 0);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                {
                    featureRow.setValue(GPS_OBS_PT_SYS_TIME, now().toString());

                    boolean hasVerticalAccuracy = location.hasVerticalAccuracy();
                    featureRow.setValue(GPS_OBS_PT_VERTICAL_ACCURACY, hasVerticalAccuracy ? (double) location.getVerticalAccuracyMeters() : 0d);
                    featureRow.setValue(GPS_OBS_PT_HAS_VERTICAL_ACCURACY, hasVerticalAccuracy ? 1 : 0);

                    boolean hasSpeedAccuracy = location.hasSpeedAccuracy();
                    featureRow.setValue(GPS_OBS_PT_SPEED_ACCURACY, hasSpeedAccuracy ? (double) location.getAccuracy() : 0d);
                    featureRow.setValue(GPS_OBS_PT_HAS_SPEED_ACCURACY, hasSpeedAccuracy ? 1 : 0);

                    boolean hasBearingAccuracy = location.hasBearingAccuracy();
                    featureRow.setValue(GPS_OBS_PT_BEARING_ACCURACY, hasBearingAccuracy ? (double) location.getAccuracy() : 0d);
                    featureRow.setValue(GPS_OBS_PT_HAS_BEARING_ACCURACY, hasBearingAccuracy ? 1 : 0);
                } else
                {
                    Date currentTime = Calendar.getInstance().getTime();
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
                    featureRow.setValue(GPS_OBS_PT_SYS_TIME, df.format(currentTime));
                    featureRow.setValue(GPS_OBS_PT_HAS_VERTICAL_ACCURACY, 0);
                    featureRow.setValue(GPS_OBS_PT_VERTICAL_ACCURACY, 0d);
                }

                featureRow.setValue(DATA_DUMP, location.toString() + " " + location.describeContents());

                //EW risk values
                featureRow.setValue(GPS_OBS_PT_PROB_RFI, -1d);
                featureRow.setValue(GPS_OBS_PT_PROB_CN0AGC, -1d);
                featureRow.setValue(GPS_OBS_PT_PROB_CONSTELLATION, -1d);

                featDao.insert(featureRow);

                for (long id : mapRows.values())
                {
                    UserMappingRow satMapRow = satMapDao.newRow();
                    satMapRow.setBaseId(featureRow.getId());
                    satMapRow.setRelatedId(id);
                    satMapDao.create(satMapRow);
                }

                // update feature table bounding box if necessary
                boolean dirty = false;
                BoundingBox bb = featDao.getBoundingBox();
                if (location.getLatitude() < bb.getMinLatitude())
                {
                    bb.setMinLatitude(location.getLatitude());
                    dirty = true;
                }

                if (location.getLatitude() > bb.getMaxLatitude())
                {
                    bb.setMaxLatitude(location.getLatitude());
                    dirty = true;
                }

                if (location.getLongitude() < bb.getMinLongitude())
                {
                    bb.setMinLongitude(location.getLongitude());
                }

                if (location.getLongitude() > bb.getMaxLongitude())
                {
                    bb.setMaxLongitude(location.getLongitude());
                }

                if (dirty)
                {
                    String bbSql = "UPDATE gpkg_contents SET " +
                            " min_x = " + bb.getMinLongitude() +
                            ", max_x = " + bb.getMaxLongitude() +
                            ", min_y = " + bb.getMinLatitude() +
                            ", max_y = " + bb.getMaxLatitude() +
                            " WHERE table_name = '" + POINTS_TABLE_NAME + "';";
                    gpsGpkg.execSQL(bbSql);
                }
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error adding location change to GeoPackage", e);
        }
    }

    /**
     * Writes the provided GNSS status to the database. Note: this method and any other method that
     * accesses the GeoPackage is synchronized to ensure the database doesn't get closed while it is
     * being updated.
     *
     * @param status The updated GNSS status
     */
    synchronized void writeSatelliteStatus(final GnssStatus status)
    {
        try
        {
            int numSats = status.getSatelliteCount();

            for (int i = 0; i < numSats; ++i)
            {

                SatelliteStatus satelliteStatus = new SatelliteStatus(status.getSvid(i),
                        GpsTestUtil.getGnssConstellationType(status.getConstellationType(i)),
                        status.getCn0DbHz(i),
                        status.hasAlmanacData(i),
                        status.hasEphemerisData(i),
                        status.usedInFix(i),
                        status.getElevationDegrees(i),
                        status.getAzimuthDegrees(i));
                if (GpsTestUtil.isGnssCarrierFrequenciesSupported())
                {
                    if (status.hasCarrierFrequencyHz(i))
                    {
                        satelliteStatus.setHasCarrierFrequency(true);
                        satelliteStatus.setCarrierFrequencyHz(status.getCarrierFrequencyHz(i));
                    }
                }

                String satelliteId = satelliteStatus.getGnssType().name() + status.getSvid(i);
                satStatus.put(satelliteId, satelliteStatus);
            }
        } catch (Exception e)
        {
            Log.e(TAG, "Error adding satellite status change to GeoPackage", e);
        }
    }

    /**
     * Shuts down the GeoPackage database. Note: this method and any other method that accesses the
     * GeoPackage is synchronized to ensure the database doesn't get closed while it is being
     * updated.
     */
    synchronized void shutdown()
    {
        if (gpsGpkg != null)
        {
            gpsGpkg.close();
            gpsGpkg = null;
        }
    }
}
