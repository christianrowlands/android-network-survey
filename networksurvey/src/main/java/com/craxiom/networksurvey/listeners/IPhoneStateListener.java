package com.craxiom.networksurvey.listeners;

import com.craxiom.messaging.PhoneState;

/**
 * Listener interface for those interested in being notified whenever a new Phone State message is
 * ready. This typically indicates that the state of the phone changed, such as a serving cell
 * change, SIM state change, or network registration update.
 */
public interface IPhoneStateListener
{
    /**
     * Notification that a new Phone State message is ready.
     *
     * @param phoneState The Phone State message.
     */
    void onPhoneState(PhoneState phoneState);
}
