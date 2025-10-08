package com.craxiom.networksurvey.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.SimChangeReceiver;
import com.craxiom.networksurvey.databinding.FragmentMainTabsBinding;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

import timber.log.Timber;

/**
 * The primary fragment to use for the Cellular page of the bottom navigation component.
 *
 * @since 0.0.10
 */
public class MainCellularFragment extends AServiceDataFragment
{
    private List<SubscriptionInfo> activeSubscriptionInfoList;

    private BroadcastReceiver simBroadcastReceiver;
    private FragmentMainTabsBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        simBroadcastReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                if (intent == null) return;

                Timber.i("SIM State Change Detected. Restarting the cellular fragment");

                try
                {
                    // Restart this fragment (yes, it seems overly complicated but it works)
                    FragmentManager fragmentManager = getParentFragmentManager();
                    Fragment currentFragment = fragmentManager.findFragmentById(R.id.cellular_fragment_container_view);
                    if (currentFragment != null)
                    {
                        FragmentTransaction detachTransaction = fragmentManager.beginTransaction();
                        detachTransaction.detach(currentFragment);
                        detachTransaction.commit();

                        FragmentTransaction attachTransaction = fragmentManager.beginTransaction();
                        attachTransaction.attach(currentFragment);
                        attachTransaction.commit();
                    }
                } catch (Exception e)
                {
                    Timber.w(e, "Could not restart the cellular fragment after a SIM event.");
                }
            }
        };

        Context context = getContext();
        if (context != null)
        {
            LocalBroadcastManager.getInstance(context).registerReceiver(simBroadcastReceiver,
                    new IntentFilter(SimChangeReceiver.SIM_CHANGED_INTENT));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        binding = FragmentMainTabsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // Reset keepScreenOn to false to ensure screen doesn't stay on
        // This fixes the issue where navigating from Survey Monitor or Tower Map
        // leaves the screen permanently on
        View view = getView();
        if (view != null)
        {
            view.setKeepScreenOn(false);
        }

        startAndBindToService();
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
    }

    @Override
    public void onDestroy()
    {
        Context context = getContext();
        if (context != null)
        {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(simBroadcastReceiver);
        }

        super.onDestroy();
    }

    @Override
    protected void onSurveyServiceConnected(NetworkSurveyService service)
    {
        View view = getView();
        if (view == null)
        {
            Timber.e("The view is null in the onSurveyServiceConnected method");
            return;
        }

        activeSubscriptionInfoList = service.getActiveSubscriptionInfoList();

        final CellularCollectionAdapter cellularCollectionAdapter = new CellularCollectionAdapter(this, activeSubscriptionInfoList);
        final ViewPager2 viewPager = view.findViewById(R.id.pager);
        viewPager.setAdapter(cellularCollectionAdapter);

        final TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        if (activeSubscriptionInfoList.size() <= 1)
        {
            // Only one tab, so hide the tab layout
            tabLayout.setVisibility(View.GONE);
        } else
        {
            tabLayout.setVisibility(View.VISIBLE);
        }
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(getTabTitle(position))).attach();
    }

    @Override
    protected void onSurveyServiceDisconnecting(NetworkSurveyService service)
    {
        super.onSurveyServiceDisconnecting(service);
    }

    /**
     * An adapter that handles creating a new fragment when a tab is selected for the first time.
     */
    public static class CellularCollectionAdapter extends FragmentStateAdapter
    {
        private final List<SubscriptionInfo> subscriptions;

        CellularCollectionAdapter(Fragment fragment, List<SubscriptionInfo> subscriptions)
        {
            super(fragment);
            this.subscriptions = subscriptions;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position)
        {
            NetworkDetailsFragment networkDetailsFragment = new NetworkDetailsFragment();
            Bundle args = new Bundle();

            // If there are no subscriptions, we might still be able to get survey results because
            // of emergency call support. It could also be that READ_PHONE_STATE permissions were not granted.
            if (subscriptions.isEmpty())
            {
                args.putInt(NetworkDetailsFragment.SUBSCRIPTION_ID_KEY, CellularController.DEFAULT_SUBSCRIPTION_ID);
            } else if (subscriptions.size() == 1)
            {
                // We only want to use the subscription info list if there are two active SIMs.  If there is only
                // one active SIM, then we will just use the default subscription ID which gets filtered out in
                // the SurveyRecordProcessor. This prevents the "slot" field from getting set on all the records.
                args.putInt(NetworkDetailsFragment.SUBSCRIPTION_ID_KEY, CellularController.DEFAULT_SUBSCRIPTION_ID);
            } else
            {
                int subscriptionId = subscriptions.get(position).getSubscriptionId();
                args.putInt(NetworkDetailsFragment.SUBSCRIPTION_ID_KEY, subscriptionId);
            }

            networkDetailsFragment.setArguments(args);
            return networkDetailsFragment;
        }

        @Override
        public int getItemCount()
        {
            int size = subscriptions.size();
            if (size == 0) return 1;

            return size;
        }
    }

    /**
     * Given the tab position, return the title that should be applied to the tab.
     *
     * @param position The tab position (starts at 0).
     * @return The title to use for the tab.
     */
    private String getTabTitle(int position)
    {
        if (activeSubscriptionInfoList.isEmpty()) return "No SIM";

        int subscriptionId = activeSubscriptionInfoList.get(position).getSubscriptionId();

        return "SIM " + subscriptionId;
    }

    /**
     * Called by child fragments when neighbor data is updated.
     * Scrolls to bottom if user was already at bottom.
     */
    public void onNeighborDataUpdated()
    {
        if (binding == null) return;

        androidx.core.widget.NestedScrollView scrollView = binding.mainTabsScrollView;

        // Capture "at bottom" state BEFORE the content changes/grows
        // canScrollVertically(1) returns false when we can't scroll down anymore (i.e., we're at bottom)
        // BUT: Also check that there's actual content, to avoid auto-scrolling on initial load
        // when scrollY=0 and there's no content yet (which also makes canScrollVertically return false)
        int scrollY = scrollView.getScrollY();
        boolean hasScrollableContent = scrollY > 0 ||
                (scrollView.getChildCount() > 0 && scrollView.getChildAt(0).getBottom() > scrollView.getHeight());
        boolean wasAtBottom = !scrollView.canScrollVertically(1) && hasScrollableContent;

        if (wasAtBottom)
        {
            // Post to wait for neighbor table layout to complete, then scroll to new bottom
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

}
