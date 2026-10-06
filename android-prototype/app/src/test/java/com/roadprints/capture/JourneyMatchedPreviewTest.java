package com.roadprints.capture;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;
import java.lang.reflect.Field;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class JourneyMatchedPreviewTest {
    private JSONObject journey() throws Exception {
        return new JSONObject("{\"mode\":\"driving\",\"route_geometry\":{\"coordinates\":[[0,0],[10,10]]},\"processing_result\":{\"geojson\":{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[1,1],[2,2],[3,3]]}},{\"geometry\":{\"type\":\"MultiLineString\",\"coordinates\":[[[4,4],[5,5]]]}}]}}}");
    }
    @Test public void matchedPreviewExcludesSourceTraceAndKeepsSeparateSections() throws Exception {
        JSONObject j=journey();String original=j.toString();
        JourneyListActivity.JourneyPreview p=JourneyListActivity.journeyPreview(j);
        assertNull(p.coordinates);assertEquals(2,p.matches.size());assertEquals("Matched route",p.legend);
        assertEquals(1,p.matches.get(0).getJSONArray(0).getDouble(0),0);assertEquals(original,j.toString());
    }
    @Test public void savedRemovalsAreAppliedWithoutFallingBackToOriginalTrace() throws Exception {
        JSONObject j=journey().put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray("[0,1,2]")));
        JourneyListActivity.JourneyPreview p=JourneyListActivity.journeyPreview(j);
        assertNull(p.coordinates);assertTrue(p.matches.isEmpty());assertEquals("All matched sections removed",p.legend);
        j.getJSONObject("journey_corrections").put("removed_matched_segments",new JSONArray("[0]"));
        p=JourneyListActivity.journeyPreview(j);assertEquals(2,p.matches.size());
        assertEquals(2,p.matches.get(0).getJSONArray(0).getDouble(0),0);
    }
    @Test public void unmatchedJourneysAndTrainsKeepTheirExistingPreviewPolicy() throws Exception {
        JSONObject j=journey();j.remove("processing_result");
        assertEquals("Recorded route",JourneyListActivity.journeyPreview(j).legend);
        assertNotNull(JourneyListActivity.journeyPreview(j).coordinates);
        j=journey().put("mode","train");assertEquals(2,JourneyListActivity.journeyPreview(j).coordinates.length());
        assertTrue(JourneyListActivity.journeyPreview(j).matches.isEmpty());
    }
    @Test public void partialPreviewLabelsMatchedSectionsAndNeverDrawsMissingConnectors() throws Exception {
        JSONObject j=journey();j.getJSONObject("processing_result").put("timeline_partial_match",true);
        JourneyListActivity.JourneyPreview p=JourneyListActivity.journeyPreview(j);
        assertNull(p.coordinates);assertEquals(2,p.matches.size());assertTrue(p.legend.startsWith("Partially matched"));
    }
    @Test public void openPreviewCanReplaceRawTraceWithNewMatch() throws Exception {
        JSONObject j=journey();RoutePreviewView view=new RoutePreviewView(RuntimeEnvironment.getApplication(),
                j.getJSONObject("route_geometry").getJSONArray("coordinates"));
        JourneyListActivity.JourneyPreview p=JourneyListActivity.journeyPreview(j);view.setRouteData(p.coordinates,p.matches);
        Field coords=RoutePreviewView.class.getDeclaredField("coordinates");coords.setAccessible(true);assertNull(coords.get(view));
        Field segments=RoutePreviewView.class.getDeclaredField("matchedSegments");segments.setAccessible(true);
        assertEquals(2,((java.util.List<?>)segments.get(view)).size());
    }
}
