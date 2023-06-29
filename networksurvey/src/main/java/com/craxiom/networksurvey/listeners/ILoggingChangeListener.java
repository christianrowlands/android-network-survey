package com.craxiom.networksurvey.listeners;

/**
 * A listener for logging change events.
 *
 * @since 1.10.0
 */
public interface ILoggingChangeListener
{
    /**
     * Notification that the logging has changed on or more of the loggers.
     */
    void onLoggingChanged();
}
