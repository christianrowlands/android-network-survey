package com.craxiom.networksurvey.models.message;

import com.google.common.base.Objects;

public class PhoneStateModel
{
    private int id;
    private String geom;
    private long time;
    private int recordNumber;
    private double latitude;
    private double longitude;
    private float altitude;
    private String missionId;
    private String simState;
    private String simOperator;
    private String networkRegistration;

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

    public double getLatitude()
    {
        return latitude;
    }

    public void setLatitude(double latitude)
    {
        this.latitude = latitude;
    }

    public double getLongitude()
    {
        return longitude;
    }

    public void setLongitude(double longitude)
    {
        this.longitude = longitude;
    }

    public float getAltitude()
    {
        return altitude;
    }

    public void setAltitude(float altitude)
    {
        this.altitude = altitude;
    }

    public String getMissionId()
    {
        return missionId;
    }

    public void setMissionId(String missionId)
    {
        this.missionId = missionId;
    }

    public String getSimState()
    {
        return simState;
    }

    public void setSimState(String simState)
    {
        this.simState = simState;
    }

    public String getSimOperator()
    {
        return simOperator;
    }

    public void setSimOperator(String simOperator)
    {
        this.simOperator = simOperator;
    }

    public String getNetworkRegistration()
    {
        return networkRegistration;
    }

    public void setNetworkRegistration(String networkRegistration)
    {
        this.networkRegistration = networkRegistration;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhoneStateModel that = (PhoneStateModel) o;
        return id == that.id &&
                time == that.time &&
                recordNumber == that.recordNumber &&
                Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.longitude, longitude) == 0 &&
                Float.compare(that.altitude, altitude) == 0 &&
                Objects.equal(geom, that.geom) &&
                Objects.equal(missionId, that.missionId) &&
                Objects.equal(simState, that.simState) &&
                Objects.equal(simOperator, that.simOperator) &&
                Objects.equal(networkRegistration, that.networkRegistration);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(id, geom, time, recordNumber, latitude, longitude, altitude, missionId, simState, simOperator, networkRegistration);
    }

    @Override
    public String toString()
    {
        return "PhoneStateModel{" +
                "id=" + id +
                ", geom='" + geom + '\'' +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", altitude=" + altitude +
                ", missionId='" + missionId + '\'' +
                ", simState='" + simState + '\'' +
                ", simOperator='" + simOperator + '\'' +
                ", networkRegistration='" + networkRegistration + '\'' +
                '}';
    }

    public static final class PhoneStateModelBuilder
    {
        private int id;
        private String geom;
        private long time;
        private int recordNumber;
        private double latitude;
        private double longitude;
        private float altitude;
        private String missionId;
        private String simState;
        private String simOperator;
        private String networkRegistration;

        public PhoneStateModelBuilder()
        {
        }

        public static PhoneStateModelBuilder newBuilder()
        {
            return new PhoneStateModelBuilder();
        }

        public PhoneStateModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public PhoneStateModelBuilder setGeom(String geom)
        {
            this.geom = geom;
            return this;
        }

        public PhoneStateModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public PhoneStateModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public PhoneStateModelBuilder setLatitude(double latitude)
        {
            this.latitude = latitude;
            return this;
        }

        public PhoneStateModelBuilder setLongitude(double longitude)
        {
            this.longitude = longitude;
            return this;
        }

        public PhoneStateModelBuilder setAltitude(float altitude)
        {
            this.altitude = altitude;
            return this;
        }

        public PhoneStateModelBuilder setMissionId(String missionId)
        {
            this.missionId = missionId;
            return this;
        }

        public PhoneStateModelBuilder setSimState(String simState)
        {
            this.simState = simState;
            return this;
        }

        public PhoneStateModelBuilder setSimOperator(String simOperator)
        {
            this.simOperator = simOperator;
            return this;
        }

        public PhoneStateModelBuilder setNetworkRegistration(String networkRegistration)
        {
            this.networkRegistration = networkRegistration;
            return this;
        }

        public PhoneStateModel build()
        {
            PhoneStateModel model = new PhoneStateModel();
            model.setId(id);
            model.setGeom(geom);
            model.setTime(time);
            model.setRecordNumber(recordNumber);
            model.setLatitude(latitude);
            model.setLongitude(longitude);
            model.setAltitude(altitude);
            model.setMissionId(missionId);
            model.setSimState(simState);
            model.setSimOperator(simOperator);
            model.setNetworkRegistration(networkRegistration);
            return model;
        }
    }
}
