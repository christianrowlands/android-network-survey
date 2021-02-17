package com.craxiom.networksurvey.models.message;

import java.util.Objects;

public class BluetoothModel
{
    private int id;
    private String geom;
    private long time;
    private int recordNumber;
    private String sourceAddress;
    private String otaDeviceName;
    private String technology;
    private String supportedTechnologies;
    private float txPower;
    private float signalStrength;

    @Override
    public String toString()
    {
        return "BluetoothTableSchemaModel{" +
                "id=" + id +
                ", geom='" + geom + '\'' +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", sourceAddress='" + sourceAddress + '\'' +
                ", otaDeviceName='" + otaDeviceName + '\'' +
                ", technology='" + technology + '\'' +
                ", supportedTechnologies='" + supportedTechnologies + '\'' +
                ", txPower=" + txPower +
                ", signalStrength=" + signalStrength +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BluetoothModel that = (BluetoothModel) o;
        return id == that.id && time == that.time && recordNumber == that.recordNumber && Float.compare(that.txPower, txPower) == 0 && Float.compare(that.signalStrength, signalStrength) == 0 && Objects.equals(geom, that.geom) && Objects.equals(sourceAddress, that.sourceAddress) && Objects.equals(otaDeviceName, that.otaDeviceName) && Objects.equals(technology, that.technology) && Objects.equals(supportedTechnologies, that.supportedTechnologies);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, sourceAddress, otaDeviceName, technology, supportedTechnologies, txPower, signalStrength);
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public String getGeom()
    {
        return geom;
    }

    public void setGeom(String geom)
    {
        this.geom = geom;
    }

    public long getTime()
    {
        return time;
    }

    public void setTime(long time)
    {
        this.time = time;
    }

    public int getRecordNumber()
    {
        return recordNumber;
    }

    public void setRecordNumber(int recordNumber)
    {
        this.recordNumber = recordNumber;
    }

    public String getSourceAddress()
    {
        return sourceAddress;
    }

    public void setSourceAddress(String sourceAddress)
    {
        this.sourceAddress = sourceAddress;
    }

    public String getOtaDeviceName()
    {
        return otaDeviceName;
    }

    public void setOtaDeviceName(String otaDeviceName)
    {
        this.otaDeviceName = otaDeviceName;
    }

    public String getTechnology()
    {
        return technology;
    }

    public void setTechnology(String technology)
    {
        this.technology = technology;
    }

    public String getSupportedTechnologies()
    {
        return supportedTechnologies;
    }

    public void setSupportedTechnologies(String supportedTechnologies)
    {
        this.supportedTechnologies = supportedTechnologies;
    }

    public float getTxPower()
    {
        return txPower;
    }

    public void setTxPower(float txPower)
    {
        this.txPower = txPower;
    }

    public float getSignalStrength()
    {
        return signalStrength;
    }

    public void setSignalStrength(float signalStrength)
    {
        this.signalStrength = signalStrength;
    }

    public static final class BluetoothTableSchemaModelBuilder
    {
        private int id;
        private String geom;
        private long time;
        private int recordNumber;
        private String sourceAddress;
        private String otaDeviceName;
        private String technology;
        private String supportedTechnologies;
        private float txPower;
        private float signalStrength;

        public BluetoothTableSchemaModelBuilder()
        {
        }

        public static BluetoothTableSchemaModelBuilder aBluetoothTableSchemaModel()
        {
            return new BluetoothTableSchemaModelBuilder();
        }

        public BluetoothTableSchemaModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setGeom(String geom)
        {
            this.geom = geom;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setSourceAddress(String sourceAddress)
        {
            this.sourceAddress = sourceAddress;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setOtaDeviceName(String otaDeviceName)
        {
            this.otaDeviceName = otaDeviceName;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setTechnology(String technology)
        {
            this.technology = technology;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setSupportedTechnologies(String supportedTechnologies)
        {
            this.supportedTechnologies = supportedTechnologies;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setTxPower(float txPower)
        {
            this.txPower = txPower;
            return this;
        }

        public BluetoothTableSchemaModelBuilder setSignalStrength(float signalStrength)
        {
            this.signalStrength = signalStrength;
            return this;
        }

        public BluetoothModel build()
        {
            BluetoothModel bluetoothModel = new BluetoothModel();
            bluetoothModel.setId(id);
            bluetoothModel.setGeom(geom);
            bluetoothModel.setTime(time);
            bluetoothModel.setRecordNumber(recordNumber);
            bluetoothModel.setSourceAddress(sourceAddress);
            bluetoothModel.setOtaDeviceName(otaDeviceName);
            bluetoothModel.setTechnology(technology);
            bluetoothModel.setSupportedTechnologies(supportedTechnologies);
            bluetoothModel.setTxPower(txPower);
            bluetoothModel.setSignalStrength(signalStrength);
            return bluetoothModel;
        }
    }
}
