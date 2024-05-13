package com.craxiom.networksurvey.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.GnssType

object LibUIUtils {
    const val ANIMATION_DURATION_SHORT_MS = 200
    const val MIN_VALUE_CN0 = 10.0f
    const val MAX_VALUE_CN0 = 45.0f

    /**
     * Converts screen dimension units from dp to pixels, based on algorithm defined in
     * http://developer.android.com/guide/practices/screens_support.html#dips-pels
     *
     * @param dp value in dp
     * @return value in pixels
     */
    @JvmStatic
    fun dpToPixels(context: Context, dp: Float): Int {
        // Get the screen's density scale
        val scale = context.resources.displayMetrics.density
        // Convert the dps to pixels, based on density scale
        return (dp * scale + 0.5f).toInt()
    }

    /**
     * Converts the provided C/N0 values to a left margin value (dp) for the avg C/N0 indicator ImageViews in gps_sky_signal
     * Left margin range for the C/N0 indicator ImageViews in gps_sky_signal is determined by dimens.xml
     * cn0_meter_width (based on device screen width) and cn0_indicator_min_left_margin values.
     *
     * This is effectively an affine transform - https://math.stackexchange.com/a/377174/554287.
     *
     * @param cn0 carrier-to-noise density at the antenna of the satellite in dB-Hz (from GnssStatus)
     * @return left margin value in dp for the C/N0 indicator ImageViews
     */
    @JvmStatic
    fun cn0ToIndicatorLeftMarginPx(
        cn0: Float,
        minIndicatorMarginPx: Int,
        maxIndicatorMarginPx: Int
    ): Int {
        return MathUtils.mapToRange(
            cn0,
            MIN_VALUE_CN0,
            MAX_VALUE_CN0,
            minIndicatorMarginPx.toFloat(),
            maxIndicatorMarginPx.toFloat()
        ).toInt()
    }

    /**
     * Converts the provided C/N0 values to a left margin value (dp) for the avg C/N0 TextViews in gps_sky_signal
     * Left margin range for the C/N0 indicator TextView in gps_sky_signal is determined by dimens.xml
     * cn0_meter_width (based on device screen width) and cn0_textview_min_left_margin values.
     *
     * This is effectively an affine transform - https://math.stackexchange.com/a/377174/554287.
     *
     * @param cn0 carrier-to-noise density at the antenna of the satellite in dB-Hz (from GnssStatus)
     * @return left margin value in dp for the C/N0 TextViews
     */
    @JvmStatic
    fun cn0ToTextViewLeftMarginPx(
        cn0: Float,
        minTextViewMarginPx: Int,
        maxTextViewMarginPx: Int
    ): Int {
        return MathUtils.mapToRange(
            cn0,
            MIN_VALUE_CN0,
            MAX_VALUE_CN0,
            minTextViewMarginPx.toFloat(),
            maxTextViewMarginPx.toFloat()
        ).toInt()
    }

    /**
     * Sets the margins for a given view
     *
     * @param v View to set the margin for
     * @param l left margin, in pixels
     * @param t top margin, in pixels
     * @param r right margin, in pixels
     * @param b bottom margin, in pixels
     */
    fun setMargins(v: View, l: Int, t: Int, r: Int, b: Int) {
        val p = v.layoutParams as RelativeLayout.LayoutParams
        p.setMargins(l, t, r, b)
        v.layoutParams = p
    }

    fun updateLeftMarginPx(leftMarginPx: Int, view: View) {
        val lp = view.layoutParams as RelativeLayout.LayoutParams
        val newLp = RelativeLayout.LayoutParams(lp.width, lp.height)
        newLp.setMargins(leftMarginPx, lp.topMargin, lp.rightMargin, lp.bottomMargin)
        lp.rules.forEachIndexed { index, i ->
            newLp.addRule(index, i)
        }
        view.layoutParams = newLp
    }

    /**
     * Shows a view using animation
     *
     * @param v                 View to show
     * @param animationDuration duration of animation
     */
    fun showViewWithAnimation(v: View, animationDuration: Int) {
        if (v.visibility == View.VISIBLE && v.alpha == 1f) {
            // View is already visible and not transparent, return without doing anything
            return
        }
        v.clearAnimation()
        v.animate().cancel()
        if (v.visibility != View.VISIBLE) {
            // Set the content view to 0% opacity but visible, so that it is visible
            // (but fully transparent) during the animation.
            v.alpha = 0f
            v.visibility = View.VISIBLE
        }

        // Animate the content view to 100% opacity, and clear any animation listener set on the view.
        v.animate()
            .alpha(1f)
            .setDuration(animationDuration.toLong())
            .setListener(null)
    }

    /**
     * Hides a view using animation
     *
     * @param v                 View to hide
     * @param animationDuration duration of animation
     */
    fun hideViewWithAnimation(v: View, animationDuration: Int) {
        if (v.visibility == View.GONE) {
            // View is already gone, return without doing anything
            return
        }
        v.clearAnimation()
        v.animate().cancel()

        // Animate the view to 0% opacity. After the animation ends, set its visibility to GONE as
        // an optimization step (it won't participate in layout passes, etc.)
        v.animate()
            .alpha(0f)
            .setDuration(animationDuration.toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    v.visibility = View.GONE
                }
            })
    }


    /**
     * Returns the display name for the given GnssType
     * @param context
     * @param gnssType
     * @return the display name for the given GnssType
     */
    fun getGnssDisplayName(context: Context, gnssType: GnssType?): String {
        return when (gnssType) {
            GnssType.NAVSTAR -> context.resources.getString(R.string.sky_legend_shape_navstar)
            GnssType.GALILEO -> context.resources.getString(R.string.sky_legend_shape_galileo)
            GnssType.GLONASS -> context.resources.getString(R.string.sky_legend_shape_glonass)
            GnssType.BEIDOU -> context.resources.getString(R.string.sky_legend_shape_beidou)
            GnssType.QZSS -> context.resources.getString(R.string.sky_legend_shape_qzss)
            GnssType.IRNSS -> context.resources.getString(R.string.sky_legend_shape_irnss)
            GnssType.SBAS -> context.resources.getString(R.string.sbas)
            GnssType.UNKNOWN -> context.resources.getString(R.string.unknown)
            else -> context.resources.getString(R.string.unknown)
        }
    }
}