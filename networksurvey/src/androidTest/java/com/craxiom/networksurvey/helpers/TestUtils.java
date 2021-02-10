package com.craxiom.networksurvey.helpers;

import java.util.Random;

public class TestUtils {

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
}
