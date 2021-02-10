package com.craxiom.networksurvey.helpers.models.tableschemas;

import java.util.Objects;

public class GnssTableSchemaModel {
    private int cid;

    @Override
    public String toString() {
        return "GnssTableSchemaModel{" +
                "cid=" + cid +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", notNull=" + notNull +
                ", defaultValue=" + defaultValue +
                ", primaryKey=" + primaryKey +
                '}';
    }

    private String name;
    private String type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GnssTableSchemaModel that = (GnssTableSchemaModel) o;
        return cid == that.cid &&
                notNull == that.notNull &&
                defaultValue == that.defaultValue &&
                primaryKey == that.primaryKey &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cid, name, type, notNull, defaultValue, primaryKey);
    }

    private int notNull;
    private int defaultValue;

    public int getCid() {
        return cid;
    }

    public void setCid(int cid) {
        this.cid = cid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getNotNull() {
        return notNull;
    }

    public void setNotNull(int notNull) {
        this.notNull = notNull;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(int defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(int primaryKey) {
        this.primaryKey = primaryKey;
    }

    private int primaryKey;

    public static final class GnssTableSchemaModelBuilder {
        private int cid;
        private String name;
        private String type;
        private int notNull;
        private int defaultValue;
        private int primaryKey;

        public GnssTableSchemaModelBuilder() {
        }

        public static GnssTableSchemaModelBuilder aGnssTableSchemaModel() {
            return new GnssTableSchemaModelBuilder();
        }

        public GnssTableSchemaModelBuilder setCid(int cid) {
            this.cid = cid;
            return this;
        }

        public GnssTableSchemaModelBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public GnssTableSchemaModelBuilder setType(String type) {
            this.type = type;
            return this;
        }

        public GnssTableSchemaModelBuilder setNotNull(int notNull) {
            this.notNull = notNull;
            return this;
        }

        public GnssTableSchemaModelBuilder setDefaultValue(int defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public GnssTableSchemaModelBuilder setPrimaryKey(int primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public GnssTableSchemaModel build() {
            GnssTableSchemaModel gnssTableSchemaModel = new GnssTableSchemaModel();
            gnssTableSchemaModel.setCid(cid);
            gnssTableSchemaModel.setName(name);
            gnssTableSchemaModel.setType(type);
            gnssTableSchemaModel.setNotNull(notNull);
            gnssTableSchemaModel.setDefaultValue(defaultValue);
            gnssTableSchemaModel.setPrimaryKey(primaryKey);
            return gnssTableSchemaModel;
        }
    }
}
