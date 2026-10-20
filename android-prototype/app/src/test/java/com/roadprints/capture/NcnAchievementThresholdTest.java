package com.roadprints.capture;
import org.junit.Test;
import static org.junit.Assert.*;
public class NcnAchievementThresholdTest {
    @Test public void exactThresholdsAndDisplayRoundingNeverAwardMissingCoverage(){
        for(double threshold:new double[]{25,50,75,100}){
            assertEquals(threshold,NcnAchievements.percent(threshold,100),0);
            assertTrue(NcnAchievements.percent(threshold-.001,100)<threshold);
        }
        assertEquals(0,NcnAchievements.percent(Double.NaN,100),0);
        assertEquals(0,NcnAchievements.percent(1,0),0);
        assertEquals(100,NcnAchievements.percent(100-1e-11,100),0);
        assertTrue(NcnAchievements.percent(99.999,100)<100);
    }
}
