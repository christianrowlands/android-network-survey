package com.craxiom.networksurvey;

import com.craxiom.messaging.phonestate.NetworkType;
import com.craxiom.networksurvey.model.CdrEvent;
import com.craxiom.networksurvey.model.CdrEventType;
import com.craxiom.networksurvey.services.controller.CellularController;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link CdrEvent} model class.
 */
public class CdrEventTest
{
    private static final String DEVICE_ID = "SOMEDEVICEID";
    private static final String TEST_MISSION_ID = "test-mission-123";

    @Test
    public void testEmptyCellIdentityToValidCellIdentityCs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testEmptyCellIdentityToValidCellIdentityPs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-12345-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testEmptyCellIdentityToValidCellIdentityCsAndPs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testValidCellIdentityToEmpty()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertFalse(changed);
    }

    @Test
    public void testNoChange()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertFalse(changed);
    }

    @Test
    public void testOnlyPs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-223-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testOnlyCs()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12344-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertTrue(changed);
    }

    @Test
    public void testOnlyCellIdChangeNoTacChange()
    {
        CdrEvent currentCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        currentCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        currentCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        CdrEvent newCdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE,
                "1112223333", "2223334444", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        newCdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-1");
        newCdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-4");

        boolean changed = currentCdrEvent.locationAreaChanged(newCdrEvent);
        Assert.assertFalse(changed);
    }

    @Test
    public void testHeadersIncludeMissionIdAndRecordNumber()
    {
        String[] headers = CdrEvent.getHeaders();
        Assert.assertEquals("missionId", headers[headers.length - 2]);
        Assert.assertEquals("recordNumber", headers[headers.length - 1]);
    }

    @Test
    public void testCsvRowLengthMatchesHeaders()
    {
        CdrEvent cdrEvent = new CdrEvent(CdrEventType.INCOMING_CALL,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        cdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        cdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

        String[] headers = CdrEvent.getHeaders();
        String[] row = cdrEvent.getCsvRowArray();
        Assert.assertEquals(headers.length, row.length);
    }

    @Test
    public void testMissionIdAndRecordNumberInCsvRow()
    {
        CdrEvent cdrEvent = new CdrEvent(CdrEventType.OUTGOING_CALL,
                "1234567890", "1122334455", CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
        cdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
        cdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");
        cdrEvent.setMissionId(TEST_MISSION_ID);
        cdrEvent.setRecordNumber(42);

        String[] row = cdrEvent.getCsvRowArray();
        Assert.assertEquals(TEST_MISSION_ID, row[row.length - 2]);
        Assert.assertEquals("42", row[row.length - 1]);
    }

    @Test
    public void testMmsEventTypesSerializeToCsv()
    {
        for (CdrEventType eventType : new CdrEventType[]{CdrEventType.INCOMING_MMS, CdrEventType.OUTGOING_MMS})
        {
            CdrEvent cdrEvent = new CdrEvent(eventType, "1234567890", "1122334455;2233445566",
                    CellularController.DEFAULT_SUBSCRIPTION_ID, DEVICE_ID);
            cdrEvent.setCircuitSwitchedInformation(NetworkType.LTE, "310-480-12345-12345678");
            cdrEvent.setPacketSwitchedInformation(NetworkType.LTE, "310-480-222-3");

            String[] row = cdrEvent.getCsvRowArray();
            Assert.assertEquals(eventType.name(), row[5]);
            Assert.assertEquals("1122334455;2233445566", row[7]);
        }
    }
}
