package com.craxiom.networksurvey.listeners;

/**
 * A listener for Mission ID changes.
 * <p>
 * The Mission ID identifies a single survey session. A new Mission ID is generated when the
 * first mission relevant survey of a session starts, and the same value is reused until all
 * mission relevant surveys stop. Listeners are notified when the Mission ID rolls to a new value
 * and when the mission session ends.
 */
public interface IMissionIdListener
{
    /**
     * Notification that the current Mission ID or the mission session state has changed.
     *
     * @param missionId            The current rolled Mission ID. This is non-null once the first
     *                             survey of the app session has started.
     * @param missionSessionActive True while at least one mission relevant survey is running,
     *                             false once they have all stopped (the missionId is retained as
     *                             the most recent value).
     */
    void onMissionIdChanged(String missionId, boolean missionSessionActive);
}
