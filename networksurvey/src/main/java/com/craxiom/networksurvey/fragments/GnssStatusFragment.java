/*
 * Copyright (C) 2008-2018 The Android Open Source Project,
 * Sean J. Barbeau (sjbarbeau@gmail.com)
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

package com.craxiom.networksurvey.fragments;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static com.craxiom.networksurvey.model.ConstellationType.GNSS;
import static com.craxiom.networksurvey.model.ConstellationType.SBAS;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.listeners.IGnssListener;
import com.craxiom.networksurvey.model.ConstellationType;
import com.craxiom.networksurvey.model.DilutionOfPrecision;
import com.craxiom.networksurvey.model.GnssMeasurementWrapper;
import com.craxiom.networksurvey.model.GnssType;
import com.craxiom.networksurvey.model.SatelliteStatus;
import com.craxiom.networksurvey.util.CarrierFreqUtils;
import com.craxiom.networksurvey.util.GpsTestUtil;
import com.craxiom.networksurvey.util.IOUtils;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.NmeaUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.craxiom.networksurvey.util.SortUtil;
import com.craxiom.networksurvey.util.UIUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import timber.log.Timber;

/**
 * A fragment for displaying the latest GNSS information to the user.
 * <p>
 * This interface is originally from the GPS Test open source Android app and has been adapted to meet the needs of
 * this Network Survey app.
 * https://github.com/barbeau/gpstest/blob/master/GPSTest/src/main/java/com/android/gpstest/GpsStatusFragment.java
 */
public class GnssStatusFragment extends Fragment implements IGnssListener
{
    static final String TITLE = "Details";

    private static final String EMPTY_LAT_LONG = "             ";

    /*TODO We either need to fix everything to a specific unit of measurement, or update the Settings UI to allow the user to control it.
    private static final String METERS = Application.get().getResources().getStringArray(R.array.preferred_distance_units_values)[0];
    private static final String METERS_PER_SECOND = Application.get().getResources().getStringArray(R.array.preferred_speed_units_values)[0];
    private static final String KILOMETERS_PER_HOUR = Application.get().getResources().getStringArray(R.array.preferred_speed_units_values)[1];*/

    private static final String METERS = "1";
    private static final String METERS_PER_SECOND = "1";
    private static final String KILOMETERS_PER_HOUR = "2";

    private final Object lock = new Object();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private SimpleDateFormat dateFormat;

    private Resources resources;

    private TextView latitudeView, longitudeView, fixTimeView, ttffView, altitudeView,
            altitudeMslView, horVertAccuracyLabelView, horVertAccuracyView,
            speedView, speedAccuracyView, bearingView, bearingAccuracyView, numSats,
            pdopLabelView, pdopView, hvdopLabelView, hvdopView, gnssNotAvailableView,
            sbasNotAvailableView;

    private Location latestLocation;

    private TableRow speedBearingAccuracyRow;

    private RecyclerView gnssStatusList;
    private RecyclerView sbasStatusList;

    private SatelliteStatusAdapter gnssAdapter;
    private SatelliteStatusAdapter sbasAdapter;

    private List<SatelliteStatus> gnssStatus = new ArrayList<>();

    private List<SatelliteStatus> sbasStatus = new ArrayList<>();

    private int svCount;

    private String snrCn0Title;
    private String agcTitle;

    private long fixTime;

    private boolean navigating;

    private Drawable flagUsa;
    private Drawable flagRussia;
    private Drawable flagJapan;
    private Drawable flagChina;
    private Drawable flagIndia;
    private Drawable flagEu;
    private Drawable flagIcao;

    private String ttff = "";

    private String prefDistanceUnits;
    private String prefSpeedUnits;
    private MainGnssFragment mainGnssFragment;

    /**
     * Constructs this fragment.
     */
    public GnssStatusFragment()
    {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mainGnssFragment = (MainGnssFragment) getParentFragment();

        dateFormat = new SimpleDateFormat(DateFormat.is24HourFormat(getContext()) ? "HH:mm:ss" : "hh:mm:ss a", Locale.getDefault());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        resources = getResources();
        setupUnitPreferences();

        View v = inflater.inflate(R.layout.gps_status, container, false);

        latitudeView = v.findViewById(R.id.latitude);
        longitudeView = v.findViewById(R.id.longitude);
        fixTimeView = v.findViewById(R.id.fix_time);
        ttffView = v.findViewById(R.id.ttff);
        altitudeView = v.findViewById(R.id.altitude);
        altitudeMslView = v.findViewById(R.id.altitude_msl);
        horVertAccuracyLabelView = v.findViewById(R.id.hor_vert_accuracy_label);
        horVertAccuracyView = v.findViewById(R.id.hor_vert_accuracy);
        speedView = v.findViewById(R.id.speed);
        speedAccuracyView = v.findViewById(R.id.speed_acc);
        bearingView = v.findViewById(R.id.bearing);
        bearingAccuracyView = v.findViewById(R.id.bearing_acc);
        numSats = v.findViewById(R.id.num_sats);
        pdopLabelView = v.findViewById(R.id.pdop_label);
        pdopView = v.findViewById(R.id.pdop);
        hvdopLabelView = v.findViewById(R.id.hvdop_label);
        hvdopView = v.findViewById(R.id.hvdop);

        speedBearingAccuracyRow = v.findViewById(R.id.speed_bearing_acc_row);

        gnssNotAvailableView = v.findViewById(R.id.gnss_not_available);
        sbasNotAvailableView = v.findViewById(R.id.sbas_not_available);

        latitudeView.setText(EMPTY_LAT_LONG);
        longitudeView.setText(EMPTY_LAT_LONG);

        final Context context = getContext();
        Resources.Theme theme = null;
        if (context != null) theme = context.getTheme();

        flagUsa = resources.getDrawable(R.drawable.ic_flag_usa, theme);
        flagRussia = resources.getDrawable(R.drawable.ic_flag_russia, theme);
        flagJapan = resources.getDrawable(R.drawable.ic_flag_japan, theme);
        flagChina = resources.getDrawable(R.drawable.ic_flag_china, theme);
        flagIndia = resources.getDrawable(R.drawable.ic_flag_india, theme);
        flagEu = resources.getDrawable(R.drawable.ic_flag_european_union, theme);
        flagIcao = resources.getDrawable(R.drawable.ic_flag_icao, theme);

        v.findViewById(R.id.status_location_card).setOnClickListener(view -> {
            // Copy location to clipboard
            if (latestLocation != null)
            {
                boolean includeAltitude = Application.getPrefs().getBoolean(Application.get().getString(R.string.pref_key_share_include_altitude), false);
                String coordinateFormat = Application.getPrefs().getString(Application.get().getString(R.string.pref_key_coordinate_format), Application.get().getString(R.string.preferences_coordinate_format_dd_key));
                String formattedLocation = UIUtils.formatLocationForDisplay(latestLocation, null, includeAltitude,
                        null, null, null, coordinateFormat);
                IOUtils.copyToClipboard(formattedLocation);
                Toast.makeText(getActivity(), R.string.copied_to_clipboard, Toast.LENGTH_LONG).show();
            }
        });

        // GNSS
        LinearLayoutManager llmGnss = new LinearLayoutManager(getContext());
        llmGnss.setOrientation(RecyclerView.VERTICAL);

        gnssStatusList = v.findViewById(R.id.gnss_status_list);
        gnssAdapter = new SatelliteStatusAdapter(GNSS);
        gnssStatusList.setAdapter(gnssAdapter);
        gnssStatusList.setFocusable(false);
        gnssStatusList.setFocusableInTouchMode(false);
        gnssStatusList.setLayoutManager(llmGnss);
        gnssStatusList.setNestedScrollingEnabled(false);

        // SBAS
        LinearLayoutManager llmSbas = new LinearLayoutManager(getContext());
        llmSbas.setOrientation(RecyclerView.VERTICAL);

        sbasStatusList = v.findViewById(R.id.sbas_status_list);
        sbasAdapter = new SatelliteStatusAdapter(SBAS);
        sbasStatusList.setAdapter(sbasAdapter);
        sbasStatusList.setFocusable(false);
        sbasStatusList.setFocusableInTouchMode(false);
        sbasStatusList.setLayoutManager(llmSbas);
        sbasStatusList.setNestedScrollingEnabled(false);


        Consumer<RecyclerView.ItemAnimator> disableRefreshAnimation = a -> {
            if (a instanceof SimpleItemAnimator)
            {
                ((SimpleItemAnimator) a).setSupportsChangeAnimations(false);
            }
        };
        disableRefreshAnimation.accept(gnssStatusList.getItemAnimator());
        disableRefreshAnimation.accept(sbasStatusList.getItemAnimator());

        snrCn0Title = resources.getString(R.string.gps_cn0_column_label);
        agcTitle = resources.getString(R.string.gps_agc_column_label);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume()
    {
        Timber.d("Resuming the GNSS Status Fragment");

        super.onResume();

        mainGnssFragment.registerGnssListener(this);

        setupUnitPreferences();
    }

    @Override
    public void onPause()
    {
        Timber.d("Pausing the GNSS Status Fragment");

        mainGnssFragment.unregisterGnssListener(this);

        super.onPause();
    }

    /*TODO Should we add the menu for sorting?
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.status_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        final int id = item.getItemId();
        if (id == R.id.sort_sats)
        {
            showSortByDialog();
        }
        return false;
    }*/

    @Override
    public void onGnssFirstFix(int ttffMillis)
    {
        ttff = UIUtils.getTtffString(ttffMillis);
        if (ttffView != null)
        {
            ttffView.setText(ttff);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status)
    {
        updateGnssStatus(status);
    }

    @Override
    public void onGnssStarted()
    {
        setStarted(true);
    }

    @Override
    public void onGnssStopped()
    {
        setStarted(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
    {
        // updates statuses if there's a valid agc measurement corresponding to that statuses svid and constellation
        BiConsumer<List<SatelliteStatus>, SatelliteStatusAdapter> updateAgc = (statuses, adapter) -> {
                // start at 1 as 0th index is the column label header
                final int statusCount = statuses.size();
                for (int i = 0; i < statusCount; i++)
                {
                    SatelliteStatus s = statuses.get(i);
                    GnssMeasurementWrapper measurement = mainGnssFragment.getGnssMeasurement(s.getSvid(), s.getGnssType(), s.getCarrierFrequencyHz());
                    boolean isChange = false;
                    boolean isAgcValid = hasValidAgc(measurement);
                    // update ui if we have a valid agc and it's a different value OR agc was invalidated
                    if (isAgcValid && s.getAgc() != measurement.getAgc())
                    {
                        s.setAgc(measurement.getAgc());
                        isChange = true;
                    }
                    else if(!isAgcValid && s.getAgc() != SatelliteStatus.NO_DATA_DOUBLE)
                    {
                        s.setAgc(SatelliteStatus.NO_DATA_DOUBLE);
                        isChange = true;
                    }

                    if(isChange)
                    {
                        final int posChanged = i + 1;
                        uiHandler.post(() -> adapter.notifyItemChanged(posChanged)); // has to be i + 1 as 0 is adapter's header item
                    }
                }
        };

        // must sync here since updateGnssStatus clears/creates the SatelliteStatus records for our lists
        synchronized (lock)
        {
            updateAgc.accept(gnssStatus, gnssAdapter);
            updateAgc.accept(sbasStatus, sbasAdapter);
            uiHandler.post(this::updateListVisibility);
        }
    }

    @Override
    public void onNmeaMessage(String message, long timestamp)
    {
        if (!isAdded())
        {
            // Do nothing if the Fragment isn't added
            return;
        }
        if (message.startsWith("$GPGGA") || message.startsWith("$GNGNS") || message.startsWith("$GNGGA"))
        {
            Double altitudeMsl = NmeaUtils.getAltitudeMeanSeaLevel(message);
            if (altitudeMsl != null && navigating)
            {
                if (prefDistanceUnits.equalsIgnoreCase(METERS))
                {
                    altitudeMslView.setText(resources.getString(R.string.gps_altitude_msl_value_meters, altitudeMsl));
                } else
                {
                    altitudeMslView.setText(resources.getString(R.string.gps_altitude_msl_value_feet, UIUtils.toFeet(altitudeMsl)));
                }
            }
        }
        if (message.startsWith("$GNGSA") || message.startsWith("$GPGSA"))
        {
            DilutionOfPrecision dop = NmeaUtils.getDop(message);
            if (dop != null && navigating)
            {
                showDopViews();
                pdopView.setText(resources.getString(R.string.pdop_value, dop.getPositionDop()));
                hvdopView.setText(
                        resources.getString(R.string.hvdop_value, dop.getHorizontalDop(),
                                dop.getVerticalDop()));
            }
        }
    }

    @Override
    public void onLocationChanged(Location location)
    {
        if (!UIUtils.isFragmentAttached(this))
        {
            // Fragment isn't visible, so return to avoid IllegalStateException (see #85)
            return;
        }

        // Cache location for copy to clipboard operation
        latestLocation = location;

        // Make sure TTFF is shown, if the TTFF is acquired before the mTTFFView is initialized
        ttffView.setText(ttff);

        String coordinateFormat = Application.getPrefs().getString(getString(R.string.pref_key_coordinate_format), getString(R.string.preferences_coordinate_format_dd_key));
        switch (coordinateFormat)
        {
            // Constants below must match string values in do_not_translate.xml
            case "dms":
                // Degrees minutes seconds
                latitudeView.setText(UIUtils.getDMSFromLocation(Application.get(), location.getLatitude(), UIUtils.COORDINATE_LATITUDE));
                longitudeView.setText(UIUtils.getDMSFromLocation(Application.get(), location.getLongitude(), UIUtils.COORDINATE_LONGITUDE));
                break;
            case "ddm":
                // Degrees decimal minutes
                latitudeView.setText(UIUtils.getDDMFromLocation(Application.get(), location.getLatitude(), UIUtils.COORDINATE_LATITUDE));
                longitudeView.setText(UIUtils.getDDMFromLocation(Application.get(), location.getLongitude(), UIUtils.COORDINATE_LONGITUDE));
                break;
            default: // Assume "dd"
                // Decimal degrees
                latitudeView.setText(resources.getString(R.string.gps_latitude_value, location.getLatitude()));
                longitudeView.setText(resources.getString(R.string.gps_longitude_value, location.getLongitude()));
                break;
        }

        fixTime = location.getTime();

        if (location.hasAltitude())
        {
            if (prefDistanceUnits.equalsIgnoreCase(METERS))
            {
                altitudeView.setText(resources.getString(R.string.gps_altitude_value_meters, location.getAltitude()));
            } else
            {
                // Feet
                altitudeView.setText(resources.getString(R.string.gps_altitude_value_feet, UIUtils.toFeet(location.getAltitude())));
            }
        } else
        {
            altitudeView.setText("");
        }
        if (location.hasSpeed())
        {
            if (prefSpeedUnits.equalsIgnoreCase(METERS_PER_SECOND))
            {
                speedView.setText(resources.getString(R.string.gps_speed_value_meters_sec, location.getSpeed()));
            } else if (prefSpeedUnits.equalsIgnoreCase(KILOMETERS_PER_HOUR))
            {
                speedView.setText(resources.getString(R.string.gps_speed_value_kilometers_hour, UIUtils.toKilometersPerHour(location.getSpeed())));
            } else
            {
                // Miles per hour
                speedView.setText(resources.getString(R.string.gps_speed_value_miles_hour, UIUtils.toMilesPerHour(location.getSpeed())));
            }
        } else
        {
            speedView.setText("");
        }
        if (location.hasBearing())
        {
            bearingView.setText(resources.getString(R.string.gps_bearing_value, location.getBearing()));
        } else
        {
            bearingView.setText("");
        }
        updateLocationAccuracies(location);
        updateSpeedAndBearingAccuracies(location);
        updateFixTime();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }

    @Override
    public void onProviderEnabled(String provider)
    {
    }

    @Override
    public void onProviderDisabled(String provider)
    {
    }

    private void setStarted(boolean navigating)
    {
        if (navigating != this.navigating)
        {
            if (!navigating)
            {
                latitudeView.setText(EMPTY_LAT_LONG);
                longitudeView.setText(EMPTY_LAT_LONG);
                fixTime = 0;
                updateFixTime();
                ttffView.setText("");
                altitudeView.setText("");
                altitudeMslView.setText("");
                horVertAccuracyView.setText("");
                speedView.setText("");
                speedAccuracyView.setText("");
                bearingView.setText("");
                bearingAccuracyView.setText("");
                numSats.setText("");
                pdopView.setText("");
                hvdopView.setText("");

                svCount = 0;
                gnssStatus.clear();
                sbasStatus.clear();
                gnssAdapter.notifyDataSetChanged();
                sbasAdapter.notifyDataSetChanged();
            }
            this.navigating = navigating;
        }
    }

    private void updateFixTime()
    {
        fixTimeView.setText(fixTime == 0 ? "" : dateFormat.format(fixTime));
    }

    /**
     * Update views for horizontal and vertical location accuracies based on the provided location
     *
     * @param location The location to pull the accuracy from.
     */
    private void updateLocationAccuracies(Location location)
    {
        if (GpsTestUtil.isVerticalAccuracySupported(location))
        {
            horVertAccuracyLabelView.setText(R.string.gps_hor_and_vert_accuracy_label);
            if (prefDistanceUnits.equalsIgnoreCase(METERS))
            {
                horVertAccuracyView.setText(resources.getString(R.string.gps_hor_and_vert_accuracy_value_meters,
                        location.getAccuracy(),
                        location.getVerticalAccuracyMeters()));
            } else
            {
                // Feet
                horVertAccuracyView.setText(resources.getString(R.string.gps_hor_and_vert_accuracy_value_feet,
                        UIUtils.toFeet(location.getAccuracy()),
                        UIUtils.toFeet(location.getVerticalAccuracyMeters())));
            }
        } else
        {
            if (location.hasAccuracy())
            {
                if (prefDistanceUnits.equalsIgnoreCase(METERS))
                {
                    horVertAccuracyView.setText(resources.getString(R.string.gps_accuracy_value_meters, location.getAccuracy()));
                } else
                {
                    // Feet
                    horVertAccuracyView.setText(resources.getString(R.string.gps_accuracy_value_feet, UIUtils.toFeet(location.getAccuracy())));
                }
            } else
            {
                horVertAccuracyView.setText("");
            }
        }
    }

    /**
     * Update views for speed and bearing location accuracies based on the provided location
     *
     * @param location The location to pull the speed and bearing from.
     */
    private void updateSpeedAndBearingAccuracies(Location location)
    {
        if (GpsTestUtil.isSpeedAndBearingAccuracySupported())
        {
            speedBearingAccuracyRow.setVisibility(View.VISIBLE);
            if (location.hasSpeedAccuracy())
            {
                if (prefSpeedUnits.equalsIgnoreCase(METERS_PER_SECOND))
                {
                    speedAccuracyView.setText(resources.getString(R.string.gps_speed_acc_value_meters_sec, location.getSpeedAccuracyMetersPerSecond()));
                } else if (prefSpeedUnits.equalsIgnoreCase(KILOMETERS_PER_HOUR))
                {
                    speedAccuracyView.setText(resources.getString(R.string.gps_speed_acc_value_km_hour, UIUtils.toKilometersPerHour(location.getSpeedAccuracyMetersPerSecond())));
                } else
                {
                    // Miles per hour
                    speedAccuracyView.setText(resources.getString(R.string.gps_speed_acc_value_miles_hour, UIUtils.toMilesPerHour(location.getSpeedAccuracyMetersPerSecond())));
                }
            } else
            {
                speedAccuracyView.setText("");
            }
            if (location.hasBearingAccuracy())
            {
                bearingAccuracyView.setText(resources.getString(R.string.gps_bearing_acc_value, location.getBearingAccuracyDegrees()));
            } else
            {
                bearingAccuracyView.setText("");
            }
        } else
        {
            speedBearingAccuracyRow.setVisibility(View.GONE);
        }
    }

    private void showDopViews()
    {
        pdopLabelView.setVisibility(View.VISIBLE);
        pdopView.setVisibility(View.VISIBLE);
        hvdopLabelView.setVisibility(View.VISIBLE);
        hvdopView.setVisibility(View.VISIBLE);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateGnssStatus(GnssStatus status)
    {
        setStarted(true);
        updateFixTime();

        if (!UIUtils.isFragmentAttached(this))
        {
            // Fragment isn't visible, so return to avoid IllegalStateException (see #85)
            return;
        }

        final int length = status.getSatelliteCount();
        svCount = 0;
        int usedInFixCount = 0;

        // must sync as onGnssMeasurementsReceived modifies the adapter records
        synchronized (lock)
        {
            gnssStatus.clear();
            sbasStatus.clear();
            while (svCount < length)
            {
                GnssType gnssType = GpsTestUtil.getGnssConstellationType(status.getConstellationType(svCount));
                SatelliteStatus satStatus = new SatelliteStatus(status.getSvid(svCount), gnssType,
                        status.getCn0DbHz(svCount),
                        status.hasAlmanacData(svCount),
                        status.hasEphemerisData(svCount),
                        status.usedInFix(svCount),
                        status.getElevationDegrees(svCount),
                        status.getAzimuthDegrees(svCount));

                // update agc if we have it and it's not timed out
                GnssMeasurementWrapper measurement = mainGnssFragment.getGnssMeasurement(satStatus.getSvid(), gnssType, satStatus.getCarrierFrequencyHz());
                satStatus.setAgc(hasValidAgc(measurement)
                        ? measurement.getAgc()
                        : SatelliteStatus.NO_DATA_DOUBLE);

                if (GpsTestUtil.isGnssCarrierFrequenciesSupported())
                {
                    if (status.hasCarrierFrequencyHz(svCount))
                    {
                        satStatus.setHasCarrierFrequency(true);
                        satStatus.setCarrierFrequencyHz(status.getCarrierFrequencyHz(svCount));
                    }
                }

                if (satStatus.getGnssType() == GnssType.SBAS)
                {
                    satStatus.setSbasType(GpsTestUtil.getSbasConstellationType(satStatus.getSvid()));
                    sbasStatus.add(satStatus);
                } else
                {
                    gnssStatus.add(satStatus);
                }

                if (satStatus.getUsedInFix())
                {
                    usedInFixCount++;
                }

                svCount++;
            }

            numSats.setText(resources.getString(R.string.gps_num_sats_value, usedInFixCount, svCount));

            refreshViews();
        }
    }

    private void refreshViews()
    {
        sortLists();

        updateListVisibility();
        gnssAdapter.notifyDataSetChanged();
        sbasAdapter.notifyDataSetChanged();
    }

    private void sortLists()
    {
        final int sortBy = PreferenceUtils.getSatSortOrderFromPreferences();
        // Below switch statement order must match arrays.xml sort_sats order
        switch (sortBy)
        {
            case 0:
                // Sort by Constellation
                gnssStatus = SortUtil.Companion.sortByGnssThenId(gnssStatus);
                sbasStatus = SortUtil.Companion.sortBySbasThenId(sbasStatus);
                break;
            case 1:
                // Sort by Carrier Frequency
                gnssStatus = SortUtil.Companion.sortByCarrierFrequencyThenId(gnssStatus);
                sbasStatus = SortUtil.Companion.sortByCarrierFrequencyThenId(sbasStatus);
                break;
            case 2:
                // Sort by Signal Strength
                gnssStatus = SortUtil.Companion.sortByCn0(gnssStatus);
                sbasStatus = SortUtil.Companion.sortByCn0(sbasStatus);
                break;
            case 3:
                // Sort by Used in Fix
                gnssStatus = SortUtil.Companion.sortByUsedThenId(gnssStatus);
                sbasStatus = SortUtil.Companion.sortByUsedThenId(sbasStatus);
                break;
            case 4:
                // Sort by Constellation, Carrier Frequency
                gnssStatus = SortUtil.Companion.sortByGnssThenCarrierFrequencyThenId(gnssStatus);
                sbasStatus = SortUtil.Companion.sortBySbasThenCarrierFrequencyThenId(sbasStatus);
                break;
            case 5:
                // Sort by Constellation, Signal Strength
                gnssStatus = SortUtil.Companion.sortByGnssThenCn0ThenId(gnssStatus);
                sbasStatus = SortUtil.Companion.sortBySbasThenCn0ThenId(sbasStatus);
                break;
            case 6:
                // Sort by Constellation, Used in Fix
                gnssStatus = SortUtil.Companion.sortByGnssThenUsedThenId(gnssStatus);
                sbasStatus = SortUtil.Companion.sortBySbasThenUsedThenId(sbasStatus);
                break;
        }
    }

    private void setupUnitPreferences()
    {
        SharedPreferences settings = Application.getPrefs();
        Application app = Application.get();

        prefDistanceUnits = settings
                .getString(app.getString(R.string.pref_key_preferred_distance_units_v2), METERS);
        prefSpeedUnits = settings
                .getString(app.getString(R.string.pref_key_preferred_speed_units_v2), METERS_PER_SECOND);
    }

    /**
     * Sets the visibility of the lists
     */
    private void updateListVisibility()
    {
        if (!gnssStatus.isEmpty())
        {
            gnssNotAvailableView.setVisibility(View.GONE);
            gnssStatusList.setVisibility(View.VISIBLE);
        } else
        {
            gnssNotAvailableView.setVisibility(View.VISIBLE);
            gnssStatusList.setVisibility(View.GONE);
        }
        if (!sbasStatus.isEmpty())
        {
            sbasNotAvailableView.setVisibility(View.GONE);
            sbasStatusList.setVisibility(View.VISIBLE);
        } else
        {
            sbasNotAvailableView.setVisibility(View.VISIBLE);
            sbasStatusList.setVisibility(View.GONE);
        }
    }

    /**
     * Show the Sort Dialog so the user can pick how they want to sort the list of satellites.
     */
    private void showSortByDialog()
    {
        final FragmentActivity activity = getActivity();
        if (activity == null)
        {
            Timber.wtf("The Activity is null so we are unable to show the sorting dialog.");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.menu_option_sort_by);

        final int currentSatOrder = PreferenceUtils.getSatSortOrderFromPreferences();

        builder.setSingleChoiceItems(R.array.sort_sats, currentSatOrder,
                (dialog, index) -> {
                    setSortByClause(index);
                    dialog.dismiss();
                });
        AlertDialog dialog = builder.create();
        dialog.setOwnerActivity(activity);
        dialog.show();
    }

    /**
     * Saves the "sort by" order to preferences
     *
     * @param index the index of R.array.sort_sats that should be set
     */
    private void setSortByClause(int index)
    {
        final String[] sortOptions = getResources().getStringArray(R.array.sort_sats);
        PreferenceUtils.saveString(getResources()
                        .getString(R.string.pref_key_default_sat_sort),
                sortOptions[index]);
    }

    /**
     * Convenience method for ensuring our GnssMeasurement has a valid AGC value
     * @param measurement   Wrapper for {@link android.location.GnssMeasurement}
     * @return              {@code true} if the measurement is not null or old, and has an AGC value
     */
    private boolean hasValidAgc(GnssMeasurementWrapper measurement)
    {
        return measurement != null && measurement.hasAgc() && !measurement.isTimedOut();
    }

    private class SatelliteStatusAdapter extends RecyclerView.Adapter<SatelliteStatusAdapter.ViewHolder>
    {
        ConstellationType mConstellationType;

        SatelliteStatusAdapter(ConstellationType constellationType)
        {
            mConstellationType = constellationType;
        }

        @SuppressWarnings("WeakerAccess")
        class ViewHolder extends RecyclerView.ViewHolder
        {
            private final TextView svId;
            private final TextView gnssFlagHeader;
            private final ImageView gnssFlag;
            private final LinearLayout gnssFlagLayout;
            private final TextView carrierFrequency;
            private final TextView signal;
            private final TextView agcTv;
            private final TextView statusFlags;

            ViewHolder(View v)
            {
                super(v);
                svId = v.findViewById(R.id.sv_id);
                gnssFlagHeader = v.findViewById(R.id.gnss_flag_header);
                gnssFlag = v.findViewById(R.id.gnss_flag);
                gnssFlagLayout = v.findViewById(R.id.gnss_flag_layout);
                carrierFrequency = v.findViewById(R.id.carrier_frequency);
                signal = v.findViewById(R.id.signal);
                agcTv = v.findViewById(R.id.status_row_agc);
                statusFlags = v.findViewById(R.id.status_flags);
            }

            public TextView getSvId()
            {
                return svId;
            }

            public TextView getFlagHeader()
            {
                return gnssFlagHeader;
            }

            public ImageView getFlag()
            {
                return gnssFlag;
            }

            public LinearLayout getFlagLayout()
            {
                return gnssFlagLayout;
            }

            public TextView getCarrierFrequency()
            {
                return carrierFrequency;
            }

            public TextView getSignal()
            {
                return signal;
            }

            public TextView getStatusFlags()
            {
                return statusFlags;
            }

            /**
             * @since 1.5.0
             */
            public TextView getAgcTv()
            {
                return agcTv;
            }

            /**
             * @since 1.5.0
             */
            public void setAgcTv(Locale locale, double agc)
            {

                String agcText = agc != SatelliteStatus.NO_DATA_DOUBLE
                        ? String.format(locale, "%.1f", agc)
                        : "";
                agcTv.setText(agcText);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType)
        {
            View v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.status_row_item, viewGroup, false);
            return new ViewHolder(v);
        }

        @Override
        public int getItemCount()
        {
            // Add 1 for header row
            if (mConstellationType == GNSS)
            {
                return gnssStatus.size() + 1;
            } else
            {
                return sbasStatus.size() + 1;
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder v, final int position)
        {
            if (position == 0)
            {
                BiConsumer<TextView, String> setTextAndBold = (tv, str) -> {
                    tv.setText(str);
                    tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
                };
                // Show the header field for the GNSS flag and hide the ImageView
                v.getFlagHeader().setVisibility(View.VISIBLE);
                v.getFlag().setVisibility(View.GONE);
                v.getFlagLayout().setVisibility(View.GONE);

                // Populate the header fields
                setTextAndBold.accept(v.getSvId(), resources.getString(R.string.gps_prn_column_label));
                if (mConstellationType == GNSS)
                {
                    v.getFlagHeader().setText(resources.getString(R.string.gnss_flag_image_label));
                } else
                {
                    v.getFlagHeader().setText(resources.getString(R.string.sbas_flag_image_label));
                }
                if (GpsTestUtil.isGnssCarrierFrequenciesSupported())
                {
                    v.getCarrierFrequency().setVisibility(View.VISIBLE);
                    setTextAndBold.accept(v.getCarrierFrequency(), resources.getString(R.string.gps_carrier_column_label));
                } else
                {
                    v.getCarrierFrequency().setVisibility(View.GONE);
                }

                setTextAndBold.accept(v.getSignal(), snrCn0Title);
                setTextAndBold.accept(v.getAgcTv(), agcTitle);
                setTextAndBold.accept(v.getStatusFlags(), resources.getString(R.string.gps_flags_column_label));
            } else
            {
                // There is a header at 0, so the first data row will be at position - 1, etc.
                int dataRow = position - 1;

                List<SatelliteStatus> sats;
                if (mConstellationType == GNSS)
                {
                    sats = gnssStatus;
                } else
                {
                    sats = sbasStatus;
                }

                // Show the row field for the GNSS flag mImage and hide the header
                v.getFlagHeader().setVisibility(View.GONE);
                v.getFlag().setVisibility(View.VISIBLE);
                v.getFlagLayout().setVisibility(View.VISIBLE);

                final Locale defaultLocale = Locale.getDefault();

                SatelliteStatus status = sats.get(dataRow);
                // Populate status data for this row
                v.getSvId().setText(String.format(defaultLocale, "%d", status.getSvid()));
                v.getFlag().setScaleType(ImageView.ScaleType.FIT_START);

                GnssType type = status.getGnssType();
                switch (type)
                {
                    case NAVSTAR:
                        v.getFlag().setVisibility(View.VISIBLE);
                        v.getFlag().setImageDrawable(flagUsa);
                        break;
                    case GLONASS:
                        v.getFlag().setVisibility(View.VISIBLE);
                        v.getFlag().setImageDrawable(flagRussia);
                        break;
                    case QZSS:
                        v.getFlag().setVisibility(View.VISIBLE);
                        v.getFlag().setImageDrawable(flagJapan);
                        break;
                    case BEIDOU:
                        v.getFlag().setVisibility(View.VISIBLE);
                        v.getFlag().setImageDrawable(flagChina);
                        break;
                    case GALILEO:
                        v.getFlag().setVisibility(View.VISIBLE);
                        v.getFlag().setImageDrawable(flagEu);
                        break;
                    case IRNSS:
                        v.getFlag().setVisibility(View.VISIBLE);
                        v.getFlag().setImageDrawable(flagIndia);
                        break;
                    case SBAS:
                        setSbasFlag(status, v.getFlag());
                        break;
                    case UNKNOWN:
                        v.getFlag().setVisibility(View.INVISIBLE);
                        break;
                }

                if (GpsTestUtil.isGnssCarrierFrequenciesSupported())
                {
                    if (status.getCarrierFrequencyHz() != SatelliteStatus.NO_DATA)
                    {
                        // Convert Hz to MHz
                        float carrierMhz = MathUtils.toMhz(status.getCarrierFrequencyHz());
                        String carrierLabel = CarrierFreqUtils.getCarrierFrequencyLabel(status.getGnssType(),
                                status.getSvid(),
                                carrierMhz);
                        if (!CarrierFreqUtils.CF_UNKNOWN.equals(carrierLabel))
                        {
                            // Make sure it's the normal text size (in case it's previously been
                            // resized to show raw number).  Use another TextView for default text size.
                            v.getCarrierFrequency().setTextSize(COMPLEX_UNIT_PX, v.getSvId().getTextSize());
                            // Show label such as "L1"
                            v.getCarrierFrequency().setText(carrierLabel);
                        } else
                        {
                            // Shrink the size so we can show raw number
                            v.getCarrierFrequency().setTextSize(COMPLEX_UNIT_DIP, 10);
                            // Show raw number for carrier frequency
                            v.getCarrierFrequency().setText(String.format(defaultLocale, "%.3f", carrierMhz));
                        }
                    } else
                    {
                        v.getCarrierFrequency().setText("");
                    }
                } else
                {
                    v.getCarrierFrequency().setVisibility(View.GONE);
                }
                if (status.getCn0DbHz() != SatelliteStatus.NO_DATA)
                {
                    v.getSignal().setText(String.format(defaultLocale, "%.1f", status.getCn0DbHz()));
                } else
                {
                    v.getSignal().setText("");
                }

                v.setAgcTv(defaultLocale, status.getAgc());

                char[] flags = new char[3];
                flags[0] = !status.getHasAlmanac() ? ' ' : 'A';
                flags[1] = !status.getHasEphemeris() ? ' ' : 'E';
                flags[2] = !status.getUsedInFix() ? ' ' : 'U';
                v.getStatusFlags().setText(new String(flags));
            }
        }

        private void setSbasFlag(SatelliteStatus status, ImageView flag)
        {
            switch (status.getSbasType())
            {
                case WAAS:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagUsa);
                    break;
                case EGNOS:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagEu);
                    break;
                case GAGAN:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagIndia);
                    break;
                case MSAS:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagJapan);
                    break;
                case SDCM:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagRussia);
                    break;
                case SNAS:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagChina);
                    break;
                case SACCSA:
                    flag.setVisibility(View.VISIBLE);
                    flag.setImageDrawable(flagIcao);
                    break;
                case UNKNOWN:
                default:
                    flag.setVisibility(View.INVISIBLE);
            }
        }
    }
}
