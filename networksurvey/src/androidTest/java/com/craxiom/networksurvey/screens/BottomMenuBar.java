package com.craxiom.networksurvey.screens;

import com.craxiom.networksurvey.R;

import static com.schibsted.spain.barista.interaction.BaristaClickInteractions.clickOn;

public class BottomMenuBar
{
    public static void clickWifiMenuOption()
    {
        clickOn(R.id.main_wifi_fragment);
    }

    public static void clickGnssMenuOption()
    {
        clickOn(R.id.main_gnss_fragment);
    }

    public static void clickCellularMenuOption()
    {
        clickOn(R.id.main_cellular_fragment);
    }

    public static void clickBluetoothMenuOption()
    {
        clickOn(R.id.main_bluetooth_fragment);
    }
}
