/*
 * Copyright (C) 2008-2013 The Android Open Source Project,
 * Sean J. Barbeau
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

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.listeners.IGnssListener;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.UIUtils;
import com.craxiom.networksurvey.view.GnssSkyView;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * A fragment for displaying a sky view of the SVs.
 * <p>
 * This interface is originally from the GPS Test open source Android app and has been adapted to meet the needs of
 * this Network Survey app.
 * https://github.com/barbeau/gpstest/blob/master/GPSTest/src/main/java/com/android/gpstest/GpsSkyFragment.java
 */
public class GnssSkyFragment extends Fragment implements IGnssListener
{
    private static final String LOG_TAG = GnssSkyFragment.class.getSimpleName();
    static final String TITLE = "Sky View";

    private MainGnssFragment mainGnssFragment;

    private GnssSkyView skyView;

    private List<View> legendLines;

    private List<ImageView> legendShapes;

    private TextView legendCn0Title, legendCn0Units, legendCn0LeftText, legendCn0LeftCenterText,
            legendCn0CenterText, legendCn0RightCenterText, legendCn0RightText, snrCn0InViewAvgText, snrCn0UsedAvgText;

    private ImageView snrCn0InViewAvg, snrCn0UsedAvg;

    Animation snrCn0InViewAvgAnimation, snrCn0UsedAvgAnimation, snrCn0InViewAvgAnimationTextView, snrCn0UsedAvgAnimationTextView;

    private boolean useLegacyGnssApi = false;

    /**
     * Constructs this fragment.
     *
     * @param mainGnssFragment Used to register and unregister for updates to GNSS events.
     */
    GnssSkyFragment(MainGnssFragment mainGnssFragment)
    {
        this.mainGnssFragment = mainGnssFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.gps_sky, container, false);

        skyView = v.findViewById(R.id.sky_view);

        initLegendViews(v);

        snrCn0InViewAvg = v.findViewById(R.id.cn0_indicator_in_view);
        snrCn0UsedAvg = v.findViewById(R.id.cn0_indicator_used);

        return v;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        mainGnssFragment.registerGnssListener(this);

        final int color = getResources().getColor(android.R.color.secondary_text_dark, null);
        /*if (Application.getPrefs().getBoolean(getString(R.string.pref_key_dark_theme), false))
        {
            // Dark theme
            color = getResources().getColor(android.R.color.secondary_text_dark, null);
        } else
        {
            // Light theme
            color = getResources().getColor(R.color.body_text_2_light, null);
        }*/
        for (View v : legendLines)
        {
            v.setBackgroundColor(color);
        }
        for (ImageView v : legendShapes)
        {
            v.setColorFilter(color);
        }
    }

    public void onLocationChanged(Location loc)
    {
    }

    public void onStatusChanged(String provider, int status, Bundle extras)
    {
    }

    public void onProviderEnabled(String provider)
    {
    }

    public void onProviderDisabled(String provider)
    {
    }

    public void gpsStart()
    {
    }

    public void gpsStop()
    {
    }

    @Override
    public void onGnssFirstFix(int ttffMillis)
    {

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status)
    {
        skyView.setGnssStatus(status);
        useLegacyGnssApi = false;
        updateSnrCn0AvgMeterText();
        updateSnrCn0Avgs();
    }

    @Override
    public void onGnssStarted()
    {
        skyView.setStarted();
    }

    @Override
    public void onGnssStopped()
    {
        skyView.setStopped();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
    {

    }

    @Override
    public void onOrientationChanged(double orientation, double tilt)
    {
        // For performance reasons, only proceed if this fragment is visible
        if (!getUserVisibleHint())
        {
            return;
        }

        if (skyView != null)
        {
            skyView.onOrientationChanged(orientation, tilt);
        }
    }

    @Override
    public void onNmeaMessage(String message, long timestamp)
    {
    }

    /**
     * Initialize the views in the C/N0 and Shape legends
     *
     * @param v view in which the legend view IDs can be found via view.findViewById()
     */
    private void initLegendViews(View v)
    {
        if (legendLines == null)
        {
            legendLines = new LinkedList<>();
        } else
        {
            legendLines.clear();
        }

        if (legendShapes == null)
        {
            legendShapes = new LinkedList<>();
        } else
        {
            legendShapes.clear();
        }

        // Avg C/N0 indicator lines
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line4));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line3));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line2));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_left_line1));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_center_line));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line1));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line2));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line3));
        legendLines.add(v.findViewById(R.id.sky_legend_cn0_right_line4));

        // Shape Legend lines
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line1a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line1b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line2a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line2b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line3a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line3b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line4a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line4b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line5a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line5b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line6a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line6b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line7a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line7b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line8a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line8b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line9a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line9b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line10a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line10b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line11a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line12a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line13a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line14a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line14b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line15a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line15b));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line16a));
        legendLines.add(v.findViewById(R.id.sky_legend_shape_line16b));

        // Shape Legend shapes
        legendShapes.add(v.findViewById(R.id.sky_legend_circle));
        legendShapes.add(v.findViewById(R.id.sky_legend_square));
        legendShapes.add(v.findViewById(R.id.sky_legend_pentagon));
        legendShapes.add(v.findViewById(R.id.sky_legend_triangle));
        legendShapes.add(v.findViewById(R.id.sky_legend_hexagon1));
        legendShapes.add(v.findViewById(R.id.sky_legend_oval));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond1));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond2));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond3));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond4));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond5));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond6));
        legendShapes.add(v.findViewById(R.id.sky_legend_diamond7));

        // C/N0 Legend text
        legendCn0Title = v.findViewById(R.id.sky_legend_cn0_title);
        legendCn0Units = v.findViewById(R.id.sky_legend_cn0_units);
        legendCn0LeftText = v.findViewById(R.id.sky_legend_cn0_left_text);
        legendCn0LeftCenterText = v.findViewById(R.id.sky_legend_cn0_left_center_text);
        legendCn0CenterText = v.findViewById(R.id.sky_legend_cn0_center_text);
        legendCn0RightCenterText = v.findViewById(R.id.sky_legend_cn0_right_center_text);
        legendCn0RightText = v.findViewById(R.id.sky_legend_cn0_right_text);
        snrCn0InViewAvgText = v.findViewById(R.id.cn0_text_in_view);
        snrCn0UsedAvgText = v.findViewById(R.id.cn0_text_used);
    }

    private void updateSnrCn0AvgMeterText()
    {
        if (!useLegacyGnssApi || (skyView != null && skyView.isSnrBad()))
        {
            // C/N0
            legendCn0Title.setText(R.string.gps_cn0_column_label);
            legendCn0Units.setText(R.string.sky_legend_cn0_units);
            legendCn0LeftText.setText(R.string.sky_legend_cn0_low);
            legendCn0LeftCenterText.setText(R.string.sky_legend_cn0_low_middle);
            legendCn0CenterText.setText(R.string.sky_legend_cn0_middle);
            legendCn0RightCenterText.setText(R.string.sky_legend_cn0_middle_high);
            legendCn0RightText.setText(R.string.sky_legend_cn0_high);
        } else
        {
            // SNR for Android 6.0 and lower (or if user unchecked "Use GNSS APIs" setting and values conform to SNR range)
            legendCn0Title.setText(R.string.gps_snr_column_label);
            legendCn0Units.setText(R.string.sky_legend_snr_units);
            legendCn0LeftText.setText(R.string.sky_legend_snr_low);
            legendCn0LeftCenterText.setText(R.string.sky_legend_snr_low_middle);
            legendCn0CenterText.setText(R.string.sky_legend_snr_middle);
            legendCn0RightCenterText.setText(R.string.sky_legend_snr_middle_high);
            legendCn0RightText.setText(R.string.sky_legend_snr_high);
        }
    }

    private void updateSnrCn0Avgs()
    {
        if (skyView == null)
        {
            return;
        }
        // Based on the avg SNR or C/N0 for "in view" and "used" satellites the left margins need to be adjusted accordingly
        int meterWidthPx = (int) Application.get().getResources().getDimension(R.dimen.cn0_meter_width)
                - UIUtils.dpToPixels(Application.get(), 7.0f); // Reduce width for padding
        int minIndicatorMarginPx = (int) Application.get().getResources().getDimension(R.dimen.cn0_indicator_min_left_margin);
        int maxIndicatorMarginPx = meterWidthPx + minIndicatorMarginPx;
        int minTextViewMarginPx = (int) Application.get().getResources().getDimension(R.dimen.cn0_textview_min_left_margin);
        int maxTextViewMarginPx = meterWidthPx + minTextViewMarginPx;

        // When both "in view" and "used" indicators and TextViews are shown, slide the "in view" TextView by this amount to the left to avoid overlap
        float TEXTVIEW_NON_OVERLAP_OFFSET_DP = -16.0f;

        // Calculate normal offsets for avg in view satellite SNR or C/N0 value TextViews
        Integer leftInViewTextViewMarginPx = null;
        if (MathUtils.isValidFloat(skyView.getSnrCn0InViewAvg()))
        {
            if (!skyView.isUsingLegacyGpsApi() || skyView.isSnrBad())
            {
                // C/N0
                leftInViewTextViewMarginPx = UIUtils.cn0ToTextViewLeftMarginPx(skyView.getSnrCn0InViewAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            } else
            {
                // SNR
                leftInViewTextViewMarginPx = UIUtils.snrToTextViewLeftMarginPx(skyView.getSnrCn0InViewAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            }
        }

        // Calculate normal offsets for avg used satellite C/N0 value TextViews
        Integer leftUsedTextViewMarginPx = null;
        if (MathUtils.isValidFloat(skyView.getSnrCn0UsedAvg()))
        {
            if (!skyView.isUsingLegacyGpsApi() || skyView.isSnrBad())
            {
                // C/N0
                leftUsedTextViewMarginPx = UIUtils.cn0ToTextViewLeftMarginPx(skyView.getSnrCn0UsedAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            } else
            {
                // SNR
                leftUsedTextViewMarginPx = UIUtils.snrToTextViewLeftMarginPx(skyView.getSnrCn0UsedAvg(),
                        minTextViewMarginPx, maxTextViewMarginPx);
            }
        }

        // See if we need to apply the offset margin to try and keep the two TextViews from overlapping by shifting one of the two left
        if (leftInViewTextViewMarginPx != null && leftUsedTextViewMarginPx != null)
        {
            int offset = UIUtils.dpToPixels(Application.get(), TEXTVIEW_NON_OVERLAP_OFFSET_DP);
            if (leftInViewTextViewMarginPx <= leftUsedTextViewMarginPx)
            {
                leftInViewTextViewMarginPx += offset;
            } else
            {
                leftUsedTextViewMarginPx += offset;
            }
        }

        // Define paddings used for TextViews
        int pSides = UIUtils.dpToPixels(Application.get(), 7);
        int pTopBottom = UIUtils.dpToPixels(Application.get(), 4);

        final Locale defaultLocale = Locale.getDefault();

        // Set avg SNR or C/N0 of satellites in view of device
        if (MathUtils.isValidFloat(skyView.getSnrCn0InViewAvg()))
        {
            snrCn0InViewAvgText.setText(String.format(defaultLocale, "%.1f", skyView.getSnrCn0InViewAvg()));

            // Set color of TextView
            int color = skyView.getSatelliteColor(skyView.getSnrCn0InViewAvg());
            LayerDrawable background = (LayerDrawable) ContextCompat.getDrawable(Application.get(), R.drawable.cn0_round_corner_background_in_view);

            // Fill
            GradientDrawable backgroundGradient = (GradientDrawable) background.findDrawableByLayerId(R.id.cn0_avg_in_view_fill);
            backgroundGradient.setColor(color);

            // Stroke
            GradientDrawable borderGradient = (GradientDrawable) background.findDrawableByLayerId(R.id.cn0_avg_in_view_border);
            borderGradient.setColor(color);

            snrCn0InViewAvgText.setBackground(background);

            // Set padding
            snrCn0InViewAvgText.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            // Set color of indicator
            snrCn0InViewAvg.setColorFilter(color);

            // Set position and visibility of TextView
            if (snrCn0InViewAvgText.getVisibility() == View.VISIBLE)
            {
                animateSnrCn0Indicator(snrCn0InViewAvgText, leftInViewTextViewMarginPx, snrCn0InViewAvgAnimationTextView);
            } else
            {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) snrCn0InViewAvgText.getLayoutParams();
                lp.setMargins(leftInViewTextViewMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                snrCn0InViewAvgText.setLayoutParams(lp);
                snrCn0InViewAvgText.setVisibility(View.VISIBLE);
            }

            // Set position and visibility of indicator
            int leftIndicatorMarginPx;
            if (!skyView.isUsingLegacyGpsApi() || skyView.isSnrBad())
            {
                // C/N0
                leftIndicatorMarginPx = UIUtils.cn0ToIndicatorLeftMarginPx(skyView.getSnrCn0InViewAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            } else
            {
                // SNR
                leftIndicatorMarginPx = UIUtils.snrToIndicatorLeftMarginPx(skyView.getSnrCn0InViewAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            }

            // If the view is already visible, animate to the new position.  Otherwise just set the position and make it visible
            if (snrCn0InViewAvg.getVisibility() == View.VISIBLE)
            {
                animateSnrCn0Indicator(snrCn0InViewAvg, leftIndicatorMarginPx, snrCn0InViewAvgAnimation);
            } else
            {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) snrCn0InViewAvg.getLayoutParams();
                lp.setMargins(leftIndicatorMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                snrCn0InViewAvg.setLayoutParams(lp);
                snrCn0InViewAvg.setVisibility(View.VISIBLE);
            }
        } else
        {
            snrCn0InViewAvgText.setText("");
            snrCn0InViewAvgText.setVisibility(View.INVISIBLE);
            snrCn0InViewAvg.setVisibility(View.INVISIBLE);
        }

        // Set avg SNR or C/N0 of satellites used in fix
        if (MathUtils.isValidFloat(skyView.getSnrCn0UsedAvg()))
        {
            snrCn0UsedAvgText.setText(String.format(defaultLocale, "%.1f", skyView.getSnrCn0UsedAvg()));
            // Set color of TextView
            int color = skyView.getSatelliteColor(skyView.getSnrCn0UsedAvg());
            LayerDrawable background = (LayerDrawable) ContextCompat.getDrawable(Application.get(), R.drawable.cn0_round_corner_background_used);

            // Fill
            GradientDrawable backgroundGradient = (GradientDrawable) background.findDrawableByLayerId(R.id.cn0_avg_used_fill);
            backgroundGradient.setColor(color);

            snrCn0UsedAvgText.setBackground(background);

            // Set padding
            snrCn0UsedAvgText.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            // Set position and visibility of TextView
            if (snrCn0UsedAvgText.getVisibility() == View.VISIBLE)
            {
                animateSnrCn0Indicator(snrCn0UsedAvgText, leftUsedTextViewMarginPx, snrCn0UsedAvgAnimationTextView);
            } else
            {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) snrCn0UsedAvgText.getLayoutParams();
                lp.setMargins(leftUsedTextViewMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                snrCn0UsedAvgText.setLayoutParams(lp);
                snrCn0UsedAvgText.setVisibility(View.VISIBLE);
            }

            // Set position and visibility of indicator
            int leftMarginPx;
            if (!skyView.isUsingLegacyGpsApi() || skyView.isSnrBad())
            {
                // C/N0
                leftMarginPx = UIUtils.cn0ToIndicatorLeftMarginPx(skyView.getSnrCn0UsedAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            } else
            {
                // SNR
                leftMarginPx = UIUtils.snrToIndicatorLeftMarginPx(skyView.getSnrCn0UsedAvg(),
                        minIndicatorMarginPx, maxIndicatorMarginPx);
            }

            // If the view is already visible, animate to the new position.  Otherwise just set the position and make it visible
            if (snrCn0UsedAvg.getVisibility() == View.VISIBLE)
            {
                animateSnrCn0Indicator(snrCn0UsedAvg, leftMarginPx, snrCn0UsedAvgAnimation);
            } else
            {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) snrCn0UsedAvg.getLayoutParams();
                lp.setMargins(leftMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin);
                snrCn0UsedAvg.setLayoutParams(lp);
                snrCn0UsedAvg.setVisibility(View.VISIBLE);
            }
        } else
        {
            snrCn0UsedAvgText.setText("");
            snrCn0UsedAvgText.setVisibility(View.INVISIBLE);
            snrCn0UsedAvg.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Animates a SNR or C/N0 indicator view from it's current location to the provided left margin location (in pixels)
     *
     * @param v                view to animate
     * @param goalLeftMarginPx the new left margin for the view that the view should animate to in pixels
     * @param animation        Animation to use for the animation
     */
    private void animateSnrCn0Indicator(final View v, final int goalLeftMarginPx, Animation animation)
    {
        if (v == null)
        {
            return;
        }

        if (animation != null)
        {
            animation.reset();
        }

        final ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

        final int currentMargin = p.leftMargin;

        animation = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t)
            {
                int newLeft;
                final float offset = Math.abs(currentMargin - goalLeftMarginPx) * interpolatedTime;
                if (goalLeftMarginPx > currentMargin)
                {
                    newLeft = currentMargin + (int) offset;
                } else
                {
                    newLeft = currentMargin - (int) offset;
                }
                UIUtils.setMargins(v, newLeft, p.topMargin, p.rightMargin, p.bottomMargin);
            }
        };
        // C/N0 updates every second, so animation of 300ms (https://material.io/guidelines/motion/duration-easing.html#duration-easing-common-durations)
        // wit FastOutSlowInInterpolator recommended by Material Design spec easily finishes in time for next C/N0 update
        animation.setDuration(300);
        animation.setInterpolator(new FastOutSlowInInterpolator());
        v.startAnimation(animation);
    }
}
