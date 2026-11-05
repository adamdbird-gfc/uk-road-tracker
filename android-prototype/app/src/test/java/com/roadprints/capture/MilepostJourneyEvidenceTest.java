package com.roadprints.capture;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.time.Instant;

public class MilepostJourneyEvidenceTest {
    static JSONObject journey(String mode)throws Exception{
        long start=Instant.parse("2026-11-05T00:00:00Z").toEpochMilli();
        JSONArray samples=new JSONArray(),coords=new JSONArray();
        for(int i=0;i<5;i++){
            MilepostProximity.Point p=MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,90,(i-2)*40);
            coords.put(new JSONArray().put(p.lon).put(p.lat));
            samples.put(new JSONArray().put(p.lon).put(p.lat).put(3).put(start+i*20000).put("cycling".equals(mode)?3:2));
        }
        JSONObject result=new JSONObject().put("status","ok").put("input_points",5).put("matched_tracepoints",5)
                .put("matching_mode",mode).put("matched_distance_is_deduplicated",true).put("matched_distance_m",160)
                .put("geojson",new JSONObject().put("type","FeatureCollection").put("features",new JSONArray().put(
                        new JSONObject().put("type","Feature").put("geometry",new JSONObject().put("type","LineString").put("coordinates",coords)))));
        return new JSONObject().put("journey_id","native-test").put("started_at","2026-11-05T00:00:00Z")
                .put("ended_at","2026-11-05T00:02:00Z").put("mode",mode).put("processing_status","complete")
                .put("source",new JSONObject().put("type","android_activity_capture"))
                .put("capture_decisions",new JSONObject().put("mode_at_save",mode).put("mode_source","capture_activity_or_gps_context"))
                .put("activity_recognition_history",new JSONArray().put(new JSONObject().put("activity",mode).put("entering",true).put("at_utc","2026-11-05T00:00:00Z")))
                .put("capture_route_samples",samples).put("route_geometry",new JSONObject().put("type","LineString").put("coordinates",coords))
                .put("processing_result",result);
    }
    static MilepostProximity.Result assess(JSONObject j)throws Exception{return MilepostProximityTest.assess(MilepostProximityTest.marker(50),MilepostJourneyEvidence.nativeArchive(j));}
    @Test public void nativeWalkingRunningCyclingPassWithoutStop()throws Exception{
        for(String mode:new String[]{"walking","running","cycling"})assertEquals(mode,MilepostProximity.Outcome.QUALIFIED,assess(journey(mode)).outcome);
    }
    @Test public void failedPendingRejectedAndPartialMatchesNeverQualify()throws Exception{
        for(String status:new String[]{"failed","pending","processing","rejected"})assertEquals(MilepostProximity.Outcome.UNVALIDATED_MATCH,assess(journey("walking").put("processing_status",status)).outcome);
        JSONObject j=journey("walking");j.getJSONObject("processing_result").put("capture_partial_match",true);
        assertEquals(MilepostProximity.Outcome.UNVALIDATED_MATCH,assess(j).outcome);
        j=journey("walking");j.getJSONObject("processing_result").put("matched_tracepoints",4);
        assertEquals(MilepostProximity.Outcome.UNVALIDATED_MATCH,assess(j).outcome);
    }
    @Test public void gapCannotCreateFalseCrossing()throws Exception{
        JSONObject j=journey("walking");JSONArray samples=j.getJSONArray("capture_route_samples");
        for(int i=2;i<5;i++)samples.getJSONArray(i).put(3,samples.getJSONArray(i).getLong(3)+180000);
        j.put("ended_at","2026-11-05T00:10:00Z");
        assertEquals(MilepostProximity.Outcome.UNSUPPORTED_GAP,assess(j).outcome);
    }
    @Test public void noisyFixAndImpossibleJumpFailClosed()throws Exception{
        JSONObject j=journey("walking");j.getJSONArray("capture_route_samples").getJSONArray(2).put(2,51);
        assertEquals(MilepostProximity.Outcome.UNRELIABLE_LOCATION,assess(j).outcome);
        j=journey("walking");j.getJSONArray("capture_route_samples").getJSONArray(2).put(0,1);
        j.getJSONObject("route_geometry").getJSONArray("coordinates").getJSONArray(2).put(0,1);
        assertEquals(MilepostProximity.Outcome.UNRELIABLE_LOCATION,assess(j).outcome);
    }
    @Test public void stationarySinglePointDriftHasNoTravelProof()throws Exception{
        JSONObject j=journey("walking");JSONArray s=j.getJSONArray("capture_route_samples"),c=j.getJSONObject("route_geometry").getJSONArray("coordinates");
        for(int i=0;i<5;i++){
            MilepostProximity.Point p=MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,90,i==2?0:180);
            s.getJSONArray(i).put(0,p.lon).put(1,p.lat).put(4,0);c.getJSONArray(i).put(0,p.lon).put(1,p.lat);
        }
        assertNotEquals(MilepostProximity.Outcome.QUALIFIED,assess(j).outcome);
    }
    @Test public void manualVehicleRelabelAndUnknownSourcesRejected()throws Exception{
        JSONObject j=journey("cycling");j.getJSONObject("capture_decisions").put("mode_at_save","driving");
        assertEquals(MilepostProximity.Outcome.UNTRUSTED_SOURCE,assess(j).outcome);
        j=journey("cycling").put("transport_changes",new JSONArray().put(new JSONObject().put("from","driving").put("to","cycling")));
        assertEquals(MilepostProximity.Outcome.UNTRUSTED_SOURCE,assess(j).outcome);
        j=journey("walking");j.getJSONObject("source").put("type","user_drawn");
        assertEquals(MilepostProximity.Outcome.UNTRUSTED_SOURCE,assess(j).outcome);
    }
    @Test public void cyclingRequiresCyclingMatchAndLocalCyclingMotion()throws Exception{
        JSONObject j=journey("cycling");j.getJSONObject("processing_result").put("matching_mode","driving");
        assertEquals(MilepostProximity.Outcome.UNVALIDATED_MATCH,assess(j).outcome);
        j=journey("cycling");for(int i=0;i<5;i++)j.getJSONArray("capture_route_samples").getJSONArray(i).put(4,25);
        assertNotEquals(MilepostProximity.Outcome.QUALIFIED,assess(j).outcome);
    }
    @Test public void cyclingWithoutActivityEvidenceAndObservedVehicleSpeedCannotQualify()throws Exception{
        JSONObject j=journey("cycling");j.remove("activity_recognition_history");
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(j).outcome);
        j=journey("cycling");JSONArray samples=j.getJSONArray("capture_route_samples"),coords=j.getJSONObject("route_geometry").getJSONArray("coordinates");
        for(int i=0;i<5;i++){
            MilepostProximity.Point p=MilepostProximityTest.offset(MilepostProximityTest.ORIGIN,90,(i-2)*600);
            samples.getJSONArray(i).put(0,p.lon).put(1,p.lat);coords.getJSONArray(i).put(0,p.lon).put(1,p.lat);
        }
        assertNotEquals(MilepostProximity.Outcome.QUALIFIED,assess(j).outcome);
    }
    @Test public void historicalSchemaCannotManufactureValidationFromCompleteFlag()throws Exception{
        JSONObject j=journey("walking");j.getJSONObject("source").put("type","timeline_import").put("source_file_fingerprint","test");
        j.remove("capture_route_samples");j.remove("capture_decisions");
        j.put("capture_quality",new JSONObject().put("source_route_points",5).put("status","usable"));
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(j).outcome);
        j.put("timeline_match_evidence",new JSONObject().put("version",1).put("point_times_ms",new JSONArray().put(1).put(2)));
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(j).outcome);
    }
    @Test public void supportedIndependentHistoricalSectionCanQualifyWithoutLiveFixTime(){
        assertEquals(MilepostProximity.Outcome.QUALIFIED,MilepostProximityTest.assess(MilepostProximityTest.marker(50),MilepostProximityTest.pass("walking")).outcome);
    }
    @Test public void correctionsAndMisalignedObservationsRemainUnresolved()throws Exception{
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(journey("walking").put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0)))).outcome);
        JSONObject j=journey("walking");j.getJSONArray("capture_route_samples").getJSONArray(2).put(0,0);
        assertEquals(MilepostProximity.Outcome.UNRELIABLE_LOCATION,assess(j).outcome);
    }
    @Test public void tokensStableAcrossJsonKeyOrderAndInputMutationCannotChangeProof()throws Exception{
        JSONObject j=journey("walking");MilepostProximity.Evidence a=MilepostJourneyEvidence.nativeArchive(j);
        JSONObject reordered=new JSONObject();java.util.List<String> keys=new java.util.ArrayList<>(j.keySet());java.util.Collections.reverse(keys);
        for(String key:keys)reordered.put(key,j.get(key));
        assertEquals(a.token(),MilepostJourneyEvidence.nativeArchive(reordered).token());
        j.getJSONArray("capture_route_samples").getJSONArray(2).put(0,0);
        assertEquals(MilepostProximity.Outcome.QUALIFIED,MilepostProximityTest.assess(MilepostProximityTest.marker(50),a).outcome);
        assertNotEquals(a.token(),MilepostJourneyEvidence.nativeArchive(j).token());
    }
    @Test public void sparseButLocallySupportedSamplesQualify()throws Exception{
        JSONObject j=journey("walking");JSONArray samples=j.getJSONArray("capture_route_samples");
        long first=samples.getJSONArray(0).getLong(3);
        for(int i=0;i<5;i++)samples.getJSONArray(i).put(3,first+i*40000).put(4,1);
        j.put("ended_at","2026-11-05T00:03:00Z");
        assertEquals(MilepostProximity.Outcome.QUALIFIED,assess(j).outcome);
    }
    @Test public void savedQualityFailureAndVehicleFootTailCannotQualify()throws Exception{
        assertEquals(MilepostProximity.Outcome.UNRELIABLE_LOCATION,assess(journey("walking").put("recording_quality",new JSONObject().put("resolved",false))).outcome);
        JSONObject j=journey("walking");for(int i=0;i<5;i++)j.getJSONArray("capture_route_samples").getJSONArray(i).put(4,7);
        assertNotEquals(MilepostProximity.Outcome.QUALIFIED,assess(j).outcome);
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(journey("walking").put("walking_validation",new JSONObject().put("station_tail_points",3))).outcome);
    }
    @Test public void oversizedArchiveGeometryIsHeldBeforeCopying()throws Exception{
        JSONObject j=journey("walking");JSONArray samples=j.getJSONArray("capture_route_samples"),coords=j.getJSONObject("route_geometry").getJSONArray("coordinates");
        while(samples.length()<=MilepostJourneyEvidence.MAX_POINTS){samples.put(samples.get(0));coords.put(coords.get(0));}
        assertEquals(MilepostProximity.Outcome.INSUFFICIENT_GEOMETRY,assess(j).outcome);
    }
    @Test public void observationsOutsideJourneyIntervalRejected()throws Exception{
        JSONObject j=journey("walking");j.put("ended_at","2026-11-05T00:00:20Z");
        assertEquals(MilepostProximity.Outcome.UNRELIABLE_LOCATION,assess(j).outcome);
    }
}
