package com.craxiom.networksurvey.helpers;

import android.os.Build;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

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

    public static class System
    {
        public static boolean areSystemAnimationsEnabled() {
            float duration, transition;
            duration = Settings.Global.getFloat(
                        InstrumentationRegistry.getInstrumentation().getContext().getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, 1);
            transition = Settings.Global.getFloat(
                        InstrumentationRegistry.getInstrumentation().getContext().getContentResolver(),
                        Settings.Global.TRANSITION_ANIMATION_SCALE, 1);
            return (duration != 0 && transition != 0);
        }
    }
}
