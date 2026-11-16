package com.roadprints.capture;

import android.location.Location;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JourneyOriginRecoveryTest {
    private static final long START = 1_700_000_000_000L;

    @Test public void walkingRunningCyclingAndRailUseOnlyMeasuredEarlyFixes() {
        assertRecovered("walking", 17, 34, 51, 1.7f);
        assertRecovered("running", 30, 60, 90, 3f);
        assertRecovered("cycling", 36, 72, 108, 3.6f);
        assertRecovered("train", 70, 150, 280, 10f);
    }

    private void assertRecovered(String mode, int early, int recognised, int later, float speed) {
        JourneyOriginRecovery recovery = new JourneyOriginRecovery();
        recovery.observe(fix(0, 0, 0));
        Location departure = fix(10, 0, 0);
        Location earlyFix = fix(20, early, speed);
        recovery.observe(departure);
        recovery.observe(earlyFix);
        // A duplicate callback for the same observation must not duplicate the prefix.
        recovery.observe(new Location(earlyFix));
        Location seed = fix(30, recognised, speed);
        recovery.observe(seed);

        JourneyOriginRecovery.Result result = recovery.recover(Arrays.asList(seed, fix(40, later, speed)), mode, START + 40_000);
        assertEquals("recovered", result.status);
        assertEquals(2, result.prefix.size());
        assertFix(departure, result.prefix.get(0));
        assertFix(earlyFix, result.prefix.get(1));
    }

    @Test public void lateAcquisitionAndInaccurateEarlyCallbacksAreDistinguishable() {
        JourneyOriginRecovery recovery = new JourneyOriginRecovery();
        List<Location> seed = Arrays.asList(fix(90, 200, 10), fix(100, 300, 10));
        assertEquals("no_earlier_location_callback", recovery.recover(seed, "unknown", START + 100_000).status);
        Location uncertain = fix(80, 50, 0);
        uncertain.setAccuracy(120);
        recovery.observe(uncertain);
        assertEquals("no_earlier_usable_fix", recovery.recover(seed, "unknown", START + 100_000).status);
    }

    @Test public void neverJoinsAcrossSamplingGapOrAnUnrelatedWalkingLeg() {
        JourneyOriginRecovery recovery = new JourneyOriginRecovery();
        recovery.observe(fix(0, 0, 0));
        recovery.observe(fix(10, 0, 0));
        assertEquals("broken_approach", recovery.recover(Arrays.asList(fix(80, 200, 12),
                fix(90, 320, 12)), "unknown", START + 90_000).status);

        recovery.observe(fix(20, 20, 1.8f));
        recovery.observe(fix(30, 40, 1.8f));
        recovery.observe(fix(40, 60, 1.8f));
        assertTrue(recovery.recover(Arrays.asList(fix(50, 140, 10), fix(60, 260, 12)),
                "unknown", START + 60_000).prefix.isEmpty());
    }

    @Test public void stationaryDriftThenReturnDoesNotReachBackBeforeTheLaterDeparture() {
        JourneyOriginRecovery recovery = new JourneyOriginRecovery();
        recovery.observe(fix(0, 0, 0));
        recovery.observe(fix(10, 1, 0));
        recovery.observe(fix(20, 24, 1));
        recovery.observe(fix(30, 3, 0));
        recovery.observe(fix(40, 1, 0));
        List<Location> prefix = recovery.recover(Arrays.asList(fix(50, 28, 2),
                fix(60, 45, 2)), "walking", START + 60_000).prefix;
        assertEquals(1, prefix.size());
        assertFix(fix(40, 1, 0), prefix.get(0));
        recovery.clear();
        assertTrue(recovery.recover(Arrays.asList(fix(50, 28, 2), fix(60, 45, 2)),
                "walking", START + 60_000).prefix.isEmpty());
    }

    private void assertFix(Location expected, Location actual) {
        assertEquals(expected.getTime(), actual.getTime());
        assertEquals(expected.getLongitude(), actual.getLongitude(), 0);
        assertEquals(expected.getLatitude(), actual.getLatitude(), 0);
        assertEquals(expected.getAccuracy(), actual.getAccuracy(), 0);
        assertEquals(expected.getSpeed(), actual.getSpeed(), 0);
    }

    private Location fix(int seconds, double metres, float speed) {
        Location fix = new Location("gps");
        fix.setLongitude(metres / 111_195);
        fix.setLatitude(0);
        fix.setAccuracy(5);
        fix.setTime(START + 1000L * seconds);
        fix.setSpeed(speed);
        return fix;
    }
}
