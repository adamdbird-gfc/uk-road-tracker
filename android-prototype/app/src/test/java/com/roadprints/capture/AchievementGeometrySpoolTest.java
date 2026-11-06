package com.roadprints.capture;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import java.io.File;

public class AchievementGeometrySpoolTest {
    @Test public void losslessDeduplicationSharedAcrossBothConsumersAndCleanup()throws Exception{
        File cache=Files.createTempDirectory("spool-test-").toFile();String token;
        JSONObject j=AchievementGeometryMemoryTest.journey(1,3000);
        String original=AchievementGeometryMemoryTest.geometry(j).toString();
        try(AchievementGeometrySpool spool=new AchievementGeometrySpool(cache)){
            CoreAchievementEvidence core=new CoreAchievementEvidence(spool);core.add(j);core.add(j);
            token=core.named.get("test lane 1").iterator().next();assertTrue(token.length()<300);
            assertEquals(token,core.townRoads.get("ref:Test Lane 1").iterator().next());
            assertEquals(original,AchievementGeometrySpool.restore(token));
            assertEquals(original,CoreAchievementEvidence.geometries(core.named.get("test lane 1")).get(0).toString());
            assertEquals(1,cache.listFiles()[0].listFiles().length);
        }
        assertEquals(0,cache.listFiles().length);cache.delete();
    }
    @Test public void failureStillClosesTemporaryGeometry()throws Exception{
        File cache=Files.createTempDirectory("spool-failure-").toFile();
        try(AchievementGeometrySpool spool=new AchievementGeometrySpool(cache)){
            spool.retain(new JSONObject().put("type","LineString"));throw new java.util.concurrent.CancellationException();
        }catch(java.util.concurrent.CancellationException expected){}
        assertEquals(0,cache.listFiles().length);cache.delete();
    }
}
