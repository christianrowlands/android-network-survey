package com.craxiom.networksurvey;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.messaging.bluetooth.Technology;
import com.craxiom.networksurvey.fragments.model.BluetoothViewModel;
import com.craxiom.networksurvey.model.SortedSet;
import com.craxiom.networksurvey.util.NsUtils;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;

/**
 * Test for the custom {@link com.craxiom.networksurvey.model.SortedSet} class.
 *
 * @since 1.0.0
 */
public class SortedSetTest
{
    private static final double FLOAT_TOLERANCE = 0.0001;

    @Test
    public void validateRemovalOfOldMatchingItem()
    {
        // Two records with the same source addresses
        final String matchingSourceAddress = "E1:A1:19:A9:68:B0";
        final BluetoothRecord record1 = getFakeBluetoothRecord(matchingSourceAddress, -71f);
        final BluetoothRecord matchingRecord = getFakeBluetoothRecord(matchingSourceAddress, -81f);

        // Records with different source addresses
        final BluetoothRecord record2 = getFakeBluetoothRecord("E1:A1:19:A9:68:B2", -72f);
        final BluetoothRecord record3 = getFakeBluetoothRecord("E1:A1:19:A9:68:B3", -73f);
        final BluetoothRecord record4 = getFakeBluetoothRecord("E1:A1:19:A9:68:B4", -74f);

        SortedSet<BluetoothRecord> bluetoothRecordSortedSet = null;
        try
        {
            final BluetoothViewModel bluetoothViewModel = new BluetoothViewModel();
            Field bluetoothRecordSortedSetField = BluetoothViewModel.class.getDeclaredField("bluetoothSortedList");
            bluetoothRecordSortedSetField.setAccessible(true);
            bluetoothRecordSortedSet = (SortedSet) bluetoothRecordSortedSetField.get(bluetoothViewModel);
        } catch (NoSuchFieldException | IllegalAccessException e)
        {
            Assert.fail("Could not get the bluetoothSortedSet field from the BluetoothViewModel class");
        }

        bluetoothRecordSortedSet.add(record1);
        Assert.assertEquals(1, bluetoothRecordSortedSet.size());

        bluetoothRecordSortedSet.add(record2);
        Assert.assertEquals(2, bluetoothRecordSortedSet.size());

        // The old record should be removed and the new one present
        Assert.assertEquals(-71f, bluetoothRecordSortedSet.get(0).getData().getSignalStrength().getValue(), FLOAT_TOLERANCE);
        bluetoothRecordSortedSet.add(matchingRecord);
        Assert.assertEquals(2, bluetoothRecordSortedSet.size());
        // Use index 1 because record2 will be at the top since its RSSI value (-72) is now stronger than the matching record's -81
        Assert.assertEquals(-81f, bluetoothRecordSortedSet.get(1).getData().getSignalStrength().getValue(), FLOAT_TOLERANCE);

        bluetoothRecordSortedSet.add(record3);
        Assert.assertEquals(3, bluetoothRecordSortedSet.size());

        bluetoothRecordSortedSet.add(record4);
        Assert.assertEquals(4, bluetoothRecordSortedSet.size());

        bluetoothRecordSortedSet.add(record1);
        Assert.assertEquals(4, bluetoothRecordSortedSet.size());
        Assert.assertEquals(-71f, bluetoothRecordSortedSet.get(0).getData().getSignalStrength().getValue(), FLOAT_TOLERANCE);

        bluetoothRecordSortedSet.add(record4);
        Assert.assertEquals(4, bluetoothRecordSortedSet.size());
    }

    /**
     * Create a fake BluetoothRecord that can be used for testing.
     * <p>
     * Note that the time used for the record is the current time.
     *
     * @param sourceAddress  The source address, which is important because it is used to determine if two records are the same.
     * @param signalStrength The signal strength which can be used to know if the new record replaces the old one.
     * @return The new Bluetooth record.
     */
    private BluetoothRecord getFakeBluetoothRecord(String sourceAddress, float signalStrength)
    {
        final BluetoothRecord.Builder recordBuilder = BluetoothRecord.newBuilder();
        recordBuilder.setVersion("0.4.0");
        recordBuilder.setMessageType("BluetoothRecord");

        final BluetoothRecordData.Builder dataBuilder = BluetoothRecordData.newBuilder();
        dataBuilder.setDeviceSerialNumber("ee4d453e4c6f73fa");
        dataBuilder.setDeviceName("BT Pixel");
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setLatitude(51.470334);
        dataBuilder.setLongitude(-0.486594);
        dataBuilder.setAltitude(184.08124f);
        dataBuilder.setMissionId("NS ee4d453e4c6f73fa 20210114-124535");
        dataBuilder.setRecordNumber(1);
        dataBuilder.setSourceAddress(sourceAddress);
        dataBuilder.setDestinationAddress("56:14:62:0D:98:01");
        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());
        dataBuilder.setTxPower(FloatValue.newBuilder().setValue(8f).build());
        dataBuilder.setTechnology(Technology.LE);
        dataBuilder.setSupportedTechnologies(SupportedTechnologies.DUAL);
        dataBuilder.setOtaDeviceName("846B2162E22433AFE9");
        dataBuilder.setChannel(Int32Value.newBuilder().setValue(6).build());

        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }
}
