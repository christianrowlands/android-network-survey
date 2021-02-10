package com.craxiom.networksurvey.helpers;

import java.util.Random;
import java.util.regex.Pattern;

public class TestUtils
{

    public static class Data
    {
        public static int getRandomNumberUsingInts(int min, int max)
        {
            Random random = new Random();
            return random.ints(min, max)
                    .findFirst()
                    .getAsInt();
        }
    }

    public static class Regex
    {

        public static Pattern getMacAddressPattern()
        {
            String regex = "^([0-9A-Fa-f]{2}[:-])"
                    + "{5}([0-9A-Fa-f]{2})|"
                    + "([0-9a-fA-F]{4}\\."
                    + "[0-9a-fA-F]{4}\\."
                    + "[0-9a-fA-F]{4})$";

            return Pattern.compile(regex);
        }
    }
}
