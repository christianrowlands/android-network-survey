package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.messaging.LteRecord;

/**
 * Listener interface for those interested in being notified when a new Survey Record is ready.
 *
 * @since 0.0.4
 */
public interface ISurveyRecordListener
{
    /**
     * Called when a new LTE Survey Record is ready.
     *
     * @param lteRecord the LTE Record.
     */
    void onLteSurveyRecord(LteRecord lteRecord);
}
