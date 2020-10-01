package com.craxiom.networksurvey.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.craxiom.networksurvey.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import timber.log.Timber;

/**
 * The primary fragment to use for the Cellular page of the bottom navigation component.
 *
 * @since 0.0.10
 */
public class MainCellularFragment extends Fragment
{
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
        final CellularCollectionAdapter cellularCollectionAdapter = new CellularCollectionAdapter(this);
        final ViewPager2 viewPager = view.findViewById(R.id.pager);
        viewPager.setAdapter(cellularCollectionAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(getTabTitle(position))).attach();
    }

    /**
     * An adapter that handles creating a new fragment when a tab is selected for the first time.
     */
    public static class CellularCollectionAdapter extends FragmentStateAdapter
    {
        CellularCollectionAdapter(Fragment fragment)
        {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position)
        {
            switch (position)
            {
                case 0:
                    return new NetworkDetailsFragment();

                case 1:
                    return new CalculatorFragment();

                default:
                    Timber.wtf("A fragment has not been specified for one of the tabs in the Cellular UI.");
                    return new NetworkDetailsFragment();
            }
        }

        @Override
        public int getItemCount()
        {
            return 2;
        }
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
                return NetworkDetailsFragment.TITLE;

            case 1:
                return CalculatorFragment.TITLE;

            default:
                Timber.wtf("No title specified for the Cellular tab.  Using a default");
                return "";
        }
    }
}
