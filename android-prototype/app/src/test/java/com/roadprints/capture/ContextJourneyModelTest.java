package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContextJourneyModelTest {
    private ContextMap.Evidence context(String kind) {
        ContextMap.Evidence e=new ContextMap.Evidence();e.available=true;
        e.road="road".equals(kind);e.rail="rail".equals(kind);e.outdoor="park".equals(kind);
        e.destination="building".equals(kind)||"parking".equals(kind);e.kind=kind;return e;
    }
    private ContextJourneyModel.Decision fix(ContextJourneyModel m,String mode,int i,long t,double metres,float speed,String kind,boolean station) {
        return m.sample(mode,i,t,51.5+metres/111195,-.1,5,speed,context(kind),station);
    }
    @Test public void trafficLightsAndParkPausesStayOpen() {
        ContextJourneyModel m=new ContextJourneyModel();
        for(int i=0;i<20;i++)assertNull(fix(m,"driving",i,1000+i*10000,0,0,"road",false));
        assertTrue(m.holdPause("driving",300000,context("road")));
        assertTrue(m.holdPause("walking",300000,context("park")));
        assertTrue(m.holdPause("train",1800000,context("rail")));
    }
    @Test public void buildingArrivalUsesStartOfDwell() {
        ContextJourneyModel m=new ContextJourneyModel();fix(m,"driving",0,1000,-200,10,"road",false);
        ContextJourneyModel.Decision d=null;
        for(int i=1;i<=19;i++)d=fix(m,"driving",i,1000+i*10000,0,0,"building",false);
        assertNotNull(d);assertNull(d.nextMode);assertEquals(1,d.boundary);assertEquals("arrival_building",d.reason);
    }
    @Test public void aNearbyStationDoesNotEndAThroughTrain() {
        ContextJourneyModel m=new ContextJourneyModel();
        for(int i=0;i<25;i++)assertNull(fix(m,"train",i,1000+i*10000,0,0,"building",true));
    }
    @Test public void stationWalkingCaptureFallsBackToPreservedVehicleLegWithoutRailMap() {
        ContextJourneyModel m=new ContextJourneyModel();ContextJourneyModel.Decision d=null;
        fix(m,"walking",0,1000,0,0,"rail",true);
        for(int i=1;i<=11&&d==null;i++)d=fix(m,"walking",i,1000+i*10000,i*100,10,"unknown",true);
        assertNotNull(d);assertEquals("unknown",d.nextMode);assertEquals(1,d.boundary);
    }
    @Test public void trainAndRoadSuggestionsNeedMappedAlignment() {
        for(String map:new String[]{"rail","road","unknown"}) {
            ContextJourneyModel m=new ContextJourneyModel();ContextJourneyModel.Decision d=null;
            for(int i=0;i<5;i++)d=fix(m,"unknown",i,1000+i*10000,i*100,10,map,false);
            if("unknown".equals(map))assertNull(d);
            else {assertNotNull(d);assertEquals("rail".equals(map)?"train":"driving",d.nextMode);}
        }
    }
    @Test public void vehicleToWalkingIncludesInitialWalkingPoints() {
        ContextJourneyModel m=new ContextJourneyModel();fix(m,"unknown",0,1000,-200,10,"unknown",false);
        ContextJourneyModel.Decision d=null;
        for(int i=1;i<=6&&d==null;i++)d=fix(m,"unknown",i,1000+i*10000,i*15,1.5f,"unknown",false);
        assertNotNull(d);assertEquals("walking",d.nextMode);assertEquals(1,d.boundary);
    }
    @Test public void walkRunWalkBecomesDistinctLegs() {
        ContextJourneyModel m=new ContextJourneyModel();ContextJourneyModel.Decision d=null;
        for(int i=0;i<7;i++)d=fix(m,"walking",i,1000+i*10000,i*30,3,"park",false);
        assertNotNull(d);assertEquals("running",d.nextMode);
        m=new ContextJourneyModel();
        d=null;
        for(int i=0;i<6&&d==null;i++)d=fix(m,"running",i,1000+i*10000,i*15,1.5f,"park",false);
        assertNotNull(d);assertEquals("walking",d.nextMode);
    }
    @Test public void poorAndOutOfOrderGpsCannotEstablishArrivalOrChangeMode() {
        ContextJourneyModel m=new ContextJourneyModel();
        for(int i=0;i<25;i++)assertNull(m.sample("walking",i,1000+i*10000,51.5,-.1,150,0,context("building"),false));
        assertNull(fix(m,"walking",1,100000,0,0,"building",false));
        assertNull(fix(m,"walking",2,90000,0,0,"building",false));
        assertNull(fix(m,"walking",3,110000,0,0,"building",false));
    }
    @Test public void cyclingIsNotTurnedIntoDrivingBySpeed() {
        ContextJourneyModel m=new ContextJourneyModel();
        for(int i=0;i<15;i++)assertNull(fix(m,"cycling",i,1000+i*10000,i*100,10,"road",false));
    }
    @Test public void pendingRunningTransitionSurvivesCheckpoint() {
        ContextJourneyModel m=new ContextJourneyModel();
        for(int i=0;i<4;i++)assertNull(fix(m,"walking",i,1000+i*10000,i*30,3,"park",false));
        ContextJourneyModel recovered=new ContextJourneyModel();recovered.restore(m.checkpoint(),4);
        ContextJourneyModel.Decision d=null;
        for(int i=4;i<7;i++)d=fix(recovered,"walking",i,1000+i*10000,i*30,3,"park",false);
        assertNotNull(d);assertEquals("running",d.nextMode);assertEquals(1,d.boundary);
    }
    @Test public void parallelRoadAndRailRemainUncertain() {
        ContextJourneyModel m=new ContextJourneyModel();ContextMap.Evidence e=context("rail");e.road=true;
        for(int i=0;i<10;i++)assertNull(m.sample("unknown",i,1000+i*10000,51.5+i*100/111195.0,-.1,5,10,e,false));
    }
}
