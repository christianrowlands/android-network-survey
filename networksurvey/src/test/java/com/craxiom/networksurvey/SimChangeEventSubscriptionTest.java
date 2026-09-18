package com.craxiom.networksurvey;

import android.content.Intent;
import android.os.Looper;
import android.telephony.TelephonyManager;

import androidx.lifecycle.Observer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SimChangeEventSubscriptionTest
{
    private final List<Observer<Long>> registeredObservers = new ArrayList<>();

    @Before
    public void drainMainLooper()
    {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void removeRegisteredObservers()
    {
        for (Observer<Long> observer : registeredObservers)
        {
            SimChangeReceiver.getSimChangeEvents().removeObserver(observer);
        }
        registeredObservers.clear();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void observerSubscribedBeforeEmissionAcceptsSequence()
    {
        Subscription subscription = subscribeFromCurrentSequence();

        sendSimStateChanged();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, subscription.sequences.size());
        assertTrue(subscription.sequences.get(0) > subscription.baseline);
    }

    @Test
    public void observerSubscribedAfterEmissionRejectsReplayedSequence()
    {
        sendSimStateChanged();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Subscription subscription = subscribeFromCurrentSequence();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue(subscription.sequences.isEmpty());
    }

    @Test
    public void distinctQueuedEmissionsAreNotCoalesced()
    {
        Subscription subscription = subscribeFromCurrentSequence();

        sendSimStateChanged();
        sendSimStateChanged();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(2, subscription.sequences.size());
        assertEquals(subscription.sequences.get(0) + 1L, subscription.sequences.get(1).longValue());
    }

    @Test
    public void removingObserverPreventsPendingAndSubsequentDelivery()
    {
        Subscription subscription = subscribeFromCurrentSequence();

        sendSimStateChanged();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, subscription.sequences.size());

        sendSimStateChanged();
        SimChangeReceiver.getSimChangeEvents().removeObserver(subscription.observer);
        registeredObservers.remove(subscription.observer);
        sendSimStateChanged();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, subscription.sequences.size());
    }

    private Subscription subscribeFromCurrentSequence()
    {
        final long baseline = SimChangeReceiver.getCurrentSimChangeEventSequence();
        final List<Long> sequences = new ArrayList<>();
        Observer<Long> observer = new Observer<Long>()
        {
            @Override
            public void onChanged(Long sequence)
            {
                if (sequence != null && sequence > baseline)
                {
                    sequences.add(sequence);
                }
            }
        };

        SimChangeReceiver.getSimChangeEvents().observeForever(observer);
        registeredObservers.add(observer);
        return new Subscription(baseline, observer, sequences);
    }

    private void sendSimStateChanged()
    {
        new SimChangeReceiver().onReceive(null, new Intent("android.intent.action.SIM_STATE_CHANGED"));
    }

    private static final class Subscription
    {
        private final long baseline;
        private final Observer<Long> observer;
        private final List<Long> sequences;

        private Subscription(long baseline, Observer<Long> observer, List<Long> sequences)
        {
            this.baseline = baseline;
            this.observer = observer;
            this.sequences = sequences;
        }
    }
}
