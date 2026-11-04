package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;

/** Production validators and context model, independent of Android callbacks. */
public class DrivingTransitionEvidenceTest {
    private FootTraceValidator.Sample p(int i,double metres,double speed,double accuracy) {
        return new FootTraceValidator.Sample(-.1,51.5+metres/111195,accuracy,1000+i*10000L,speed,i);
    }
    private ContextMap.Evidence road(){ContextMap.Evidence c=new ContextMap.Evidence();c.available=true;c.road=true;return c;}
    @Test public void footPlausibilityVetoesRealVehicleFixButTransportValidationAcceptsIt() {
        FootTraceValidator.Sample a=p(0,0,15,5),b=p(1,150,15,5);
        assertFalse(CaptureQualityValidator.reliable(a,b,true));assertTrue(CaptureQualityValidator.reliable(a,b,false));
    }
    @Test public void sameGpsIsBlockedByOldGateAndResolvesWithTransportGate() {
        for(boolean footGate:new boolean[]{true,false}) {
            ContextJourneyModel model=new ContextJourneyModel();String mode="walking";FootTraceValidator.Sample last=null;
            for(int i=0;i<10;i++) {
                FootTraceValidator.Sample fix=p(i,i*150,15,5);
                if(CaptureQualityValidator.reliable(last,fix,footGate)) {
                    ContextJourneyModel.Decision d=model.sample(mode,i,fix.time,fix.lat,fix.lon,(float)fix.accuracy,(float)fix.speed,road(),false);
                    if(d!=null)mode=d.nextMode;
                    last=fix;
                }
            }
            assertEquals(footGate?"walking":"driving",mode);
        }
    }
    @Test public void transportValidationStillRejectsTeleportsDuplicateTimesAndBadAccuracy() {
        FootTraceValidator.Sample a=p(0,0,15,5);
        assertFalse(CaptureQualityValidator.reliable(a,p(1,1000,15,5),false));
        assertFalse(CaptureQualityValidator.reliable(a,p(0,100,15,5),false));
        assertFalse(CaptureQualityValidator.reliable(a,p(1,150,15,150),false));
    }
    @Test public void noRoadContextLeavesVehicleMovementUnknown() {
        ContextJourneyModel model=new ContextJourneyModel();
        for(int i=0;i<10;i++){FootTraceValidator.Sample p=p(i,i*150,15,5);assertNull(model.sample("unknown",i,p.time,p.lat,p.lon,5,15,new ContextMap.Evidence(),false));}
    }
    @Test public void roadAndRailAmbiguityDoesNotAssignDrivingOrChangeTrain() {
        for(String mode:new String[]{"unknown","train"}) {
            ContextJourneyModel model=new ContextJourneyModel();ContextMap.Evidence c=road();c.rail=true;
            for(int i=0;i<10;i++){FootTraceValidator.Sample p=p(i,i*150,15,5);assertNull(model.sample(mode,i,p.time,p.lat,p.lon,5,15,c,false));}
        }
    }
    @Test public void railOnlyContextStillRecognisesTrain() {
        ContextJourneyModel model=new ContextJourneyModel();ContextMap.Evidence c=new ContextMap.Evidence();c.available=true;c.rail=true;ContextJourneyModel.Decision d=null;
        for(int i=0;i<5;i++){FootTraceValidator.Sample p=p(i,i*150,15,5);d=model.sample("unknown",i,p.time,p.lat,p.lon,5,15,c,false);}
        assertNotNull(d);assertEquals("train",d.nextMode);
    }
    @Test public void speedBelowExistingVehicleThresholdDoesNotInventDriving() {
        ContextJourneyModel model=new ContextJourneyModel();
        for(int i=0;i<10;i++){FootTraceValidator.Sample p=p(i,i*40,4,5);assertNull(model.sample("unknown",i,p.time,p.lat,p.lon,5,4,road(),false));}
    }
}
