package com.craxiom.networksurvey.model;

import android.location.GnssMeasurement;

import com.craxiom.networksurvey.util.CarrierFreqUtils;
import com.craxiom.networksurvey.util.MathUtils;

import java.util.Objects;

/**
 * Wrapper for {@link GnssMeasurement} as we have multiple listeners updating our unified model, {@link SatelliteStatus}
 *
 * @since 1.5.0
 */
public class GnssMeasurementWrapper
{
    public static final long TIMEOUT_VALUE_NANOS = 4_000_000_000L;
    // using a default value lets us avoid storing nulls in our records
    private static final long TIMED_OUT = Long.MIN_VALUE;

    private final int svId;
    // lets us associate measurement with satellite's nationality
    private final GnssType gnssType;
    private final String carrierFrequencyLabel;

    private volatile double agc;
    private volatile boolean hasAgc;
    private volatile long receivedTimeNanos;

    /**
     * @param svid               from {@link GnssMeasurement#getSvid()}
     * @param gnssType           from {@link GnssMeasurement#getConstellationType()} converted into GnssType
     *                           via {@link com.craxiom.networksurvey.util.GpsTestUtil#getGnssConstellationType(int)}
     * @param carrierFrequencyHz from {@link GnssMeasurement#getCarrierFrequencyHz()}
     */
    public GnssMeasurementWrapper(int svid, GnssType gnssType, float carrierFrequencyHz)
    {
        this.gnssType = gnssType;
        svId = svid;
        carrierFrequencyLabel = CarrierFreqUtils.getCarrierFrequencyLabel(gnssType, svid, MathUtils.toMhz(carrierFrequencyHz));
    }

    public boolean hasAgc()
    {
        return hasAgc;
    }

    public double getAgc()
    {
        return agc;
    }

    public long getReceivedTimeNanos()
    {
        return receivedTimeNanos;
    }

    /**
     * Updates this measurement with new received time and agc values
     *
     * @param measurement Source of our updated data
     */
    public void updateMeasurement(GnssMeasurement measurement)
    {
        receivedTimeNanos = measurement.getReceivedSvTimeNanos();
        hasAgc = measurement.hasAutomaticGainControlLevelDb();

        if (hasAgc)
        {
            agc = measurement.getAutomaticGainControlLevelDb();
        }
    }

    /**
     * Labels this record as outdated
     */
    public void onTimeout()
    {
        receivedTimeNanos = TIMED_OUT;
    }

    /**
     * @return {@code true} if this record is outdated
     */
    public boolean isTimedOut()
    {
        return receivedTimeNanos == TIMED_OUT;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GnssMeasurementWrapper that = (GnssMeasurementWrapper) o;
        return svId == that.svId && Double.compare(that.agc, agc) == 0
                && hasAgc == that.hasAgc
                && receivedTimeNanos == that.receivedTimeNanos
                && gnssType == that.gnssType
                && Objects.equals(carrierFrequencyLabel, that.carrierFrequencyLabel);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(svId, gnssType, carrierFrequencyLabel, agc, hasAgc, receivedTimeNanos);
    }

    /**
     * Generates an id for a hashmap allowing us to treat this class as an updatable record.
     *
     * @param svId               SvId from a GNSS record
     * @param type               Constellation value converted to GnssType
     * @param carrierFrequencyHz Carrier frequency from a GNSS record in Hz
     * @return Concatenation of svId and type
     */
    public static String getId(int svId, GnssType type, float carrierFrequencyHz)
    {
        return svId + type.toString() + CarrierFreqUtils.getCarrierFrequencyLabel(type, svId, MathUtils.toMhz(carrierFrequencyHz));
    }
}
