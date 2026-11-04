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
    @Test public void repeatedFullCalculationsPreserve1605ArchivesEvidenceAndRecognitionDates() throws Exception {
        Context app=RuntimeEnvironment.getApplication();JourneyStore.deleteAll(app);
        for(String name:new String[]{"roadprints_achievements_v1","roadprints_town_achievement_evidence_v1"})app.getSharedPreferences(name,0).edit().clear().commit();
        JourneyStore.prepareArchive(app);
        MessageDigest before=MessageDigest.getInstance("SHA-256");
        try {
            for(int i=0;i<1605;i++) {
                JSONObject j=AchievementGeometryMemoryTest.journey(i,500).put("started_at","2026-11-01T09:00:00Z").put("ended_at","2026-11-01T09:05:00Z").put("timezone","Europe/London")
                        .put("source",new JSONObject().put("type","timeline_import"));
                byte[] bytes=j.toString().getBytes(StandardCharsets.UTF_8);before.update(bytes);
                // Test fixture archives bypass observer/matcher work; production calculation reads them normally.
                Files.write(new File(app.getFilesDir(),"journey_j"+i+".json").toPath(),bytes);
            }
            AchievementStore.Snapshot first=AchievementStore.calculate(app);
            assertEquals(160500/1609.344,progress(first,"road-total").value,1e-9);
            assertEquals(1605,progress(first,"the-knowledge").value,0);
            String history=app.getSharedPreferences("roadprints_achievements_v1",0).getString("level_history","");
            String unlocked=app.getSharedPreferences("roadprints_achievements_v1",0).getString("unlocked","");
            for(int scan=0;scan<2;scan++) {
                AchievementStore.Snapshot next=AchievementStore.calculate(app);
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
            for(int i=0;i<1605;i++)after.update(Files.readAllBytes(new File(app.getFilesDir(),"journey_j"+i+".json").toPath()));
            assertArrayEquals(before.digest(),after.digest());assertEquals(1605,JourneyStore.archiveFiles(app).size());
        } finally {JourneyStore.deleteAll(app);}
    }
    private AchievementStore.Progress progress(AchievementStore.Snapshot snapshot,String id) {
        return snapshot.achievements.stream().filter(p->id.equals(p.definition.id)).findFirst().orElseThrow(AssertionError::new);
    }
}
