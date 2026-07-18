package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;

import org.junit.Test;

/**
 * Unit tests for {@link PreferenceUtils} helper logic that does not require an Android context.
 * <p>
 * These cover {@link PreferenceUtils#clampStreamingQueueLimit(int)}, the pure normalization step behind
 * {@code getStreamingQueueLimit()}. This is the logic where a previous regression silently clamped an
 * explicit 0 (the documented "disable / unbounded queue" value) up to the default, defeating the feature.
 */
public class PreferenceUtilsTest
{
    /**
     * An explicit 0 must be honored as "disabled" and returned unchanged so the enforcement layers use an
     * unbounded queue, matching the Settings UI and MDM descriptions.
     */
    @Test
    public void clampStreamingQueueLimit_zeroIsDisabled()
    {
        assertEquals(NetworkSurveyConstants.STREAMING_QUEUE_LIMIT_DISABLED,
                PreferenceUtils.clampStreamingQueueLimit(0));
    }

    /**
     * Negative values are outside the valid range and must fall back to the safe default rather than being
     * treated as "disable", so garbage input cannot silently create an unbounded queue.
     */
    @Test
    public void clampStreamingQueueLimit_negativeFallsBackToDefault()
    {
        assertEquals(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT,
                PreferenceUtils.clampStreamingQueueLimit(-1));
        assertEquals(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT,
                PreferenceUtils.clampStreamingQueueLimit(-100000));
    }

    /**
     * Positive values within range pass through unchanged, including the exact default value.
     */
    @Test
    public void clampStreamingQueueLimit_positiveWithinRangeUnchanged()
    {
        assertEquals(50, PreferenceUtils.clampStreamingQueueLimit(50));
        assertEquals(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT,
                PreferenceUtils.clampStreamingQueueLimit(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT));
    }

    /**
     * The maximum boundary value passes through unchanged; values above it are capped at the maximum.
     */
    @Test
    public void clampStreamingQueueLimit_aboveMaximumIsCapped()
    {
        // 100,000 is the documented maximum (MAX_STREAMING_QUEUE_LIMIT).
        assertEquals(100_000, PreferenceUtils.clampStreamingQueueLimit(100_000));
        assertEquals(100_000, PreferenceUtils.clampStreamingQueueLimit(200_000));
    }
}
