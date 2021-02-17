package com.craxiom.networksurvey.models.message;

import java.util.Objects;

public class GnssModel
{
    private int id;
    private String geom;
    private long time;
    private int recordNumber;
    private int groupNumber;
    private String constellation;
    private int spaceVehicleId;
    private int carrierFrequencyHz;
    private float latitudeStandardDeviation;
    private float longitudeStandardDeviation;
    private float altitudeStandardDeviation;
    private float agcDb;
    private float cN0;

    @Override
    public String toString()
    {
        return "GnssModel{" +
                "id=" + id +
                ", geom='" + geom + '\'' +
                ", time=" + time +
                ", recordNumber=" + recordNumber +
                ", groupNumber=" + groupNumber +
                ", constellation='" + constellation + '\'' +
                ", spaceVehicleId=" + spaceVehicleId +
                ", carrierFrequencyHz=" + carrierFrequencyHz +
                ", latitudeStandardDeviation=" + latitudeStandardDeviation +
                ", longitudeStandardDeviation=" + longitudeStandardDeviation +
                ", altitudeStandardDeviation=" + altitudeStandardDeviation +
                ", agcDb=" + agcDb +
                ", cN0=" + cN0 +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GnssModel gnssModel = (GnssModel) o;
        return id == gnssModel.id &&
                time == gnssModel.time &&
                recordNumber == gnssModel.recordNumber &&
                groupNumber == gnssModel.groupNumber &&
                spaceVehicleId == gnssModel.spaceVehicleId &&
                carrierFrequencyHz == gnssModel.carrierFrequencyHz &&
                Float.compare(gnssModel.latitudeStandardDeviation, latitudeStandardDeviation) == 0 &&
                Float.compare(gnssModel.longitudeStandardDeviation, longitudeStandardDeviation) == 0 &&
                Float.compare(gnssModel.altitudeStandardDeviation, altitudeStandardDeviation) == 0 &&
                Float.compare(gnssModel.agcDb, agcDb) == 0 &&
                Float.compare(gnssModel.cN0, cN0) == 0 &&
                Objects.equals(geom, gnssModel.geom) &&
                Objects.equals(constellation, gnssModel.constellation);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, geom, time, recordNumber, groupNumber, constellation, spaceVehicleId, carrierFrequencyHz, latitudeStandardDeviation, longitudeStandardDeviation, altitudeStandardDeviation, agcDb, cN0);
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

    public int getGroupNumber()
    {
        return groupNumber;
    }

    public void setGroupNumber(int groupNumber)
    {
        this.groupNumber = groupNumber;
    }

    public String getConstellation()
    {
        return constellation;
    }

    public void setConstellation(String constellation)
    {
        this.constellation = constellation;
    }

    public int getSpaceVehicleId()
    {
        return spaceVehicleId;
    }

    public void setSpaceVehicleId(int spaceVehicleId)
    {
        this.spaceVehicleId = spaceVehicleId;
    }

    public int getCarrierFrequencyHz()
    {
        return carrierFrequencyHz;
    }

    public void setCarrierFrequencyHz(int carrierFrequencyHz)
    {
        this.carrierFrequencyHz = carrierFrequencyHz;
    }

    public float getLatitudeStandardDeviation()
    {
        return latitudeStandardDeviation;
    }

    public void setLatitudeStandardDeviation(float latitudeStandardDeviation)
    {
        this.latitudeStandardDeviation = latitudeStandardDeviation;
    }

    public float getLongitudeStandardDeviation()
    {
        return longitudeStandardDeviation;
    }

    public void setLongitudeStandardDeviation(float longitudeStandardDeviation)
    {
        this.longitudeStandardDeviation = longitudeStandardDeviation;
    }

    public float getAltitudeStandardDeviation()
    {
        return altitudeStandardDeviation;
    }

    public void setAltitudeStandardDeviation(float altitudeStandardDeviation)
    {
        this.altitudeStandardDeviation = altitudeStandardDeviation;
    }

    public float getAgcDb()
    {
        return agcDb;
    }

    public void setAgcDb(float agcDb)
    {
        this.agcDb = agcDb;
    }

    public float getcN0()
    {
        return cN0;
    }

    public void setcN0(float cN0)
    {
        this.cN0 = cN0;
    }

    public static final class GnssModelBuilder
    {
        private int id;
        private String geom;
        private long time;
        private int recordNumber;
        private int groupNumber;
        private String constellation;
        private int spaceVehicleId;
        private int carrierFrequencyHz;
        private float latitudeStandardDeviation;
        private float longitudeStandardDeviation;
        private float altitudeStandardDeviation;
        private float agcDb;
        private float cN0;

        public GnssModelBuilder()
        {
        }

        public static GnssModelBuilder aGnssModel()
        {
            return new GnssModelBuilder();
        }

        public GnssModelBuilder setId(int id)
        {
            this.id = id;
            return this;
        }

        public GnssModelBuilder setGeom(String geom)
        {
            this.geom = geom;
            return this;
        }

        public GnssModelBuilder setTime(long time)
        {
            this.time = time;
            return this;
        }

        public GnssModelBuilder setRecordNumber(int recordNumber)
        {
            this.recordNumber = recordNumber;
            return this;
        }

        public GnssModelBuilder setGroupNumber(int groupNumber)
        {
            this.groupNumber = groupNumber;
            return this;
        }

        public GnssModelBuilder setConstellation(String constellation)
        {
            this.constellation = constellation;
            return this;
        }

        public GnssModelBuilder setSpaceVehicleId(int spaceVehicleId)
        {
            this.spaceVehicleId = spaceVehicleId;
            return this;
        }

        public GnssModelBuilder setCarrierFrequencyHz(int carrierFrequencyHz)
        {
            this.carrierFrequencyHz = carrierFrequencyHz;
            return this;
        }

        public GnssModelBuilder setLatitudeStandardDeviation(float latitudeStandardDeviation)
        {
            this.latitudeStandardDeviation = latitudeStandardDeviation;
            return this;
        }

        public GnssModelBuilder setLongitudeStandardDeviation(float longitudeStandardDeviation)
        {
            this.longitudeStandardDeviation = longitudeStandardDeviation;
            return this;
        }

        public GnssModelBuilder setAltitudeStandardDeviation(float altitudeStandardDeviation)
        {
            this.altitudeStandardDeviation = altitudeStandardDeviation;
            return this;
        }

        public GnssModelBuilder setAgcDb(float agcDb)
        {
            this.agcDb = agcDb;
            return this;
        }

        public GnssModelBuilder setCN0(float cN0)
        {
            this.cN0 = cN0;
            return this;
        }

        public GnssModel build()
        {
            GnssModel gnssModel = new GnssModel();
            gnssModel.setId(id);
            gnssModel.setGeom(geom);
            gnssModel.setTime(time);
            gnssModel.setRecordNumber(recordNumber);
            gnssModel.setGroupNumber(groupNumber);
            gnssModel.setConstellation(constellation);
            gnssModel.setSpaceVehicleId(spaceVehicleId);
            gnssModel.setCarrierFrequencyHz(carrierFrequencyHz);
            gnssModel.setLatitudeStandardDeviation(latitudeStandardDeviation);
            gnssModel.setLongitudeStandardDeviation(longitudeStandardDeviation);
            gnssModel.setAltitudeStandardDeviation(altitudeStandardDeviation);
            gnssModel.setAgcDb(agcDb);
            gnssModel.cN0 = this.cN0;
            return gnssModel;
        }
    }
}
