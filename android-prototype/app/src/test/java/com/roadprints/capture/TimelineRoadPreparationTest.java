package com.roadprints.capture;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class TimelineRoadPreparationTest {
    private JSONArray points(String coordinates) throws Exception {
        JSONArray out = new JSONArray(), source = new JSONArray(coordinates);
        for (int i=0;i<source.length();i++) out.put(new JSONObject()
                .put("lng",source.getJSONArray(i).getDouble(0)).put("lat",source.getJSONArray(i).getDouble(1)));
        return out;
    }
    private JSONObject journey(String times) throws Exception {
        return new JSONObject().put("source",new JSONObject().put("type","timeline_import"))
                .put("timeline_match_evidence",new JSONObject().put("point_times_ms",new JSONArray(times))
                    .put("end_appended",true).put("parking_coordinates",new JSONArray("[0.12,0]"))
                    .put("parking_time_ms",180000));
    }
    @Test public void contradictoryEndpointIsOmittedOnlyWithNearbyTimedParkingEvidence() throws Exception {
        JSONArray points=points("[[0,0],[0.11,0],[0.12,0],[0,0]]");
        String original=points.toString();
        TimelineRoadPreparation.Prepared p=TimelineRoadPreparation.prepare(journey("[0,60000,120000,180000]"),points);
        assertTrue(p.changed);assertEquals(3,p.sections.get(0).length());assertEquals(original,points.toString());
        assertEquals("omitted_contradictory_activity_end",p.details.getString("endpoint_action"));
    }
    @Test public void inconsistentEndpointWithoutCorroborationIsPreserved() throws Exception {
        JSONObject journey=journey("[0,60000,120000,180000]");
        journey.getJSONObject("timeline_match_evidence").put("parking_coordinates",new JSONArray("[0,0]"));
        assertFalse(TimelineRoadPreparation.prepare(journey,points("[[0,0],[0.11,0],[0.12,0],[0,0]]")).changed);
    }
    @Test public void plausibleReturnAndStaleParkingAreNotRemoved() throws Exception {
        JSONObject j=journey("[0,60000,120000,600000]");
        assertFalse(TimelineRoadPreparation.prepare(j,points("[[0,0],[0.11,0],[0.12,0],[0,0]]")).changed);
        j=journey("[0,60000,120000,180000]");j.getJSONObject("timeline_match_evidence").put("parking_time_ms",900000);
        assertFalse(TimelineRoadPreparation.prepare(j,points("[[0,0],[0.11,0],[0.12,0],[0,0]]")).changed);
    }
    @Test public void unrecordedGapSplitsRequestsWithoutAConnector() throws Exception {
        JSONObject j=journey("[0,60000,9300000,9360000]");
        j.getJSONObject("timeline_match_evidence").put("end_appended",false);
        TimelineRoadPreparation.Prepared p=TimelineRoadPreparation.prepare(j,points("[[0,0],[0.001,0],[0.1,0],[0.101,0]]"));
        assertEquals(2,p.sections.size());assertEquals(2,p.sections.get(0).length());assertEquals(2,p.sections.get(1).length());
        assertEquals(9240000,p.details.getJSONArray("unrecorded_gaps").getJSONObject(0).getLong("duration_ms"));
    }
    @Test public void isolatedPointAcrossGapIsReportedWithoutBlockingSupportedSection() throws Exception {
        TimelineRoadPreparation.Prepared p=TimelineRoadPreparation.prepare(journey("[0,9300000,9360000]"),points("[[0,0],[0.1,0],[0.101,0]]"));
        assertEquals(1,p.details.getInt("isolated_samples_omitted"));assertTrue(p.changed);assertEquals(1,p.sections.size());
    }
    @Test public void duplicateMinuteTimesAreKeptInSourceOrder() throws Exception {
        JSONObject j=journey("[0,60000,60000,180000]");
        j.getJSONObject("timeline_match_evidence").put("end_appended",false);
        JSONArray points=points("[[0,0],[0.01,0],[0.02,0],[0.03,0]]");
        assertEquals(points.toString(),TimelineRoadPreparation.prepare(j,points).sections.get(0).toString());
    }
    @Test public void malformedTimingFallsBackToOriginalTrace() throws Exception {
        JSONArray p=points("[[0,0],[0.01,0],[0.02,0]]");
        assertEquals(p.toString(),TimelineRoadPreparation.prepare(journey("[0,60000]"),p).sections.get(0).toString());
        assertEquals(p.toString(),TimelineRoadPreparation.prepare(journey("[60000,0,120000]"),p).sections.get(0).toString());
    }
    @Test public void mergedGeometryKeepsSectionsSeparateAndSumsOnlyMatchedDistances() throws Exception {
        JSONObject a=new JSONObject("{\"input_points\":2,\"matched_tracepoints\":2,\"matched_distance_m\":100,\"geojson\":{\"features\":[{\"geometry\":{\"coordinates\":[[0,0],[0.01,0]]}}]}}");
        JSONObject b=new JSONObject("{\"input_points\":2,\"matched_tracepoints\":2,\"matched_distance_m\":200,\"geojson\":{\"features\":[{\"geometry\":{\"coordinates\":[[0.1,0],[0.11,0]]}}]}}");
        JSONObject merged=TimelineRoadPreparation.merge(Arrays.asList(a,b));
        assertEquals(300,merged.getDouble("matched_distance_m"),.01);assertEquals(4,merged.getInt("input_points"));
        assertEquals(2,merged.getJSONObject("geojson").getJSONArray("features").length());
    }
    @Test public void failureDiagnosticsRetainIndicesButNotMapGeometry() throws Exception {
        JSONObject r=new JSONObject("{\"unmatched_point_indices\":[3],\"failed_sections\":[{\"start_point_index\":2,\"end_point_index\":3,\"detail\":\"NoMatch\"}],\"geojson\":{\"features\":[]}}");
        JSONObject d=TimelineRoadPreparation.diagnostics(r);
        assertEquals(3,d.getJSONArray("unmatched_point_indices").getInt(0));
        assertEquals(2,d.getJSONArray("failed_sections").getJSONObject(0).getInt("start_point_index"));assertFalse(d.has("geojson"));
    }
    private JSONObject partial(String indices,String omitted,String failures) throws Exception {
        return new JSONObject().put("input_points",5).put("matched_tracepoints",4)
                .put("matched_distance_is_deduplicated",true).put("matched_point_indices",new JSONArray(indices))
                .put("unmatched_point_indices",new JSONArray(omitted)).put("failed_sections",new JSONArray(failures));
    }
    @Test public void verifiedUnmatchedTailIsAcceptedAsExplicitPartialEvidence() throws Exception {
        assertTrue(TimelineRoadPreparation.endpointPartial(partial("[0,1,2,3]","[4]",
            "[{\"start_point_index\":3,\"end_point_index\":4}]"),points("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]")));
    }
    @Test public void interiorFailureCannotBeReclassifiedAsAnEndpoint() throws Exception {
        assertFalse(TimelineRoadPreparation.endpointPartial(partial("[0,1,3,4]","[2]",
            "[{\"start_point_index\":1,\"end_point_index\":2}]"),points("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]")));
    }
    @Test public void failureInsideMatchedCoreIsRejectedEvenIfOmittedIndicesClaimATail() throws Exception {
        assertFalse(TimelineRoadPreparation.endpointPartial(partial("[0,1,2,3]","[4]",
            "[{\"start_point_index\":1,\"end_point_index\":2}]"),points("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]")));
    }
    @Test public void nearDuplicateStartFixIsAllowedButDistantInteriorSampleIsNot() throws Exception {
        JSONObject r=partial("[0,2,3,4]","[1]","[]");
        assertTrue(TimelineRoadPreparation.endpointPartial(r,points("[[0,0],[0.00005,0],[0.002,0],[0.003,0],[0.004,0]]")));
        assertFalse(TimelineRoadPreparation.endpointPartial(r,points("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]")));
    }
    @Test public void sparseAndUnverifiedPartialEvidenceIsRejected() throws Exception {
        JSONArray p=points("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]");
        JSONObject r=partial("[0,1,2,3]","[4]","[]").put("matched_distance_is_deduplicated",false);
        assertFalse(TimelineRoadPreparation.endpointPartial(r,p));
        r=partial("[0,1,2]","[3,4]","[]").put("matched_tracepoints",3);
        assertFalse(TimelineRoadPreparation.endpointPartial(r,p));
    }
    @Test public void duplicatedMatcherIndicesAreRejected() throws Exception {
        assertFalse(TimelineRoadPreparation.endpointPartial(partial("[0,1,2,2]","[4]","[]"),
                points("[[0,0],[0.001,0],[0.002,0],[0.003,0],[0.004,0]]")));
    }
}
