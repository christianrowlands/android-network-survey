package com.craxiom.networksurvey.screens;

import com.craxiom.networksurvey.R;

import static com.schibsted.spain.barista.interaction.BaristaClickInteractions.clickOn;

public class BottomMenuBar
{
    public static void clickWifiMenuOption()
    {
        clickOn(R.id.wifi);
    }

    public static void clickGnssMenuOption()
    {
        clickOn(R.id.gnss);
    }

    public static void clickCellularMenuOption()
    {
        clickOn(R.id.cellular);
    }

    public static void clickBluetoothMenuOption()
    {
        clickOn(R.id.bluetooth);
    }
}
