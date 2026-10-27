package com.roadprints.capture;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettlementBoundaryValidationTest {
    private JSONObject polygon() throws Exception {
        return new JSONObject("{\"type\":\"Polygon\",\"coordinates\":[[[0,51],[1,51],[1,52],[0,51]]]}");
    }
    @Test public void polygonFeatureAndFeatureCollectionRemainSupported() throws Exception {
        JSONObject polygon = polygon(); SettlementBoundaryValidation.validate(polygon);
        JSONObject feature = new JSONObject().put("type","Feature").put("geometry",polygon);
        SettlementBoundaryValidation.validate(feature);
        SettlementBoundaryValidation.validate(new JSONObject().put("type","FeatureCollection")
                .put("features",new org.json.JSONArray().put(feature)));
    }
    @Test public void invalidNestedRingIsNotPersistable() throws Exception {
        for (String data : new String[]{"{\"type\":\"Polygon\",\"coordinates\":[[]]}",
                "{\"type\":\"Polygon\",\"coordinates\":[[[0,51],[1,51],[1,52],[0,52]]]}",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[]]}"}) {
            try { SettlementBoundaryValidation.validate(new JSONObject(data)); fail(data); }
            catch (java.io.IOException expected) { }
        }
    }
}
