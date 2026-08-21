package com.craxiom.networksurvey.fragments.model;

/**
 * An immutable snapshot of the most recent live NR secondary cell sighting, used to drive the NR
 * Secondary Cell card's idle state after the NR leg detaches.
 * <p>
 * The three values are read and written as one unit so that a reader can never pair a view state
 * with another sighting's timestamp or serving cell. Reading them through separate accessors would
 * allow exactly that, because the survey executor writes new sightings while the main thread can
 * clear them.
 *
 * @param state         The last live (non-idle) view state that was displayed.
 * @param realtimeMs    The {@link android.os.SystemClock#elapsedRealtime()} it was seen at, or -1
 *                      when there is no sighting.
 * @param servingCellId The serving cell the phone was attached to at the time, per
 *                      {@link com.craxiom.networksurvey.util.CellularUtils#getTowerId(com.craxiom.networksurvey.model.ServingCellInfo)}.
 *                      A sighting from a different serving cell is no longer relevant to where the
 *                      user is now, so the card drops it rather than showing it as idle.
 */
public record NrSecondaryCellSighting(NrSecondaryCellViewState state, long realtimeMs,
                                      String servingCellId)
{
    /**
     * A sighting snapshot meaning "no NR secondary cell has been seen".
     */
    public static final NrSecondaryCellSighting NONE = new NrSecondaryCellSighting(null, -1, "");

    /**
     * @param currentServingCellId The serving cell the phone is attached to now.
     * @return True when this sighting can still back the card's idle state, i.e. it exists and was
     * recorded while attached to the same serving cell.
     */
    public boolean isUsableFor(String currentServingCellId)
    {
        return state != null && realtimeMs >= 0 && servingCellId.equals(currentServingCellId);
    }
}
