package com.craxiom.networksurvey.model;

import android.location.GnssStatus;

import com.craxiom.networksurvey.util.CarrierFreqUtils;
import com.craxiom.networksurvey.util.MathUtils;

import java.util.Objects;

/**
 * A simple key class that is used to uniquely identify a GNSS constellation and carrier frequency.
 */
public class ConstellationFreqKey
{
    private final int constellationType;
    private final String carrierFrequencyNormalized;
    private final int hash;

    public ConstellationFreqKey(int constellationType, long carrierFrequencyHz)
    {
        this.constellationType = constellationType;
        if (constellationType == GnssStatus.CONSTELLATION_GLONASS)
        {
            // GLONASS frequencies have a pretty wide range, so we use the band label instead.
            // For example, here are the results of GNSS measurement scan, and the AGC class on a
            // Pixel 8 Pro. Notice that the carrier freq ranges from 1598062464 to 1605374976 and
            // they all have the same AGC.
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
            carrierFrequencyNormalized = CarrierFreqUtils.getCarrierFrequencyLabel(GnssType.GLONASS, -1, MathUtils.toMhz(carrierFrequencyHz));
        } else
        {
            carrierFrequencyNormalized = String.valueOf(carrierFrequencyHz / 1_000);
        }
        hash = Objects.hash(constellationType, carrierFrequencyNormalized);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstellationFreqKey that = (ConstellationFreqKey) o;
        return carrierFrequencyNormalized == that.carrierFrequencyNormalized && constellationType == that.constellationType;
    }

    @Override
    public int hashCode()
    {
        return hash;
    }
}
