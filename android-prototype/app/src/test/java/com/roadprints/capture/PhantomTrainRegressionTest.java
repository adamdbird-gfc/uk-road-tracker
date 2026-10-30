package com.roadprints.capture;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

/** Entirely synthetic coordinates; no private recording is committed to the repository. */
public class PhantomTrainRegressionTest {
 private ContextMap.Evidence context(String kind){ContextMap.Evidence e=new ContextMap.Evidence();e.available=true;e.rail="rail".equals(kind);e.road="road".equals(kind);e.destination="home".equals(kind)||"club".equals(kind);e.kind=kind;return e;}
 private FootTraceValidator.Sample p(int i,long t,double metres,double speed){return new FootTraceValidator.Sample(metres/111195,0,5,t,speed,i);}
 @Test public void trainLabelAloneCannotHoldAnOffStationStopForever(){
  StationJourneyModel s=new StationJourneyModel(new RailStationCatalog());
  assertFalse(s.holdStill("train",1000));assertFalse(s.holdStill("train",49*3600000L));
  ContextJourneyModel c=new ContextJourneyModel();
  assertFalse(c.holdPause("train",300000,context("home")));
  assertFalse(c.holdPause("train",900000,context("club")));
  assertTrue(c.holdPause("train",49*3600000L,context("rail")));
 }
 @Test public void stationThenCarClubHomeAndIndependentFridayDeparture(){
  ContextJourneyModel c=new ContextJourneyModel();
  // Short station stop: the train remains open with actual station/rail evidence.
  for(int i=0;i<=6;i++)assertNull(c.sample("train",i,1000+i*20000,0,0,5,0,context("rail"),true));
  assertTrue(c.holdPause("train",120000,context("rail")));
  // Sustained road-only movement outside the station changes the leg using measured GPS.
  ContextJourneyModel.Decision d=null;
  for(int i=0;i<=5&&d==null;i++)d=c.sample("train",7+i,141000+i*10000,0,(100+i*100)/111195.,5,10,context("road"),false);
  assertNotNull(d);assertEquals("driving",d.nextMode);
  // Each fresh car leg can close at its own evidenced destination; mode is not sticky train.
  for(String place:new String[]{"club","home"}){
   c=new ContextJourneyModel();d=null;
   for(int i=0;i<=15;i++)d=c.sample("driving",i,1000+i*10000,0,0,5,0,context(place),false);
   assertNotNull(d);assertNull(d.nextMode);assertTrue(d.reason.startsWith("arrival_"));
  }
  // The next independent capture starts Unknown; road evidence resolves it to driving.
  c=new ContextJourneyModel();d=null;
  for(int i=0;i<=5&&d==null;i++)d=c.sample("unknown",i,172801000L+i*10000,0,i*100/111195.,5,10,context("road"),false);
  assertNotNull(d);assertEquals("driving",d.nextMode);
 }
 @Test public void fiveMinuteGpsStopIsUnchangedAndSilenceIsNotStillness(){
  WalkingStillnessDetector w=new WalkingStillnessDetector();
  for(int i=0;i<=4;i++)w.accept(p(i,1000+i*30000,0,0),true);
  assertFalse(w.canFinish(121000)); // pickup before five minutes
  assertFalse(w.canFinish(301000)); // no fresh samples: do not invent the missing dwell
  for(int i=5;i<=10;i++)w.accept(p(i,1000+i*30000,0,0),true);
  assertTrue(w.canFinish(301000));assertEquals(0,w.anchor().index);
  assertFalse(w.canFinish(172801000)); // multi-day GPS silence is not stationary evidence
 }
 @Test public void fifteenMinuteMeasuredDwellWorksWithoutActivityCallbacks(){
  WalkingStillnessDetector w=new WalkingStillnessDetector();
  for(int i=0;i<=30;i++)w.accept(p(i,1000+i*30000,i%2,0),true);
  assertTrue(w.canFinish(901000));assertEquals(0,w.anchor().index);
 }
 @Test public void recognisedHomeArrivalIsAvailableInTrainModeButRailIsProtected(){
  for(boolean rail:new boolean[]{false,true}){
   ContextJourneyModel c=new ContextJourneyModel();ContextMap.Evidence e=context("home");e.rail=rail;
   ContextJourneyModel.Decision d=null;for(int i=0;i<=9;i++)d=c.sample("train",i,1000+i*10000,0,0,5,0,e,rail);
   assertEquals(rail,d==null);
  }
 }
 @Test public void locationContextAndStationRestoreDoNotReinstateTheGlobalTrainHold(){
  ContextJourneyModel c=new ContextJourneyModel();for(int i=0;i<=4;i++)c.sample("train",i,1000+i*10000,0,0,5,0,context("home"),false);
  ContextJourneyModel restored=new ContextJourneyModel();restored.restore(c.checkpoint(),5);
  assertNotNull(restored.sample("train",5,71000,0,0,5,0,context("home"),false));
  StationJourneyModel s=new StationJourneyModel(new RailStationCatalog()),s2=new StationJourneyModel(new RailStationCatalog());s2.restore(s.checkpoint(),6);
  assertFalse(s2.holdStill("train",172801000));
 }
 @Test public void aLongTrainOnRailDoesNotBecomeACarOrEndFromElapsedDuration(){
  ContextJourneyModel c=new ContextJourneyModel();for(int i=0;i<12;i++)assertNull(c.sample("train",i,1000+i*3600000L,0,i*.1,5,10,context("rail"),false));
  assertTrue(c.holdPause("train",172800000,context("rail")));
 }
 @Test public void sizeGuardUsesFullIntegerCountsAndNeverMutatesOriginal()throws Exception{
  for(int count:new int[]{19999,20000,20001,32766,32767,32768,39120}){
   JSONArray rows=new JSONArray();for(int i=0;i<count;i++)rows.put(new JSONArray().put(0).put(0).put(5).put(1000L+i*1000L).put(0));
   JSONObject j=new JSONObject().put("mode","train").put("raw_capture_samples",rows);
   String before=j.toString();assertEquals(count,JourneySizeGuard.rawCount(j));
   assertEquals(count>20000,JourneySizeGuard.requiresReview(j));assertEquals(before,j.toString());
  }
 }
}
