package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.GnssRecord;

/**
 * Listener interface for those interested in being notified when a new GNSS Survey Record is ready.
 *
 * @since 0.3.0
 */
public interface IGnssSurveyRecordListener
{
    /**
     * Called when a new GNSS Survey Record is ready.
     *
     * @param gnssRecord the GNSS Record.
     */
    void onGnssSurveyRecord(GnssRecord gnssRecord);
}
