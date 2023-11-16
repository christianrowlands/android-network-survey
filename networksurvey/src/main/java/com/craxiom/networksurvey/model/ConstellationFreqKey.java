package com.craxiom.networksurvey.model;

import android.location.GnssStatus;

import java.util.Objects;

/**
 * A simple key class that is used to uniquely identify a GNSS constellation and carrier frequency.
 */
public class ConstellationFreqKey
{
    private final int constellationType;
    private final long carrierFrequencyTrimmed;
    private final int hash;

    public ConstellationFreqKey(int constellationType, long carrierFrequency)
    {
        this.constellationType = constellationType;
        if (constellationType == GnssStatus.CONSTELLATION_GLONASS)
        {
            // GLONASS frequencies have a pretty wide range, so we trim them down to 10 MHz bins
            // Even this is not enough, but I am nervous going any farther. For example, here
            // are the results of GNSS measurement scan, and the AGC class on a Pixel 8 Pro. Notice
            // that the carier freq ranges from 1598062464 to 1605374976 and they all have the same AGC.
            //
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1598062464, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1600312448, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1600875008, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1601437440, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1602562560, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1603124992, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1603687552, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1604812544, AGC=47.18083190917969
            // GnssMeasurement: Constellation=GLONASS, CarrierFreq=1605374976, AGC=47.18083190917969
            //
            // GnssAutomaticGainControl: Constellation=GLONASS, CarrierFreq=1602000000, AGC=46.839942932128906
            carrierFrequencyTrimmed = carrierFrequency / 10_000_000;
        } else
        {
            carrierFrequencyTrimmed = carrierFrequency / 1_000;
        }
        hash = Objects.hash(constellationType, carrierFrequencyTrimmed);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstellationFreqKey that = (ConstellationFreqKey) o;
        return carrierFrequencyTrimmed == that.carrierFrequencyTrimmed && constellationType == that.constellationType;
    }

    @Override
    public int hashCode()
    {
        return hash;
    }
}
