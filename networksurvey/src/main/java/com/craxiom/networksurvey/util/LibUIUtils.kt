package com.craxiom.networksurvey.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

object LibUIUtils {
    const val ANIMATION_DURATION_SHORT_MS = 200
    const val MIN_VALUE_CN0 = 10.0f
    const val MAX_VALUE_CN0 = 45.0f

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
}