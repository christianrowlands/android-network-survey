package com.craxiom.networksurvey.services.controller;

import com.craxiom.networksurvey.listeners.ILiveScanView;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import timber.log.Timber;

/**
 * A base class for all of the protocol controllers that the Network Survey Service uses to control
 * the various survey protocols.
 * <p>
 * It also tracks the {@link ILiveScanView}s open for the controller's radio. While one is open the
 * radio may scan at the live view's shorter interval (see {@link #getEffectiveScanRateMs()}); the
 * survey's configured interval, {@link #getScanRateMs()}, never changes.
 */
public abstract class AController
{
    protected NetworkSurveyService surveyService;
    protected ExecutorService executorService;
    protected volatile boolean isPaused = false;

    // Each open live view and the interval it asked for.
    private final Map<ILiveScanView, Integer> liveViews = new ConcurrentHashMap<>();

    public AController(NetworkSurveyService surveyService, ExecutorService executorService)
    {
        this.surveyService = surveyService;
        this.executorService = executorService;
    }

    public void onDestroy()
    {
        liveViews.clear();
        surveyService = null;
        executorService = null;
    }

    /**
     * @return The survey's scan interval for this radio in milliseconds, as configured by the user,
     * an organization, or the defaults. Live views never change it.
     */
    public abstract int getScanRateMs();

    /**
     * Records that a live view of this radio is open. Adding a view that is already open does nothing.
     *
     * @param view The live view.
     */
    public void addLiveView(ILiveScanView view)
    {
        if (liveViews.put(view, view.getLiveScanIntervalMs()) == null) onLiveViewsChanged();
    }

    /**
     * Records that a live view of this radio closed. Removing a view that is not open does nothing.
     *
     * @param view The live view.
     */
    public void removeLiveView(ILiveScanView view)
    {
        if (liveViews.remove(view) != null) onLiveViewsChanged();
    }

    /**
     * @return True while at least one live view of this radio is open.
     */
    public boolean hasLiveViews()
    {
        return !liveViews.isEmpty();
    }

    /**
     * @return The scan interval the radio runs at right now, in milliseconds: the shortest interval
     * an open live view asks for when that is shorter than {@link #getScanRateMs()} and
     * {@link #canScanFaster()} allows it, otherwise {@link #getScanRateMs()}.
     */
    public int getEffectiveScanRateMs()
    {
        final int configuredMs = getScanRateMs();
        int liveMs = Integer.MAX_VALUE;
        for (int intervalMs : liveViews.values())
        {
            liveMs = Math.min(liveMs, intervalMs);
        }

        if (liveMs >= configuredMs || !canScanFaster()) return configuredMs;
        return liveMs;
    }

    /**
     * @return True while the radio scans faster than {@link #getScanRateMs()} for a live view.
     */
    public boolean isScanningFaster()
    {
        return getEffectiveScanRateMs() < getScanRateMs();
    }

    /**
     * @return Whether the platform lets this radio scan faster than its survey interval right now.
     * Subclasses override this when it does not always (Wi-Fi scan throttling).
     */
    protected boolean canScanFaster()
    {
        return true;
    }

    /**
     * Called when a live view opens or closes, so the controller can move its scanning to the new
     * {@link #getEffectiveScanRateMs()}. Called on the thread that registered or unregistered the
     * view.
     */
    protected void onLiveViewsChanged()
    {
    }

    /**
     * Tells the screens how often the radio scans right now (see {@link LiveScanRates}). Called when
     * scanning starts, stops, pauses, resumes, or changes interval. Controllers that scan faster for
     * live views override it.
     */
    protected void publishLiveScanRate()
    {
    }

    /**
     * Pauses scanning operations without releasing resources or changing configuration.
     * This is used for battery management to temporarily halt operations.
     * Subclasses should override this to pause their specific scanning operations.
     */
    public void pauseScanning()
    {
        isPaused = true;
        publishLiveScanRate();
        Timber.i("%s scanning paused", getClass().getSimpleName());
    }

    /**
     * Resumes scanning operations that were previously paused.
     * This only resumes scanning if it was active before the pause.
     * Subclasses should override this to resume their specific scanning operations.
     */
    public void resumeScanning()
    {
        isPaused = false;
        publishLiveScanRate();
        Timber.i("%s scanning resumed", getClass().getSimpleName());
    }

    /**
     * Checks if scanning is currently paused.
     *
     * @return true if scanning is paused, false otherwise.
     */
    public boolean isPaused()
    {
        return isPaused;
    }

    /**
     * Wraps the execute command for the executor service in a try catch to prevent the app from crashing if something
     * goes wrong with submitting the runnable. The most common crash I am seeing seems to be from the executor service
     * shutting down but some scan results are coming in. Hopefully that is the only case because otherwise we are
     * losing some survey results.
     *
     * @param runnable The runnable to execute on the executor service.
     */
    protected void execute(Runnable runnable)
    {
        try
        {
            executorService.execute(runnable);
        } catch (Throwable t)
        {
            Timber.w(t, "Could not submit to the executor service");
        }
    }
}
