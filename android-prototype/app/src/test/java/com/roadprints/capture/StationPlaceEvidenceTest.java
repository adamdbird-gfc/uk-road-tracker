package com.roadprints.capture;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class StationPlaceEvidenceTest {
    private RailStationCatalog catalog() {
        RailStationCatalog catalog=new RailStationCatalog();
        catalog.stations.add(new RailStationCatalog.Station("CHX","London Charing Cross",51.507497,-.123689));
        return catalog;
    }
    private FootTraceValidator.Sample sample(double lat) {
        return new FootTraceValidator.Sample(lat,-.123689,10,100000,.1,0);
    }
    @Test public void walkingAwayCannotReuseDepartureStationAsArrival() throws Exception {
        JSONObject arrival=StationPlaceEvidence.arrival("walk",sample(51.53),400000,
                new ContextMap.Evidence(),"CHX","arrival_gps_dwell",catalog());
        assertEquals("estimated",arrival.getString("kind"));
        assertEquals("gps_dwell",arrival.getString("source"));
        assertEquals("walk:arrival",arrival.getString("place_id"));
    }
    @Test public void actualStationArrivalUsesFullNameAndBracketedCode() throws Exception {
        JSONObject arrival=StationPlaceEvidence.arrival("walk",sample(51.507497),400000,
                new ContextMap.Evidence(),"CHX","station_arrival_waiting",catalog());
        assertEquals("station",arrival.getString("kind"));
        assertEquals("London Charing Cross (CHX)",arrival.getString("name"));
        assertTrue(StationPlaceEvidence.reviewable(arrival,catalog()));
        JSONObject departure=PlaceBoundaryEvidence.departure(arrival,sample(51.507497));
        assertTrue(StationPlaceEvidence.reviewable(departure,catalog()));
    }
    @Test public void unrelatedStationDoesNotOverrideMappedDestination() throws Exception {
        ContextMap.Evidence map=new ContextMap.Evidence();map.destination=true;
        map.name="Office";map.kind="business";map.featureId="osm:way:1";
        JSONObject arrival=StationPlaceEvidence.arrival("walk",sample(51.53),400000,map,"CHX","arrival_business",catalog());
        assertEquals("Office",arrival.getString("name"));assertEquals("business",arrival.getString("kind"));
    }
    @Test public void historicalStationSuggestionIsCheckedAtItsOwnEndpoint() throws Exception {
        JSONObject old=PlaceBoundaryEvidence.arrival("old",sample(51.53),400000,new ContextMap.Evidence(),"CHX","arrival_gps_dwell");
        assertFalse(StationPlaceEvidence.reviewable(old,catalog()));
        JSONObject origin=PlaceBoundaryEvidence.arrival("origin",sample(51.507497),400000,new ContextMap.Evidence(),"CHX","station_alighting_or_transfer");
        assertTrue(StationPlaceEvidence.reviewable(origin,catalog()));
        assertEquals("London Charing Cross (CHX)",StationPlaceEvidence.label(origin,catalog()));
    }
    @Test public void explicitPlaceCorrectionIsNeverRewrittenToCatalogueName() throws Exception {
        JSONObject old=PlaceBoundaryEvidence.arrival("old",sample(51.53),400000,new ContextMap.Evidence(),"CHX","arrival_gps_dwell");
        JSONObject reviewed=PlaceBoundaryEvidence.review(old,"Corrected destination",true,500000);
        assertTrue(StationPlaceEvidence.reviewable(reviewed,catalog()));
        assertEquals("Corrected destination",StationPlaceEvidence.label(reviewed,catalog()));
        assertNull(StationPlaceEvidence.stationAt(catalog(),"CHX",51.507497,-.123689,150));
        assertNull(StationPlaceEvidence.stationAt(null,"CHX",51.507497,-.123689,10));
    }
}
