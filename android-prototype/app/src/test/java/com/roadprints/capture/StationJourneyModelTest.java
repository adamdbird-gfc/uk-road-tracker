package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;

public class StationJourneyModelTest {
    private static final double LAT=51.5,LON=-.1;
    private StationJourneyModel model() {
        RailStationCatalog catalog=new RailStationCatalog();
        catalog.stations.add(new RailStationCatalog.Station("TEST","Test station",LAT,LON));
        return new StationJourneyModel(catalog);
    }
    private StationJourneyModel.Decision fix(StationJourneyModel m,String mode,int i,long t,double north,float speed) {
        return m.sample(mode,i,t,LAT+north/111195,LON,5,speed);
    }
    @Test public void intermediateStationAndThirtyMinuteWaitRemainOneTrainLeg() {
        StationJourneyModel m=model(); m.activity("vehicle",1000,0);
        assertNull(fix(m,"train",0,1000,0,0)); m.activity("still",2000,0);
        assertNull(fix(m,"train",1,1802000,3,0));
        assertTrue(m.holdStill("train",1802000));
        m.activity("vehicle",1803000,1);
        assertNull(fix(m,"train",2,1810000,100,12));
        assertNull(fix(m,"train",3,1850000,850,20));
        assertEquals("",m.stationCode());
    }
    @Test public void sustainedPlatformWalkingEndsTrainInsideStation() {
        StationJourneyModel m=model(); m.activity("vehicle",1000,0);
        fix(m,"train",0,1000,0,0); fix(m,"train",1,11000,0,0);
        m.activity("walking",12000,1);
        StationJourneyModel.Decision decision=null;
        for(int i=2;i<=6;i++) decision=fix(m,"train",i,12000+(i-1)*10000,15*(i-1),1.5f);
        assertNotNull(decision); assertEquals("walking",decision.nextMode);
        assertEquals(1,decision.endIndex); assertEquals(1,decision.startIndex);
        assertEquals("TEST",decision.stationCode);
    }
    @Test public void boardingWaitThenDepartureSeparatesWalkingAndTrain() {
        StationJourneyModel m=model(); m.activity("walking",1000,0);
        fix(m,"walking",0,1000,-500,1); fix(m,"walking",1,11000,-100,1);
        m.activity("still",20000,1);
        assertTrue(m.holdStill("walking",900000));
        assertNull(fix(m,"walking",2,1800000,-80,0)); // thirty-minute interchange
        m.activity("vehicle",1801000,2);
        assertNull(fix(m,"walking",3,1810000,300,12));
        StationJourneyModel.Decision d=fix(m,"walking",4,1850000,850,20);
        assertNotNull(d); assertEquals("train",d.nextMode);
        assertEquals(1,d.endIndex); assertEquals(3,d.startIndex);
        assertTrue(d.startIndex>d.endIndex); // waiting GPS is retained outside the matched walk
    }
    @Test public void unknownDepartureGetsTrainSuggestionWithoutAnotherJourney() {
        StationJourneyModel m=model(); fix(m,"unknown",0,1000,0,0); m.activity("vehicle",2000,0);
        fix(m,"unknown",1,71000,300,10);
        StationJourneyModel.Decision d=fix(m,"unknown",2,111000,850,20);
        assertNotNull(d); assertEquals(-1,d.endIndex); assertEquals("train",d.nextMode);
    }
    @Test public void walkingPastStationDoesNotSplitOrLeaveBoardingEvidence() {
        StationJourneyModel m=model(); m.activity("walking",1000,0);
        assertNull(fix(m,"walking",0,1000,-100,1));
        assertFalse(m.holdStill("walking",10000));
        assertNull(fix(m,"walking",1,61000,500,1));
        assertEquals("",m.stationCode()); assertFalse(m.ownsModeChange("walking"));
    }
    @Test public void stationProximityAndFastGpsAloneDoNotClassifyTrain() {
        StationJourneyModel m=model(); m.activity("walking",1000,0);
        fix(m,"walking",0,1000,0,0);
        fix(m,"walking",1,71000,300,10);
        assertNull(fix(m,"walking",2,111000,850,20));
    }
    @Test public void gpsDriftAndBriefWalkingSignalDoNotSplitTrain() {
        StationJourneyModel m=model(); fix(m,"train",0,1000,0,0); m.activity("walking",2000,0);
        assertNull(fix(m,"train",1,12000,4,1));
        assertNull(fix(m,"train",2,22000,-4,1));
        assertNull(fix(m,"train",3,32000,3,1));
        m.activity("vehicle",33000,3);
        assertNull(fix(m,"train",4,44000,5,0)); assertTrue(m.holdStill("train",44000));
    }
    @Test public void unreliableAndOutOfOrderFixesDoNotConfirmBoundaries() {
        StationJourneyModel m=model(); fix(m,"train",0,1000,0,0); m.activity("walking",2000,0);
        assertNull(m.sample("train",1,12000,LAT+.01,LON,150,1));
        assertNull(fix(m,"train",2,500,50,1));
        assertNull(fix(m,"train",3,32000,500,1));
        assertNull(fix(m,"train",4,42000,0,1));
    }
    @Test public void confirmedRoadCaptureCannotBeReclassified() {
        StationJourneyModel m=model(); fix(m,"driving",0,1000,0,0); m.activity("vehicle",2000,0);
        fix(m,"driving",1,71000,300,10);
        assertNull(fix(m,"driving",2,111000,850,20));
    }
    @Test public void delayedVehicleSignalRetainsOriginalStationAndDepartureBoundary() {
        StationJourneyModel m=model(); m.activity("walking",1000,0);
        fix(m,"walking",0,1000,-500,1); fix(m,"walking",1,11000,-100,1);
        m.activity("still",20000,1);
        assertNull(fix(m,"walking",2,180000,300,12));
        assertNull(fix(m,"walking",3,220000,850,20));
        assertNull(fix(m,"walking",4,260000,2000,0)); // intermediate stop before activity signal
        StationJourneyModel recovered=model(); recovered.restore(m.checkpoint(),5);
        recovered.activity("vehicle",720000,4);
        StationJourneyModel.Decision d=fix(recovered,"walking",5,750000,2500,15);
        assertNotNull(d); assertEquals(1,d.endIndex); assertEquals(2,d.startIndex);
    }
    @Test public void checkpointRetainsPendingTransferAndStopEvidence() {
        StationJourneyModel first=model(); fix(first,"train",0,1000,0,0);
        fix(first,"train",1,11000,0,0); first.activity("walking",12000,1);
        fix(first,"train",2,22000,15,1.5f); fix(first,"train",3,32000,30,1.5f);
        StationJourneyModel restored=model(); restored.restore(first.checkpoint(),4);
        assertEquals("walking",restored.currentActivity());
        fix(restored,"train",4,42000,45,1.5f);
        StationJourneyModel.Decision d=fix(restored,"train",5,52000,60,1.5f);
        assertNotNull(d); assertEquals(1,d.endIndex);
    }
    @Test public void repeatedActivitySubscriptionsDoNotResetWalkingEvidence() {
        StationJourneyModel m=model(); fix(m,"train",0,1000,0,0); fix(m,"train",1,11000,0,0);
        m.activity("walking",12000,1); fix(m,"train",2,22000,15,1.5f);
        m.activity("walking",23000,2); fix(m,"train",3,32000,30,1.5f);
        m.activity("walking",33000,3); fix(m,"train",4,42000,45,1.5f);
        assertNotNull(fix(m,"train",5,52000,60,1.5f));
    }
}
