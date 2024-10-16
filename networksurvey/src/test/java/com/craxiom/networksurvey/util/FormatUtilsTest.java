package com.craxiom.networksurvey.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatUtilsTest
{
    @Test
    public void formatSpeed_withSpeed()
    {
        float inputSpeed = 15.5f;
        float formattedSpeed = FormatUtils.formatSpeed(inputSpeed);
        assertEquals(15.5f, formattedSpeed, 0.0f);
    }

    @Test
    public void formatSpeed_withLowSpeed()
    {
        float inputSpeed = 0.5f;
        float formattedSpeed = FormatUtils.formatSpeed(inputSpeed);
        assertEquals(0.5f, formattedSpeed, 0.0f);
    }

    @Test
    public void formatSpeed_withoutSpeed()
    {
        float inputSpeed = 10000000f;
        float formattedSpeed = FormatUtils.formatSpeed(inputSpeed);
        assertEquals(10000000f, formattedSpeed, 0.0f);
    }

    @Test
    public void formatSpeed_smallSpeed()
    {
        float inputSpeed = 0.000000000000082499864f;
        float formattedSpeed = FormatUtils.formatSpeed(inputSpeed);
        assertEquals(0.0f, formattedSpeed, 0.0f);

        String floatString = Float.toString(formattedSpeed);
        assertEquals("0.0", floatString);
    }
}

