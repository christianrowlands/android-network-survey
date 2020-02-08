package com.craxiom.networksurvey.fragments;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.craxiom.networksurvey.NetworkSurveyActivity;

/**
 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 *
 * @since 0.0.4
 */
public class SectionsPagerAdapter extends FragmentPagerAdapter
{
    private static final String LOG_TAG = SectionsPagerAdapter.class.getSimpleName();

    private final NetworkSurveyActivity networkSurveyActivity;

    private NetworkDetailsFragment networkDetailsFragment;

    public SectionsPagerAdapter(FragmentManager fragmentManager, NetworkSurveyActivity networkSurveyActivity)
    {
        super(fragmentManager, FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.networkSurveyActivity = networkSurveyActivity;
    }

    @NonNull
    @Override
    public Fragment getItem(int position)
    {
        Log.d(LOG_TAG, "Creating the fragment for tab " + position);

        // getItem is called to instantiate the fragment for the given page.
        switch (position)
        {
            case 0:
                networkDetailsFragment = new NetworkDetailsFragment();
                return networkDetailsFragment;

            case 1:
                return new CalculatorFragment();

            case 2:
                return new GrpcConnectionFragment();
        }

        Log.wtf(LOG_TAG, "Somehow we are trying to get an item for a tab that does not exist");

        return new NetworkDetailsFragment();
    }

    @Override
    public int getCount()
    {
        return 3;
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        switch (position)
        {
            case 0:
                return "Network Details";
            case 1:
                return "Calculators";
            case 2:
                return "Connection";
        }
        return null;
    }

    public boolean isNetworkDetailsVisible()
    {
        return networkDetailsFragment.isVisible();
    }
}
