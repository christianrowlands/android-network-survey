package com.craxiom.networksurvey.fragments.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link NrSecondaryCellSighting}, which decides whether a retained NR secondary cell
 * sighting is still relevant enough to back the NR Secondary Cell card's idle state.
 */
public class NrSecondaryCellSightingTest
{
    private static final String TOWER_A = "3112340012345678";
    private static final String TOWER_B = "3112340087654321";

    private static NrSecondaryCellViewState state()
    {
        return new NrSecondaryCellViewState("n77 (TD 3700)", "3900.000 MHz", "123 (0/41)",
                "660000", -95, -11, 12, false);
    }

    @Test
    public void isUsableFor_sameServingCell()
    {
        assertTrue(new NrSecondaryCellSighting(state(), 5_000, TOWER_A).isUsableFor(TOWER_A));
    }

    @Test
    public void isUsableFor_differentServingCellIsNotUsable()
    {
        // The user moved to another tower, so the held cell describes somewhere they have left.
        assertFalse(new NrSecondaryCellSighting(state(), 5_000, TOWER_A).isUsableFor(TOWER_B));
    }

    @Test
    public void isUsableFor_noSightingIsNotUsable()
    {
        assertFalse(NrSecondaryCellSighting.NONE.isUsableFor(TOWER_A));
        assertFalse(NrSecondaryCellSighting.NONE.isUsableFor(""));
    }

    @Test
    public void isUsableFor_missingStateOrTimestampIsNotUsable()
    {
        assertFalse(new NrSecondaryCellSighting(null, 5_000, TOWER_A).isUsableFor(TOWER_A));
        assertFalse(new NrSecondaryCellSighting(state(), -1, TOWER_A).isUsableFor(TOWER_A));
    }

    @Test
    public void isUsableFor_unknownServingCellDoesNotMatchKnownOne()
    {
        // An empty ID means the scan had no serving cell record. It must not be treated as
        // matching a real tower, or the card would hold a sighting across a service drop.
        assertFalse(new NrSecondaryCellSighting(state(), 5_000, TOWER_A).isUsableFor(""));
        assertFalse(new NrSecondaryCellSighting(state(), 5_000, "").isUsableFor(TOWER_A));
    }

    @Test
    public void asIdle_preservesValuesAndSetsIdle()
    {
        final NrSecondaryCellViewState idle = state().asIdle();

        assertTrue(idle.idle());
        assertFalse(state().idle());
        assertTrue(idle.band().equals(state().band())
                && idle.frequency().equals(state().frequency())
                && idle.pci().equals(state().pci())
                && idle.narfcn().equals(state().narfcn())
                && idle.ssRsrp().equals(state().ssRsrp())
                && idle.ssRsrq().equals(state().ssRsrq())
                && idle.ssSinr().equals(state().ssSinr()));
    }
}
