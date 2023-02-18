package com.craxiom.networksurvey;

import com.craxiom.messaging.phonestate.NetworkType;
import com.craxiom.networksurvey.model.CdrEvent;
import com.craxiom.networksurvey.model.CdrEventType;

import org.junit.Assert;
import org.junit.Test;

public class CdrEventTest
{
    @Test
    public void testEmptyCellIdentityToValidCellIdentityCs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testEmptyCellIdentityToValidCellIdentityPs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-12345-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testEmptyCellIdentityToValidCellIdentityCsAndPs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testValidCellIdentityToEmpty()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertFalse(changed);
    }

    @Test
    public void testNoChange()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertFalse(changed);
    }

    @Test
    public void testOnlyPs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-223-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testOnlyCs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12344-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testOnlyCellIdChangeNoTacChange()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455");
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-1");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-4");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertFalse(changed);
    }
}
