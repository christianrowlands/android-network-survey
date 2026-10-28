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
 * <p>
 * They also cover {@link PreferenceUtils#clampRolloverSizeMb(int)}, which once capped the 250 and 1000 MB
 * choices on the Log files page to 200 MB without saying so.
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

    /**
     * Never roll over (0) and every size the Log files page offers, up to and including 1000 MB, are
     * honored unchanged.
     */
    @Test
    public void clampRolloverSizeMb_offeredChoicesUnchanged()
    {
        for (int sizeMb : new int[]{0, 1, 5, 10, 50, 100, 250, 1000})
        {
            assertEquals(sizeMb, PreferenceUtils.clampRolloverSizeMb(sizeMb));
        }
    }

    /**
     * Sizes above the maximum, such as a larger MDM value, are capped at the maximum.
     */
    @Test
    public void clampRolloverSizeMb_aboveMaximumIsCapped()
    {
        assertEquals(PreferenceUtils.MAX_ROLLOVER_SIZE_MB, PreferenceUtils.clampRolloverSizeMb(1001));
        assertEquals(PreferenceUtils.MAX_ROLLOVER_SIZE_MB, PreferenceUtils.clampRolloverSizeMb(Integer.MAX_VALUE));
    }
}
