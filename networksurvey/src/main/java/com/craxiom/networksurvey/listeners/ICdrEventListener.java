package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.model.CdrEvent;

/**
 * Listener interface for those interested in being notified when a new Call Detail Record (CDR) is
 * ready.
 *
 * @since 1.11
 */
public interface ICdrEventListener
{
    /**
     * Called when a new Call Detail Record Event is ready.
     *
     * @param record the CDR Event.
     */
    void onCdrEvent(CdrEvent record);
}
