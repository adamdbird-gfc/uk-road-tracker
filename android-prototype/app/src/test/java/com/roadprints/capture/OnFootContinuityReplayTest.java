package com.roadprints.capture;

import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnFootContinuityReplayTest {
    @Test public void incidentAndReferencePaceEvidenceNeverProposesANewFootLeg()throws Exception {
        JSONObject fixture;
        try(java.io.InputStream in=getClass().getResourceAsStream("/on-foot-fragmentation/replay.json")){
            assertNotNull(in);fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        JSONArray recordings=fixture.getJSONArray("recordings");
        for(int r=0;r<recordings.length();r++) {
            JSONObject recording=recordings.getJSONObject(r);JSONArray points=recording.getJSONArray("samples");
            for(String mode:new String[]{"walking","running"}) {
                ContextJourneyModel model=new ContextJourneyModel();
                for(int i=0;i<points.length();i++) {
                    JSONArray p=points.getJSONArray(i);
                    assertNull(recording.getString("name")+" at sample "+i+" from "+mode,
                            model.sample(mode,i,1_700_000_000_000L+p.getLong(0),0,p.getDouble(1)/111195d,
                                    (float)p.getDouble(2),(float)p.getDouble(3),new ContextMap.Evidence(),false));
                }
            }
        }
    }
}
