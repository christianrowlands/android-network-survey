package com.craxiom.networksurvey.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.gnss.model.SignalInfoViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber

/**
 * The primary fragment to use for the GNSS page of the bottom navigation component.  This fragment view contains tabs
 * which represent the different GNSS views (e.g. Status, Sky View, ...)
 *
 * @since 0.0.10
 */
class MainGnssFragment : Fragment() {

    @ExperimentalCoroutinesApi
    val viewModel: SignalInfoViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_gnss_tabs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val gnssCollectionAdapter = GnssCollectionAdapter(this)
        val viewPager = view.findViewById<ViewPager2>(R.id.pager)
        viewPager.setAdapter(gnssCollectionAdapter)
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            tab.setText(
                getTabTitle(position)
            )
        }.attach()
    }

    @ExperimentalCoroutinesApi
    override fun onResume() {
        super.onResume()
        if (!hasLocationPermission()) return
        viewModel.setStarted(requireContext(), true, Application.getPrefs())
    }

    @ExperimentalCoroutinesApi
    override fun onPause() {
        viewModel.setStarted(requireContext(), false, Application.getPrefs())
        super.onPause()
    }

    /**
     * @return True if the [Manifest.permission.ACCESS_FINE_LOCATION] permission has been granted.  False otherwise.
     */
    private fun hasLocationPermission(): Boolean {
        val context = context
        if (context != null && ActivityCompat.checkSelfPermission(
                context.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("The ACCESS_FINE_LOCATION permission has not been granted")
            return false
        }
        return true
    }

    /**
     * An adapter that handles creating a new fragment when a tab is selected for the first time.
     */
    class GnssCollectionAdapter internal constructor(fragment: MainGnssFragment) :
        FragmentStateAdapter(
            fragment
        ) {
        @ExperimentalCoroutinesApi
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> GnssDetailsFragment()
                1 -> SkyFragment()

                else -> {
                    Timber.wtf("A fragment has not been specified for one of the tabs in the GNSS UI.")
                    SkyFragment()
                }
            }
        }

        override fun getItemCount(): Int {
            return 2
        }
    }

    companion object {

        /**
         * Given the tab position, return the title that should be applied to the tab.
         *
         * @param position The tab position (starts at 0).
         * @return The title to use for the tab.
         */
        private fun getTabTitle(position: Int): String {
            return when (position) {
                0 -> TITLE
                1 -> "Sky View"
                else -> {
                    Timber.wtf("No title specified for the GNSS tab.  Using a default")
                    ""
                }
            }
        }
    }
}
