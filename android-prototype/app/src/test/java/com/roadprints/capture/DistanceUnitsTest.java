package com.roadprints.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DistanceUnitsTest {
    @Test public void formatsDistancesInSelectedUnitsWithReadablePrecision() {
        assertEquals("75.9 mi", DistanceUnits.format(122136, false));
        assertEquals("122.1 km", DistanceUnits.format(122136, true));
        assertEquals("0.1 mi", DistanceUnits.format(100, false));
        assertEquals("2,251", DistanceUnits.formatPointCount(2251));
    }
}
