package com.craxiom.networksurvey.listeners;

import com.craxiom.networksurvey.ConnectionState;

/**
 * Listener interface for those interested in keeping track of the gRPC connection state so they can update themselves appropriately.
 *
 * @since 0.0.4
 */
public interface IGrpcConnectionStateListener
{
    /**
     * Called when the gRPC Connection State changes.
     *
     * @param newConnectionState the new Connection State.
     */
    void onGrpcConnectionStateChange(ConnectionState newConnectionState);
}
