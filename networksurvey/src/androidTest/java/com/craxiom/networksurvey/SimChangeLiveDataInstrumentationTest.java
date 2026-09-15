package com.craxiom.networksurvey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.telephony.TelephonyManager;

import androidx.lifecycle.Observer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SimChangeLiveDataInstrumentationTest
{
    private static final long DELIVERY_TIMEOUT_SECONDS = 5;

    @Test
    public void onReceiveIgnoresNullAndUnrelatedIntents()
    {
        SimChangeReceiver receiver = new SimChangeReceiver();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long sequenceBefore = SimChangeReceiver.getCurrentSimChangeEventSequence();

        receiver.onReceive(context, null);
        receiver.onReceive(context, new Intent("com.craxiom.networksurvey.UNRELATED_ACTION"));

        assertEquals(sequenceBefore, SimChangeReceiver.getCurrentSimChangeEventSequence());
    }

    @Test
    public void validSimStateChangeIsDeferredAndDeliveredOnMainThread() throws InterruptedException
    {
        SimChangeReceiver receiver = new SimChangeReceiver();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicLong subscriptionBaseline = new AtomicLong();
        AtomicReference<Long> deliveredSequence = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicBoolean deliveredBeforeOnReceiveReturned = new AtomicBoolean();
        CountDownLatch delivery = new CountDownLatch(1);
        Observer<Long> observer = sequence -> {
            if (sequence != null && sequence > subscriptionBaseline.get())
            {
                deliveredSequence.set(sequence);
                callbackThread.set(Thread.currentThread());
                delivery.countDown();
            }
        };

        registerObserver(subscriptionBaseline, observer);
        try
        {
            runOnMainThread(() -> {
                receiver.onReceive(context, new Intent("android.intent.action.SIM_STATE_CHANGED"));
                deliveredBeforeOnReceiveReturned.set(delivery.getCount() == 0);
            });

            assertTrue("SIM-change delivery must be queued rather than delivered from onReceive", !deliveredBeforeOnReceiveReturned.get());
            assertTrue("The queued SIM-change event was not delivered", delivery.await(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(deliveredSequence.get() > subscriptionBaseline.get());
            assertSame(Looper.getMainLooper().getThread(), callbackThread.get());
        }
        finally
        {
            removeObserver(observer);
        }
    }

    @Test
    public void subscriberStyleSequenceGateRejectsReplayAndAcceptsNewEvent() throws InterruptedException
    {
        SimChangeReceiver receiver = new SimChangeReceiver();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        runOnMainThread(() -> receiver.onReceive(context, new Intent("android.intent.action.SIM_STATE_CHANGED")));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AtomicLong subscriptionBaseline = new AtomicLong();
        AtomicInteger replayedValuesIgnored = new AtomicInteger();
        AtomicReference<Long> deliveredSequence = new AtomicReference<>();
        CountDownLatch newDelivery = new CountDownLatch(1);
        Observer<Long> observer = sequence -> {
            if (sequence == null)
            {
                return;
            }

            if (sequence <= subscriptionBaseline.get())
            {
                replayedValuesIgnored.incrementAndGet();
                return;
            }

            deliveredSequence.set(sequence);
            newDelivery.countDown();
        };

        registerObserver(subscriptionBaseline, observer);
        try
        {
            assertTrue("LiveData's existing value must be filtered by the subscription baseline", replayedValuesIgnored.get() > 0);

            runOnMainThread(() -> receiver.onReceive(context, new Intent("android.intent.action.SIM_STATE_CHANGED")));

            assertTrue("A SIM-change issued after subscription was not delivered", newDelivery.await(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(deliveredSequence.get() > subscriptionBaseline.get());
        }
        finally
        {
            removeObserver(observer);
        }
    }

    @Test
    public void removingObserverPreventsPendingAndLaterSimChangeDeliveries()
    {
        SimChangeReceiver receiver = new SimChangeReceiver();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicLong subscriptionBaseline = new AtomicLong();
        AtomicInteger deliveries = new AtomicInteger();
        Observer<Long> observer = sequence -> {
            if (sequence != null && sequence > subscriptionBaseline.get())
            {
                deliveries.incrementAndGet();
            }
        };

        runOnMainThread(() -> {
            subscriptionBaseline.set(SimChangeReceiver.getCurrentSimChangeEventSequence());
            SimChangeReceiver.getSimChangeEvents().observeForever(observer);
            receiver.onReceive(context, new Intent("android.intent.action.SIM_STATE_CHANGED"));
            SimChangeReceiver.getSimChangeEvents().removeObserver(observer);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals("Removing the observer must prevent delivery of an already queued event", 0, deliveries.get());

        runOnMainThread(() -> receiver.onReceive(context, new Intent("android.intent.action.SIM_STATE_CHANGED")));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertEquals("Removing the observer must prevent delivery of later events", 0, deliveries.get());
    }

    private static void registerObserver(AtomicLong subscriptionBaseline, Observer<Long> observer)
    {
        runOnMainThread(() -> {
            subscriptionBaseline.set(SimChangeReceiver.getCurrentSimChangeEventSequence());
            SimChangeReceiver.getSimChangeEvents().observeForever(observer);
        });
    }

    private static void removeObserver(Observer<Long> observer)
    {
        runOnMainThread(() -> SimChangeReceiver.getSimChangeEvents().removeObserver(observer));
    }

    private static void runOnMainThread(Runnable runnable)
    {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }
}
