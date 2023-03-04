package com.craxiom.networksurvey.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.listeners.IGnssListener;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import timber.log.Timber;

/**
 * The primary fragment to use for the GNSS page of the bottom navigation component.  This fragment view contains tabs
 * which represent the different GNSS views (e.g. Status, Sky View, ...)
 *
 * @since 0.0.10
 */
public class MainGnssFragment extends Fragment
{
    private static final int LOCATION_REFRESH_RATE_MS = 2_000;

    private final Set<IGnssListener> gnssListeners = new CopyOnWriteArraySet<>();

    private LocationListener locationListener;
    private GnssStatus.Callback gnssStatusListener;
    private GnssMeasurementsEvent.Callback gnssMeasurementCallback;
    private LocationManager locationManager;

    public MainGnssFragment()
    {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        FragmentActivity fragmentActivity = getActivity();
        if (fragmentActivity != null)
        {
            locationManager = fragmentActivity.getSystemService(LocationManager.class);
        }

        if (locationManager == null)
        {
            Timber.e("The Location Manager is null. Unable to get GNSS information");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_main_tabs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        final GnssCollectionAdapter gnssCollectionAdapter = new GnssCollectionAdapter(this);
        final ViewPager2 viewPager = view.findViewById(R.id.pager);
        viewPager.setAdapter(gnssCollectionAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(getTabTitle(position))).attach();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!hasLocationPermission()) return;

        addLocationListener();
        addGnssStatusListener();
    }

    @Override
    public void onPause()
    {
        removeLocationListener();
        removeGnssStatusListener();

        super.onPause();
    }

    /**
     * Add a listener for GNSS updates per the {@link IGnssListener} contract.
     *
     * @param gnssListener The GNSS listener to add.
     */
    void registerGnssListener(IGnssListener gnssListener)
    {
        gnssListeners.add(gnssListener);
    }

    /**
     * Remove a listener for GNSS updates.
     *
     * @param gnssListener The GNSS listener to remove.
     */
    void unregisterGnssListener(IGnssListener gnssListener)
    {
        gnssListeners.remove(gnssListener);
    }

    /**
     * @return True if the {@link Manifest.permission#ACCESS_FINE_LOCATION} permission has been granted.  False otherwise.
     */
    private boolean hasLocationPermission()
    {
        final Context context = getContext();
        if (context != null && ActivityCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.w("The ACCESS_FINE_LOCATION permission has not been granted");
            return false;
        }

        return true;
    }

    /**
     * Register a listener for location updates from the Android system.
     */
    @SuppressLint("MissingPermission")
    private void addLocationListener()
    {
        if (locationManager == null)
        {
            Timber.e("The location manager is null.  Unable to register a location listener");
            return;
        }

        if (locationListener == null)
        {
            locationListener = new LocationListener()
            {
                @Override
                public void onLocationChanged(Location location)
                {
                    for (IGnssListener listener : gnssListeners)
                    {
                        listener.onLocationChanged(location);
                    }
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
            };
        } else
        {
            Timber.w("When trying to add a new location listener, the old one was not null.");
        }

        try
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_RATE_MS, 0f, locationListener);
        } catch (Exception e)
        {
            Timber.w(e, "Could not request location updates in the MainGnssFragment");
        }
    }

    /**
     * Unregister the location listener from the Android system.
     */
    private void removeLocationListener()
    {
        if (locationManager == null || locationListener == null)
        {
            Timber.i("The location manager or listener is null.  Unable to remove a location listener");
            return;
        }

        locationManager.removeUpdates(locationListener);
    }

    /**
     * Registers the GNSS Status Listener with the Android System.  Registration occurs in this {@link MainGnssFragment}
     * so that each child fragment in the various tabs don't have to register directly with the Android system.
     */
    @SuppressLint("MissingPermission")
    private void addGnssStatusListener()
    {
        if (locationManager == null)
        {
            Timber.e("The location manager is null.  Unable to register a GNSS status listener");
            return;
        }

        if (gnssStatusListener == null)
        {
            gnssStatusListener = new GnssStatus.Callback()
            {
                @Override
                public void onStarted()
                {
                    for (IGnssListener listener : gnssListeners)
                    {
                        listener.onGnssStarted();
                    }
                }

                @Override
                public void onStopped()
                {
                    for (IGnssListener listener : gnssListeners)
                    {
                        listener.onGnssStopped();
                    }
                }

                @Override
                public void onFirstFix(int ttffMillis)
                {
                    for (IGnssListener listener : gnssListeners)
                    {
                        listener.onGnssFirstFix(ttffMillis);
                    }
                }

                @Override
                public void onSatelliteStatusChanged(GnssStatus status)
                {
                    for (IGnssListener listener : gnssListeners)
                    {
                        listener.onSatelliteStatusChanged(status);
                    }
                }
            };
        }
        if (gnssMeasurementCallback == null)
        {
            gnssMeasurementCallback = new GnssMeasurementsEvent.Callback()
            {
                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs)
                {
                    gnssListeners.forEach(l -> l.onGnssMeasurementsReceived(eventArgs));
                }
            };
        }

        locationManager.registerGnssStatusCallback(gnssStatusListener);
        locationManager.registerGnssMeasurementsCallback(gnssMeasurementCallback);
    }

    /**
     * Unregisters the GNSS Status Listener with the Android system.
     */
    private void removeGnssStatusListener()
    {
        if (locationManager == null)
        {
            Timber.e("The location manager is null.  Unable to unregister a GNSS status listener");
            return;
        }

        locationManager.unregisterGnssStatusCallback(gnssStatusListener);
        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementCallback);
    }

    /**
     * Given the tab position, return the title that should be applied to the tab.
     *
     * @param position The tab position (starts at 0).
     * @return The title to use for the tab.
     */
    private static String getTabTitle(int position)
    {
        switch (position)
        {
            case 0:
                return GnssStatusFragment.TITLE;

            case 1:
                return GnssSkyFragment.TITLE;

            default:
                Timber.wtf("No title specified for the GNSS tab.  Using a default");
                return "";
        }
    }

    /**
     * An adapter that handles creating a new fragment when a tab is selected for the first time.
     */
    public static class GnssCollectionAdapter extends FragmentStateAdapter
    {
        private final MainGnssFragment mainGnssFragment;

        GnssCollectionAdapter(MainGnssFragment fragment)
        {
            super(fragment);
            mainGnssFragment = fragment;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position)
        {
            switch (position)
            {
                case 0:
                    return new GnssStatusFragment();

                case 1:
                    return new GnssSkyFragment();

                default:
                    Timber.wtf("A fragment has not been specified for one of the tabs in the GNSS UI.");
                    return new GnssStatusFragment();
            }
        }

        @Override
        public int getItemCount()
        {
            return 2;
        }
    }
}
