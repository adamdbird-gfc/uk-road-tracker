package com.roadprints.capture;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AchievementArchiveVolumeTest {
    @Test public void repeatedFullCalculationsPreserve1623ArchivesEvidenceAndRecognitionDates() throws Exception {
        Context app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        for(String name:new String[]{"roadprints_achievements_v1","roadprints_town_achievement_evidence_v1"})app.getSharedPreferences(name,0).edit().clear().commit();
        JourneyStore.prepareArchive(app);
        MessageDigest before=MessageDigest.getInstance("SHA-256");
        try {
            for(int i=0;i<1623;i++) {
                JSONObject j=AchievementGeometryMemoryTest.journey(i,2000).put("started_at","2026-11-01T09:00:00Z").put("ended_at","2026-11-01T09:05:00Z").put("timezone","Europe/London")
                        .put("source",new JSONObject().put("type","timeline_import"));
                // Keep this retention/recognition fixture inside one county, away from
                // the unrelated repeated polygon-edge work at the London county border.
                JSONObject result=j.getJSONObject("processing_result");
                for(String field:new String[]{"road_geojson","geojson"}) {
                    JSONArray line=result.getJSONObject(field).getJSONArray("features").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                    for(int p=0;p<line.length();p++) {
                        JSONArray point=line.getJSONArray(p);point.put(0,point.getDouble(0)+.7);point.put(1,point.getDouble(1)-.25);
                    }
                }
                if(i%200==0){
                    // Large retained matches plus actual edge and road corrections, not just a two-point trace.
                    JSONArray large=new JSONArray();
                    for(int v=0;v<12000;v++)large.put(new JSONArray().put(.6+v*.0000001).put(51.25+v*.0000001));
                    result.getJSONObject("geojson").getJSONArray("features").getJSONObject(0).getJSONObject("geometry").put("coordinates",large);
                    j.put("journey_corrections",new JSONObject().put("removed_matched_segments",new JSONArray().put(0))
                        .put("removed_road_ids",new JSONArray().put("name:unrelated removed road")));
                }
                byte[] bytes=j.toString().getBytes(StandardCharsets.UTF_8);before.update(bytes);
                // Test fixture archives bypass observer/matcher work; production calculation reads them normally.
                Files.write(new File(app.getFilesDir(),"journey_j"+i+".json").toPath(),bytes);
            }
            System.out.println("ACHIEVEMENT_ARCHIVE_MAX_HEAP="+Runtime.getRuntime().maxMemory());
            assertThrows(java.util.concurrent.CancellationException.class,()->AchievementStore.calculate(app,()->true));
            AchievementStore.Snapshot first=AchievementStore.calculate(app);
            assertEquals(162300/1609.344,progress(first,"road-total").value,1e-9);
            assertEquals(1623,progress(first,"the-knowledge").value,0);
            String history=app.getSharedPreferences("roadprints_achievements_v1",0).getString("level_history","");
            String unlocked=app.getSharedPreferences("roadprints_achievements_v1",0).getString("unlocked","");
            System.gc();long baseline=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            for(int scan=0;scan<2;scan++) {
                AchievementStore.Snapshot next=AchievementStore.calculate(app);
                System.gc();long retained=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
                System.out.println("ACHIEVEMENT_ARCHIVE_HEAP journeys=1623 road_vertices=3246000 matched_vertices=108000 scan="+scan+" max="+Runtime.getRuntime().maxMemory()+" retained="+retained+" baseline="+baseline);
                assertTrue("Repeated scan retained growth",retained<baseline+32L*1024*1024);
                assertTrue(next.newlyUnlocked.isEmpty());
                for(AchievementStore.Progress old:first.achievements) {
                    AchievementStore.Progress current=progress(next,old.definition.id);
                    assertEquals(old.definition.id,old.value,current.value,1e-9);assertEquals(old.level,current.level);
                    assertEquals(old.contributions,current.contributions);assertEquals(old.milestones,current.milestones);
                }
                assertEquals(history,app.getSharedPreferences("roadprints_achievements_v1",0).getString("level_history",""));
                assertEquals(unlocked,app.getSharedPreferences("roadprints_achievements_v1",0).getString("unlocked",""));
            }
            MessageDigest after=MessageDigest.getInstance("SHA-256");
            for(int i=0;i<1623;i++)after.update(Files.readAllBytes(new File(app.getFilesDir(),"journey_j"+i+".json").toPath()));
            assertArrayEquals(before.digest(),after.digest());assertEquals(1623,JourneyStore.archiveFiles(app).size());
        } finally {JourneyStore.deleteAll(app);}
    }
    private AchievementStore.Progress progress(AchievementStore.Snapshot snapshot,String id) {
        return snapshot.achievements.stream().filter(p->id.equals(p.definition.id)).findFirst().orElseThrow(AssertionError::new);
    }
}
