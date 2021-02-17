package com.craxiom.networksurvey.models.tableschemas;

import java.util.Objects;

public class MessageTableSchema
{
    private int cid;
    private String name;
    private String type;
    private int notNull;
    private int defaultValue;
    private int primaryKey;

    @Override
    public String toString()
    {
        return "MessageTableSchemaModel{" +
                "cid=" + cid +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", notNull=" + notNull +
                ", defaultValue=" + defaultValue +
                ", primaryKey=" + primaryKey +
                '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageTableSchema that = (MessageTableSchema) o;
        return cid == that.cid &&
                notNull == that.notNull &&
                defaultValue == that.defaultValue &&
                primaryKey == that.primaryKey &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(cid, name, type, notNull, defaultValue, primaryKey);
    }

    public int getCid()
    {
        return cid;
    }

    public void setCid(int cid)
    {
        this.cid = cid;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public int getNotNull()
    {
        return notNull;
    }

    public void setNotNull(int notNull)
    {
        this.notNull = notNull;
    }

    public int getDefaultValue()
    {
        return defaultValue;
    }

    public void setDefaultValue(int defaultValue)
    {
        this.defaultValue = defaultValue;
    }

    public int getPrimaryKey()
    {
        return primaryKey;
    }

    public void setPrimaryKey(int primaryKey)
    {
        this.primaryKey = primaryKey;
    }

    public static final class MessageTableSchemaModelBuilder
    {
        private int cid;
        private String name;
        private String type;
        private int notNull;
        private int defaultValue;
        private int primaryKey;

        public MessageTableSchemaModelBuilder()
        {
        }

        public static MessageTableSchemaModelBuilder messageTableSchemaModel()
        {
            return new MessageTableSchemaModelBuilder();
        }

        public MessageTableSchemaModelBuilder setCid(int cid)
        {
            this.cid = cid;
            return this;
        }

        public MessageTableSchemaModelBuilder setName(String name)
        {
            this.name = name;
            return this;
        }

        public MessageTableSchemaModelBuilder setType(String type)
        {
            this.type = type;
            return this;
        }

        public MessageTableSchemaModelBuilder setNotNull(int notNull)
        {
            this.notNull = notNull;
            return this;
        }

        public MessageTableSchemaModelBuilder setDefaultValue(int defaultValue)
        {
            this.defaultValue = defaultValue;
            return this;
        }

        public MessageTableSchemaModelBuilder setPrimaryKey(int primaryKey)
        {
            this.primaryKey = primaryKey;
            return this;
        }

        public MessageTableSchema build()
        {
            MessageTableSchema messageTableSchema = new MessageTableSchema();
            messageTableSchema.setCid(cid);
            messageTableSchema.setName(name);
            messageTableSchema.setType(type);
            messageTableSchema.setNotNull(notNull);
            messageTableSchema.setDefaultValue(defaultValue);
            messageTableSchema.setPrimaryKey(primaryKey);
            return messageTableSchema;
        }
    }
}
