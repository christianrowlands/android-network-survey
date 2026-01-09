package com.craxiom.networksurvey.util;

import android.content.Context;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import com.craxiom.networksurvey.R;

/**
 * Manages the location hint timer and display logic for fragments that show a location card.
 * This helper class encapsulates the timer management and hint display functionality to avoid
 * code duplication across multiple fragments.
 */
public class LocationHintManager
{
    /**
     * The delay in milliseconds before showing the location hint if GPS location is not obtained.
     */
    public static final long LOCATION_HINT_DELAY_MS = 15_000;

    private final Handler handler;
    private Runnable locationHintRunnable;

    /**
     * Creates a new LocationHintManager.
     *
     * @param handler The Handler to use for posting delayed runnables. Should use the main looper.
     */
    public LocationHintManager(Handler handler)
    {
        this.handler = handler;
    }

    /**
     * Starts a timer to show a hint about changing location provider if GPS takes too long.
     * If a timer is already running, it will be cancelled before starting the new one.
     *
     * @param showHintCallback The callback to invoke when the timer expires to show the hint.
     */
    public void startTimer(Runnable showHintCallback)
    {
        cancelTimer();
        locationHintRunnable = showHintCallback;
        handler.postDelayed(locationHintRunnable, LOCATION_HINT_DELAY_MS);
    }

    /**
     * Cancels the location hint timer if it's running.
     */
    public void cancelTimer()
    {
        if (locationHintRunnable != null)
        {
            handler.removeCallbacks(locationHintRunnable);
            locationHintRunnable = null;
        }
    }

    /**
     * Shows the location hint suggesting the user try a different location provider.
     * Note: The click listener should be set separately during UI initialization.
     *
     * @param hintView The TextView to display the hint in.
     * @param context  The context used to get the string resource.
     */
    public void showHint(TextView hintView, Context context)
    {
        if (hintView != null && context != null)
        {
            hintView.setText(
                    Html.fromHtml(context.getString(R.string.location_hint_open_settings), Html.FROM_HTML_MODE_LEGACY));
            hintView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hides the location hint.
     *
     * @param hintView The TextView to hide.
     */
    public void hideHint(TextView hintView)
    {
        if (hintView != null)
        {
            hintView.setVisibility(View.GONE);
        }
    }
}
